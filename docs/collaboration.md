# Working with the owner

Written so a brand-new session, with no memory of earlier conversations, can
pick this project up without anything being re-explained. If a working
preference gets corrected, update this file then and there.

## Who and why

A solo hobby project and a learning vehicle (Android, CameraX, on-device ML,
CI/CD, releases). The owner is fairly new to professional git practice, so
explain the *why* in plain words when it matters.

## Communication

- **Very short answers by default: 2–3 lines.** Plain, everyday words.
- **Improvise.** The owner explicitly wants Claude to bring better ideas and
  solutions, not just follow the literal request. Propose the improvement,
  briefly say why, and build it if it's clearly within scope.
- Ask only questions whose answers change what gets built. Otherwise pick the
  sensible default and mention it in one line.
- Every piece of work ends with a 2–3 sentence plain TLDR.
- When corrected, don't be defensive. Fix it and say so plainly.

## Finish fully, in one go

- Carry each version all the way through: code, tests, real-phone check,
  docs (`docs/status.md` especially), CHANGELOG, and release. Don't leave a
  known gap unmentioned.
- No scope creep. Narrow feedback about one thing gets applied to that thing.

## The owner's machine

Low-end Windows PC (8 GB RAM). **No Android emulator** (too heavy); verify on
a real phone over USB (`adb`). The JDK 17 and Android SDK are portable,
user-level installs (`~/.jdks/`, `~/Android/Sdk`). No admin rights needed.

## Process rules (details elsewhere)

- Commit messages are one line, `Imp :`/`Fix :`, **never a Co-Authored-By
  line**: `.claude/skills/phase-wrapup/SKILL.md`.
- Review once, fix, re-review once. **Never get stuck in a review loop**:
  `.claude/skills/ship/SKILL.md`.
- Never credit external inspiration sources; never narrate specific
  real-world bug examples in code, tests or docs.
