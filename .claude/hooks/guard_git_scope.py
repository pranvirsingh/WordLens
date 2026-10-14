from __future__ import annotations

import json
import re
import shlex
import sys

data = json.load(sys.stdin)
command = data.get("tool_input", {}).get("command", "")

# Full git/GitHub write access is intentional (the owner's standing
# "Ship <version>" instruction — see CLAUDE.md and
# .claude/skills/ship/SKILL.md, and docs/shipping.md for the full
# rationale). This hook is NOT a write-blocker — it only stops a short
# list of things that are never part of the owner's real shipping
# pattern (a clean feature-branch -> PR -> `gh pr merge --merge` -> tag
# -> release flow, no rebasing, no forced pushes, no history rewriting,
# no tag deletion, no remote/config rewrites) and anything pointed at a
# repo other than the owner's own.
#
# This is a best-effort static check on a command string, not a real
# shell parser — two things it deliberately can't confidently resolve
# fail CLOSED (deny) rather than guess:
#   - command substitution ($(...) / `...`) anywhere near a git/gh word,
#     since what's actually inside it isn't something this file can
#     safely inspect token-by-token.
#   - a git/gh invocation this file's own tokenizer can't clearly
#     identify at all.
# Every flag check below matches `--flag value`, `--flag=value`, and (for
# push's `-f`) a squashed short-option cluster like `-uf` — git/gh accept
# all of these, and a check that only covers one form is a real bypass,
# not a theoretical one. Regression cases: test_guard_git_scope.py.

ALLOWED_REPO = "pranvirsingh/wordlens"

FORCE_PUSH_FLAG_NAMES = ("-f", "--force", "--force-with-lease", "--force-if-includes")
CONFIG_READONLY_FLAGS = {
    "--get",
    "--get-all",
    "--get-regexp",
    "--list",
    "-l",
    "--list-all",
    "--show-origin",
    "--show-scope",
}
MAIN_REF_NAMES = {"main", "refs/heads/main"}

# Global options that take their value as a SEPARATE following token
# (`-c foo=bar`, not `-c=foo=bar`) — anything else starting with "-"
# is either boolean or uses `=value` inline, so just skipping the one
# token is correct for those.
GIT_GLOBAL_VALUE_FLAGS = {"-c", "-C", "--git-dir", "--work-tree", "--namespace", "--super-prefix", "--exec-path"}
GH_GLOBAL_VALUE_FLAGS = {"--hostname"}

# Anywhere a git/gh word appears near command substitution syntax, refuse
# outright rather than trying to parse what's inside it.
SUBSTITUTION_PATTERN = re.compile(r"\$\(|`")
GIT_OR_GH_WORD = re.compile(r"\bgit\b|\bgh\b")


def base_tool_name(tok: str) -> str:
    """`git`, `/usr/bin/git`, `C:\\...\\git.exe`, `gh.exe` all resolve to
    their bare tool name — a path- or extension-qualified invocation is
    just as real an invocation as the bare word, and treating it as
    unrecognized (and therefore unchecked) is the bug, not a feature."""
    name = tok.replace("\\", "/").rsplit("/", 1)[-1]
    if name.lower().endswith(".exe"):
        name = name[:-4]
    return name.lower()


def tokens_of(segment: str) -> list[str]:
    # POSIX shlex treats a backslash as an escape, which would mangle a
    # Windows path like C:\Git\cmd\git.exe into "C:Gitcmdgit.exe" and hide
    # the git invocation. Normalise path separators first.
    segment = segment.replace("\\", "/")
    try:
        return shlex.split(segment, posix=True)
    except ValueError:
        # Unbalanced quotes — typically half of a quoted command that was cut
        # at a ';'/'&'/'|' inside it. Drop the quote marks and keep checking.
        return segment.replace('"', " ").replace("'", " ").split()


def has_flag(args: list[str], names: tuple[str, ...]) -> tuple[bool, str | None]:
    """True if any of `names` appears in `args`, as `--flag value`,
    `--flag=value`, or bare (value None either way)."""
    for i, tok in enumerate(args):
        for name in names:
            if tok == name:
                return True, (args[i + 1] if i + 1 < len(args) else None)
            if tok.startswith(name + "="):
                return True, tok[len(name) + 1 :]
    return False, None


