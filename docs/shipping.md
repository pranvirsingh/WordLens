# Shipping — git flow, CI and releases

Claude Code has **full git/GitHub write authority for this one repo**
(`pranvirsingh/WordLens`): commit, push, PR, merge, tag, release. The owner
granted it on 2026-10-14 ("properly push on git, from code to the end
releases"). The trigger is **"Ship `<version>`"**, or the owner asking to take a
version to release. Always use the `ship` skill.

## The flow (mirrors the owner's ClaimLens repo)

1. **Feature branch**: `feat/wl-v1` for the v1 line (never commit to `main`).
2. **Code**: implement, test, and check on a real phone.
3. **Review → fix → re-review, once.** Never a loop.
4. **Commit**: one line, `Imp : …` / `Fix : …`. **No Co-Authored-By or any
   trailer, ever.**
5. **PR** into `main`, with a short plain-language description.
6. **CI green**: `.github/workflows/ci.yml` (lint, unit tests, debug build).
   Wait for the real result.
7. **Merge** with a true merge commit: `gh pr merge --merge` (never squash or rebase).
8. **Tag** `v<version>` on `main`.
9. **Release**: `gh release create`. `.github/workflows/release.yml` then
   builds the **signed** APK and attaches it as `WordLens-v<version>.apk`.

Versioning is semantic: `vMAJOR.MINOR.PATCH`. A new feature bumps MINOR, a
fix bumps PATCH. The version lives in one place, `appVersionName` in
`app/build.gradle.kts`. `versionCode` is derived from it
(`major*10000 + minor*100 + patch`). The release workflow refuses a tag that
doesn't match.

## Release signing

- The upload key is `~/.wordlens/wordlens-release.jks` (PKCS12, alias
  `wordlens`), and its passwords are in `~/.wordlens/signing.env`. Both live
  **outside the repo**, and `.gitignore` blocks `*.jks`/`*.keystore` anyway.
- The same values are stored as GitHub Actions secrets:
  `WORDLENS_KEYSTORE_BASE64`, `WORDLENS_KEYSTORE_PASSWORD`,
  `WORDLENS_KEY_ALIAS`, `WORDLENS_KEY_PASSWORD`.
- **Back up the `.jks` file and `signing.env` somewhere safe.** If the key is
  lost, existing installs can't be updated by a new APK, only reinstalled.
- To build a signed release locally:
  `set -a; . ~/.wordlens/signing.env; set +a; ./gradlew assembleRelease`.

## Guardrails (always on)

`.claude/hooks/guard_git_scope.py` blocks, regardless of any grant:
- force-push, hard reset, force-clean
- history or ref rewriting
- deleting `main` or a tag
- git config or remote rewrites
- deleting the repo
- anything aimed at another repo

Its regression tests are in `.claude/hooks/test_guard_git_scope.py`.
