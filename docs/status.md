# Status

**Current:** v1.1.0 (Simple meanings), built on `feat/wl-v1`. v1.0.0 shipped
2026-10-14 (PR #1, release `v1.0.0` with its APK).

## v1.1.0

- Online meanings come from Simple English Wiktionary, then full Wiktionary,
  plus Datamuse synonyms and opposites. No key is needed.
- The offline meaning shows first, then gets upgraded. Fetching starts while
  the reader is still aiming.
- Offline antonyms were added to the dictionary file (`antonyms` table).
- New card: pronunciation, example bubbles, tappable Similar/Opposite chips
  with Back.
- ⋮ menu with theme (Match phone / Light / Dark), an online on/off switch,
  and About.
- Parsers were checked against real live responses (contagious, ominous,
  reluctant, run, wistful, …).
- 136 unit tests passing; lint clean. Checked on a real phone (Motorola Edge 50
  Fusion, Android 16): card in light + dark, chips + Back, theme menu, and a
  word with two base forms ("saw" → see + saw).

## v1.0.0

- Code complete: camera lens, hold-still lookup, tap lookup, offline WordNet
  dictionary with frequency ranking, lemmatizer, hyphen rejoining, sentence
  capture, pronunciation, reading light, haptics, keep-screen-on, and the
  welcome/permission screen.
- 66 unit tests passing, including real words against the shipped dictionary file.
- `lintDebug` clean, with warnings as errors.
- Signed release APK builds locally (~31 MB, R8-shrunk, ARM-only).
- CI (`ci.yml`) and the release-APK workflow (`release.yml`) are in place.

## Next

- v1.2.0 (My words): see [roadmap-v1.md](roadmap-v1.md).
