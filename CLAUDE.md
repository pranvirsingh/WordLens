# WordLens — personal hobby project

Solo hobby project (owner: pranvirsingh). An Android app for people reading
**physical books**: point the phone at the page, rest the on-screen lens on a
word, and its meaning appears in under a second — no typing, no searching,
no losing the thread of the story.

## Working rules for Claude Code in this repo

- **Everything stays inside this project folder** (skills, hooks, docs), so a
  fresh session on any machine can pick it up cold from the repo alone. If a
  preference or correction comes up, write it into the relevant doc below.
- **Keep this file a short index.** Substantial content goes in `docs/`.
- **Answer the owner very briefly** (2–3 lines), plain everyday language —
  see [docs/collaboration.md](docs/collaboration.md).

## Project docs (read the relevant one before acting)

- [docs/why.md](docs/why.md) — the problem and the product idea (read first)
- [docs/collaboration.md](docs/collaboration.md) — how to work with the owner
- [docs/quality-bar.md](docs/quality-bar.md) — non-negotiable quality standard
- [docs/stack.md](docs/stack.md) — Kotlin + Compose + CameraX + ML Kit + WordNet, and why
- [docs/codebase-map.md](docs/codebase-map.md) — what lives where; "where do I change X?"
- [docs/roadmap-v1.md](docs/roadmap-v1.md) — the v1 line, version by version
- [docs/status.md](docs/status.md) — **where things stand right now** (resume here)
- [docs/shipping.md](docs/shipping.md) — git/GitHub flow, CI, releases, signing
- [CHANGELOG.md](CHANGELOG.md) — user-facing history

## Build & test

JDK 17 (machine default is Java 8 — set `JAVA_HOME=~/.jdks/jdk-17.0.20.1+1`),
Android SDK at `~/Android/Sdk`. Then:
`./gradlew lintDebug testDebugUnitTest assembleDebug`.
Use the `testing` skill (`.claude/skills/testing/SKILL.md`) for test work.

## Wrap-up and shipping

- End of any change: `phase-wrapup` skill (commit msg, PR text, CHANGELOG entry, release notes).
- "Ship `<version>`": `ship` skill — review → fix → re-review (once, no loops)
  → one-line commit (**never a Co-Authored-By line**) → PR → green CI →
  merge commit → tag → GitHub release with the signed APK. Details:
  [docs/shipping.md](docs/shipping.md).
