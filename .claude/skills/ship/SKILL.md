---
name: ship
description: Use when the owner says "Ship <version>" (e.g. "Ship v1.1.0") or otherwise asks to take finished WordLens work all the way to a release. Runs the mandatory review → fix → re-review gate, then commit, push, PR, green CI, merge, tag, and GitHub release with the signed APK attached. This is a standing grant of full git/GitHub write authority for this one repo (pranvirsingh/WordLens) — see docs/shipping.md for why, and .claude/hooks/guard_git_scope.py for the (narrow) things it still refuses to do regardless.
---

# Ship: review → commit → push → PR → CI → merge → tag → release

Triggered by the owner saying **"Ship `<version>`"** (loose spelling is fine:
"ship 1.1.0", "ship this as v1.1.0"). Do the **entire pipeline without
stopping to ask** — that phrase *is* the confirmation for every step.
Report what actually happened at the end (real links/SHAs), not before.

Read these steps as the baseline pattern, not a literal script — apply real
judgment, never skip something that matters, never improvise a different flow.
If a step can't be done safely, stop and say why in plain language.

## 0. Preconditions

- **On a feature branch, never `main`.** This repo's line branch is
  `feat/wl-v<major>` (e.g. `feat/wl-v1`). If on `main`, stop and ask.
- **Version bumped.** `appVersionName` in `app/build.gradle.kts` must equal
  the version being shipped (without the `v`). The release workflow refuses
  to publish a tag that doesn't match it.
- **Green locally** (JDK 17 — the machine default is Java 8, so set
  `JAVA_HOME` to `~/.jdks/jdk-17.0.20.1+1` first):
  `./gradlew lintDebug testDebugUnitTest assembleDebug`. Lint runs with
  warnings-as-errors. A red result means stop and say so.
- **Checked on a real phone** for any change a reader can see (see
  `docs/quality-bar.md`) — both light and dark mode.
- **Nothing unrelated swept in.** `git status`, then stage *specific files*
  — never blindly `git add -A`/`git add .`. Flag anything unexpected.

## 1. Review, fix, re-review — mandatory, never skipped

1. Run the `code-review` skill on the full diff at **`high`** effort.
2. Fix every **CONFIRMED** finding. Genuinely ambiguous/architectural
   findings: stop and ask, don't guess.
3. Re-run the local checks from step 0.
4. **Re-review once**: run `code-review` again on the updated diff — a real
   second look confirming the fixes landed and introduced nothing new.
5. **Never loop.** One review + one re-review is the gate. If the re-review
   finds something new, fix it, re-run the checks, and move on — a third
   pass only if a fix was itself large or risky. The owner explicitly does
   not want endless review loops.

If the owner already ran `/code-review ultra` on this diff, that *is* the
re-review — don't also run a shallower inline one.

## 2. Generate the content

Use the **phase-wrapup** skill's rules (`.claude/skills/phase-wrapup/SKILL.md`)
for the commit message, PR title + description, `CHANGELOG.md` entry, and
release description. Add the `CHANGELOG.md` entry and update
`docs/status.md` **before** committing — they ship in the same commit.

## 3. Commit and push

```
git add <specific files>
git commit -m "<Imp|Fix> : <one line>"
git push -u origin <branch>
```

**One line only. Never a `Co-Authored-By:` line or any other trailer** —
the owner's explicit, standing rule.

## 4. Open the PR

```
gh pr create --base main --head <branch> --title "<title>" --body "<description>"
```

## 5. Wait for CI to actually go green

`.github/workflows/ci.yml` runs lint, unit tests and a debug build on every
PR. Opening the PR only *starts* it:

```
gh pr checks <PR-number> --watch
```

On failure: don't merge and don't blindly re-run — read the log
(`gh run view <id> --log-failed`), fix the real cause, push, wait again.

## 6. Merge — a real merge commit, never squash or rebase

```
gh pr merge <PR-number> --merge
```

## 7. Tag

```
git checkout main
git pull origin main
git tag v<version>
git push origin v<version>
```

Then return to the feature branch and bring it level with `main`
(`git checkout <branch> && git merge --ff-only main`) so the next PR starts
clean.

## 8. Publish the release (the APK attaches itself)

```
gh release create v<version> --title "v<version>" --notes "<release description>"
```

Publishing triggers `.github/workflows/release.yml`, which builds the
signed release APK and uploads it to that release as
`WordLens-v<version>.apk`. **Wait for it and confirm the APK is really
attached** (`gh run watch`, then `gh release view v<version>`). If it failed,
read the log and fix the cause — a release without its APK isn't shipped.

## 9. Report back

Short and real: what review found and fixed (or "clean"), commit SHA, PR
number + URL, release URL, APK attached (name + size). Then the usual two-line
plain-language TLDR. Never claim a step happened without having seen it succeed.

## Never, even when triggered

Enforced by `.claude/hooks/guard_git_scope.py`, not just this text:
force-push, hard reset, force-clean, history rewriting, deleting `main` or a
tag, git config/remote rewrites, deleting the repo, or touching any repo other
than `pranvirsingh/WordLens`.
