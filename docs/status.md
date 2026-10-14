# Status

**Current:** v1.2.0 (Update alerts), built on `feat/wl-v1`.

Shipped so far:
- v1.0.0, 2026-10-14 (PR #1)
- v1.1.0, 2026-10-15 (PR #2)

The repo is public under the MIT licence.

## v1.2.0

- `update/UpdateChecker.kt` reads GitHub's latest release: version, notes,
  and the APK link.
- `update/UpdateManager.kt` checks at most every 3 hours, caches the result,
  and remembers a dismissed banner per version.
- The UI (`update/UpdateUi.kt`) is a banner under the top bar, an "Update to
  x.y.z" ⋮ menu item, and a What's-new dialog. Download hands the APK link to
  the browser.
- Professional README, LICENSE (MIT), and the GitHub About section.
- Verified on a real phone against the live GitHub release feed: banner, ⋮ menu
  item, and the What's-new dialog with its Download button. 155 tests pass.

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

- v1.3.0 (My words): see [roadmap-v1.md](roadmap-v1.md).