def has_short_flag_char(args: list[str], char: str) -> bool:
    """True if `char` appears inside a squashed short-option cluster
    (e.g. `-uf`, `-fu` both carry `f`) — git's own option parser treats
    these identically to the flag given on its own."""
    for tok in args:
        if tok.startswith("-") and not tok.startswith("--") and char in tok[1:]:
            return True
    return False


def strip_global_options(rest: list[str], value_flags: set[str]) -> list[str]:
    """Skip leading global options (`git -c x=y push ...`,
    `gh --hostname x repo ...`) so the real subcommand is what ends up
    treated as the verb. The predecessor hook explicitly skipped these
    before matching a verb; this file originally didn't, which was a
    real regression (any global flag re-enabled every check below it —
    found by this repo's own review-gate process, not assumed)."""
    i = 0
    while i < len(rest) and rest[i].startswith("-"):
        i += 2 if rest[i] in value_flags else 1
    return rest[i:]


VERSION_TAG = re.compile(r"^v\d+(\.\d+)*$")


def is_protected_ref(name: str) -> bool:
    """`main`, or a release tag in any spelling (`refs/tags/v1.0.0`, `v1.0.0`)."""
    return name in MAIN_REF_NAMES or name.startswith("refs/tags/") or bool(VERSION_TAG.match(name))


def is_dangerous_delete_refspec(tok: str) -> bool:
    # `git push origin :main` / `:refs/tags/v1.0.0` / `:v1.0.0` — an empty
    # source ref before the colon means "delete this ref on the remote",
    # the same outcome `--delete` achieves via a different, equally
    # standard syntax. A shipped release tag is exactly as protected here
    # as it is by the `tag -d` guard below.
    return tok.startswith(":") and is_protected_ref(tok[1:])


def is_force_refspec(tok: str) -> bool:
    # `git push origin +main` / `+HEAD:main` force-updates that ref just
    # like --force does, with no flag at all.
    return tok.startswith("+") and len(tok) > 1


INLINE_CONFIG_DANGER = re.compile(r"^\s*(alias\.|url\.|core\.sshcommand|core\.hookspath)", re.IGNORECASE)


def check_inline_config(rest: list[str]) -> str | None:
    """`git -c alias.p='push --force' p ...` or `git -c url.X.insteadOf=Y push`
    smuggle a forbidden operation or a push redirect in through a one-off
    config value, under a harmless-looking verb."""
    i = 0
    while i < len(rest) and rest[i].startswith("-"):
        tok = rest[i]
        value = None
        if tok == "-c" and i + 1 < len(rest):
            value = rest[i + 1]
        elif tok.startswith("-c") and len(tok) > 2:
            value = tok[2:]
        elif tok.startswith("--config-env"):
            value = tok.partition("=")[2] or (rest[i + 1] if i + 1 < len(rest) else "")
        if value is not None and INLINE_CONFIG_DANGER.match(value):
            return "an inline git config override (alias/url/ssh/hooks) — can hide a forbidden operation or redirect a push"
        i += 2 if tok in GIT_GLOBAL_VALUE_FLAGS or tok == "--config-env" else 1
    return None


