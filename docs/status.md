# Status

**Current:** v1.0.0 (Point and hold), built on `feat/wl-v1`.

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

- v1.1.0 (In your sentence): see [roadmap-v1.md](roadmap-v1.md).
