# WordLens

**Look up any word in a printed book without putting the book down.**

WordLens is an Android app for people who read physical books. Point your
phone at the page and rest the on-screen lens on a word. A highlighter sweeps
across it, and a plain-English meaning slides up in under a second. You don't
type, search, or switch apps, so you stay in the story.

[![CI](https://github.com/pranvirsingh/WordLens/actions/workflows/ci.yml/badge.svg)](https://github.com/pranvirsingh/WordLens/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/pranvirsingh/WordLens)](https://github.com/pranvirsingh/WordLens/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## Why

E-readers and phones let you tap a word to see what it means. Paper books
don't. Picking up the phone, opening a dictionary and typing the word takes
5–10 seconds, which is long enough to break the spell of a good chapter.
WordLens brings that one-tap feeling to paper.

## Features

- **Point and hold.** Rest the lens on a word. No shutter, no photos, no
  typing. You can also tap any word on screen.
- **Plain-English meanings.** Definitions and short everyday examples come
  from Simple English Wiktionary, falling back to the full Wiktionary.
- **Similar and opposite words** appear as chips. Tap one to explore it, then
  press Back to return.
- **Works offline.** A complete built-in dictionary (WordNet, 117,000
  meanings) answers instantly with no connection.
- **Understands word forms.** "ran" → *run*, "children" → *child*, words
  hyphenated across two lines are rejoined, and words with more than one base
  form ("saw") keep every reading.
- **Shows its context**: the sentence on your page that the word came from.
- **Reading comfort**: pronunciation (spoken and written), a reading light,
  pinch-to-zoom, a screen that stays awake while you read, and light, dark or
  match-phone themes.
- **Stays up to date.** The app tells you when a new version is released and
  links straight to the download.

## Install

1. Download the latest `WordLens-vX.Y.Z.apk` from
   [Releases](https://github.com/pranvirsingh/WordLens/releases/latest).
2. Open it on your phone, and allow installing apps from your browser if
   Android asks.
3. Later updates install over the top. The app lets you know when one is out.

Requires Android 8.0 (API 26) or newer, on an ARM phone (virtually all phones).

## Privacy

- The camera picture **never leaves your phone**. Text is read on the device
  by ML Kit.
- With *Simple online meanings* on, the words you aim the lens at are sent as
  plain text to Wiktionary and Datamuse to fetch their meanings.
- With *Update alerts* on, the app occasionally asks GitHub whether a newer
  version has been released. This is an ordinary web request, like opening a
  web page.
- Both can be switched off in the ⋮ menu. With both off, nothing leaves the
  phone.
- There are no accounts, no analytics and no ads.

## How it works

| Step | What happens |
|---|---|
| See | CameraX streams the preview, and ML Kit reads text on-device several times a second, returning each word's position on screen. |
| Aim | The word under the lens is picked, and a hold-still timer starts. The online meaning starts loading at this point. |
| Look up | The offline WordNet lookup finds the base form and ranks meanings by real-world frequency, so the card appears at once. |
| Upgrade | The plainer Wiktionary meaning, examples and related words replace it a moment later, or stay offline if there's no connection. |

## Building from source

Requires JDK 17 and the Android SDK (platform 35).

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

- Kotlin, Jetpack Compose (Material 3), CameraX, ML Kit Text Recognition,
  OkHttp, and SQLite.
- The offline dictionary (`app/src/main/assets/dictionary/wordnet.db`) is
  generated from the WordNet 3.0 database files with
  `tools/build_dictionary.py`.
- Release APKs are built and signed by GitHub Actions when a release is
  published (`.github/workflows/release.yml`).

The project's working notes, roadmap and conventions are in [`docs/`](docs).

## Credits

- **WordNet 3.0**: Copyright 2006 by Princeton University, used under the
  WordNet licence (the notice ships in the app at
  `app/src/main/assets/licenses/wordnet-license.txt`).
- **Simple English Wiktionary** and **Wiktionary**: definitions and examples,
  CC BY-SA, fetched at lookup time and credited on every card.
- **Datamuse**: similar and opposite words.
- **ML Kit** (Google): on-device text recognition.

## License

The WordLens source code is released under the [MIT License](LICENSE).
Third-party data keeps its own licence, as listed under Credits.