def check_git(rest: list[str]) -> str | None:
    inline = check_inline_config(rest)
    if inline:
        return inline
    rest = strip_global_options(rest, GIT_GLOBAL_VALUE_FLAGS)
    if not rest:
        return None
    verb = rest[0]
    args = rest[1:]

    if verb == "push":
        if (
            has_flag(args, FORCE_PUSH_FLAG_NAMES)[0]
            or has_short_flag_char(args, "f")
            or any(is_force_refspec(a) for a in args)
        ):
            return "a force push — never part of this repo's normal shipping pattern"
        has_delete = has_flag(args, ("--delete", "-d"))[0] or has_short_flag_char(args, "d")
        if has_delete and any(is_protected_ref(a) for a in args if not a.startswith("-")):
            return "deleting the main branch or a release tag"
        if any(is_dangerous_delete_refspec(a) for a in args):
            return "deleting the main branch or a tag (via a `:ref`-style refspec)"

    elif verb == "reset":
        if "--hard" in args:
            return "a hard reset — discards uncommitted work"

    elif verb == "clean":
        if has_short_flag_char(args, "f") or has_flag(args, ("--force",))[0]:
            return "a forced clean — permanently deletes untracked files"

    elif verb in ("filter-branch", "filter-repo", "replace", "update-ref", "symbolic-ref"):
        return "directly rewriting refs/history — never part of this repo's shipping pattern"

    elif verb == "branch":
        # `-D` is shorthand for `--delete --force` — both spellings must
        # be caught, not just the short one.
        force_delete = has_flag(args, ("-D",))[0] or (
            has_flag(args, ("--delete", "-d"))[0] and (has_flag(args, ("--force", "-f"))[0] or has_short_flag_char(args, "f"))
        )
        if force_delete and "main" in args:
            return "force-deleting the main branch"

    elif verb == "tag":
        if has_flag(args, ("-d", "-D", "--delete"))[0]:
            return "deleting a git tag — tags are shipped release markers, never deleted in this repo's pattern"

    elif verb == "remote":
        write_verbs = ("add", "remove", "rm", "rename", "set-url", "set-head", "set-branches", "prune")
        if args[:1] and args[0] in write_verbs:
            return "changing the git remote — never needed for shipping this repo, and can silently redirect where pushes go"

    elif verb == "config":
        # A read is `git config [--get|--list|...] <key>` — the flag, if
        # any, is the first argument. Checking membership anywhere in
        # `args` (rather than specifically `args[0]`) would wrongly treat
        # a write whose VALUE happens to spell a flag name (e.g. defining
        # an alias whose body is the string "--get-all") as a read.
        first_is_readonly_flag = bool(args) and args[0] in CONFIG_READONLY_FLAGS
        if not first_is_readonly_flag:
            bare = [a for a in args if not a.startswith("-")]
            if len(bare) > 1 or any(a.startswith("-") for a in args):
                return "a git config write — never part of this repo's pattern, and can silently redirect where pushes/clones go (e.g. a url.insteadOf rewrite, or a malicious alias)"

    return None


def check_gh(rest: list[str]) -> str | None:
    has_repo_flag, repo_value = has_flag(rest, ("--repo", "-R"))
    if has_repo_flag and repo_value:
        target = repo_value.strip().strip("/").lower()
        if target and target != ALLOWED_REPO:
            return f"references a repo other than pranvirsingh/WordLens ('{repo_value}')"

    if strip_global_options(rest, GH_GLOBAL_VALUE_FLAGS)[:2] == ["repo", "delete"]:
        return "deleting the repository"

    return None


GITHUB_URL_PATTERN = re.compile(r"github\.com[:/]([\w.-]+/[\w.-]+?)(?:\.git)?(?:[/\s]|$)", re.IGNORECASE)


def check_github_url(line: str) -> str | None:
    for found in GITHUB_URL_PATTERN.findall(line):
        normalized = found.strip().strip("/").lower()
        if normalized and normalized != ALLOWED_REPO:
            return f"references a repo other than pranvirsingh/WordLens ('{found}')"
    return None


# Anything that ends one command and starts another: ; & && | || and
# subshell parentheses. Splitting on single characters covers all of them.
SEGMENT_SPLIT = re.compile(r"[;&|()]")
MAX_NESTING = 3


def analyze(text: str, depth: int = 0) -> str | None:
    if depth > MAX_NESTING:
        return "deeply nested shell commands around git/gh — too complex to verify, refused rather than guessed"
    for raw_line in text.splitlines():
        if SUBSTITUTION_PATTERN.search(raw_line) and GIT_OR_GH_WORD.search(raw_line):
            return (
                "command substitution ($(...) or `...`) appears alongside a git/gh "
                "invocation — too complex to safely verify here, refused rather than guessed"
            )

        for segment in SEGMENT_SPLIT.split(raw_line):
            toks = tokens_of(segment)
            if not toks:
                continue

            # A quoted command handed to another shell (`bash -c "git push -f"`,
            # `pwsh -Command "..."`) is checked as a command of its own.
            for tok in toks:
                if any(c.isspace() for c in tok) and GIT_OR_GH_WORD.search(tok):
                    nested = analyze(tok, depth + 1)
                    if nested:
                        return nested

            tool_index = next((i for i, t in enumerate(toks) if base_tool_name(t) in ("git", "gh")), None)
            if tool_index is not None:
                tool, rest = base_tool_name(toks[tool_index]), toks[tool_index + 1 :]
                found = check_git(rest) if tool == "git" else check_gh(rest)
                if found:
                    return found

            url_reason = check_github_url(segment)
            if url_reason:
                return url_reason
    return None


try:
    reason = analyze(command)
except Exception as error:  # never let a parsing bug turn into "allowed"
    reason = f"the git safety guard could not check this command ({type(error).__name__})"

if reason:
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": "Blocked: " + reason + ".",
                }
            }
        )
    )
    sys.exit(0)

print("{}")
