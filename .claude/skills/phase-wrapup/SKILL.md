---
name: phase-wrapup
description: Use every time a WordLens version, feature, or bug fix is finished — produces the one-line commit message, PR title + short plain-language PR description, a CHANGELOG.md entry, a release description (for a version), a short dev-notes explainer, and a closing TLDR. Also triggers on "finishing touch" / "wrap this up". Text only — the `ship` skill is what actually executes git/GitHub steps.
---

# Phase wrap-up

**Text only — never runs git/gh itself.** The `ship` skill reuses these rules
and does the execution. Always produce the full set below, in this order.

## 1. Commit message

- **One line.** Prefix exactly `Imp : ` (improvement/addition) or `Fix : `
  (bug fix) — space on both sides of the colon, never another word.
- **Never add a `Co-Authored-By:` line or any other trailer/body.** Owner's
  explicit standing rule.
- Example: `Imp : Point-and-hold lens with offline meanings`

## 2. PR title and description (two separate blocks)

- **Title:** a few plain words naming what the PR does ("Meanings in your
  sentence's own words"), no jargon.
- **Description:** a one-line summary, then 3–5 short bullets (group under a
  sub-heading only for a big change). Everyday words — a non-developer should
  skim it in under a minute. End with a short `## Test plan` checklist of
  what was really verified (unit tests count, lint, real phone check), then
  the line `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Describe the final product, never the struggle.** Bugs caught and fixed
  before shipping are never mentioned. A fix to something a user already had
  is reported as "Fixed: …".

## 3. CHANGELOG.md entry

Add at the **top** of `CHANGELOG.md` (newest first), today's real date:

```
## v1.1.0 — 2026-10-20

### New
- Something the reader can now do, in plain words

### Fixed
- Something that was broken and now works, in plain words
```

Omit an empty section. Only list what a reader will actually notice. Every
change gets an entry, including small fixes.

## 4. Release description (for a version)

```
## v1.1.0 — Short name

One or two sentences on what this version is about.

- One line per major thing (compress hard)

**Install:** download `WordLens-v1.1.0.apk` below and open it on your phone.
```

## 5. How it works (dev notes)

For each notable piece: **what it does** (one sentence), **how** (the real
mechanism and file — `DwellTracker.kt`, `MlKitAnalyzer`), and **why this way**
when there's a real reason. Short blocks with a bold lead-in each — clear
beats exhaustive.

## 6. TLDR

Two or three plain sentences: what was done. Readable in five seconds.

## Never

- Never credit or name external inspiration sources (sites, blog posts,
  snippets) in commits, PRs, code comments, or docs.
- Never narrate a specific real-world bug example in code, tests, or docs —
  describe the general rule instead, with placeholder test data.
