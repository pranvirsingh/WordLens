# Codebase map

All app code: `app/src/main/java/com/pranvirsingh/wordlens/`.

## How a lookup flows

1. `scan/CameraPreview.kt`: CameraX shows the preview, and ML Kit reads every
   frame on a background thread. Results are mapped into plain `OcrFrame`
   objects in screen pixels (`text/OcrFrame.kt`). Dash-joined pairs like
   "hope—despite" are split by `text/CompoundSplitter.kt`.
2. `scan/ScanViewModel.kt` gets each frame on the main thread and:
   - `text/WordPicker.kt` picks the word under the lens point (`scan/ScanLayout.kt`).
   - `text/LookupQueryBuilder.kt` turns it into lookup spellings, rejoining
     words hyphenated across lines.
   - `text/DwellTracker.kt` decides when the lens has rested long enough
     (650 ms). Then it looks the word up once.
   - `text/SentenceExtractor.kt` recovers the sentence around the word.
3. `meaning/MeaningLookup.kt` runs the lookup in two steps:
   - offline first, which shows instantly;
   - then `online/OnlineDictionary.kt`: Simple English Wiktionary, then full
     Wiktionary, plus Datamuse, all in parallel and cached.

   `meaning/MeaningPresenter.kt` merges both into one `WordMeaning`. Wiki
   markup is read by `online/WikitextParser.kt`; the JSON responses by
   `online/ResponseParsers.kt`.

   The offline step itself: `dictionary/DictionaryRepository.kt` uses `dictionary/Lemmatizer.kt`
   ("running" → "run", "went" → "go") against `dictionary/DictionaryDatabase.kt`.
   That is the SQLite file from `assets/dictionary/wordnet.db`, copied to
   private storage on first use. Readings are ranked most-common-first.
4. The UI draws the result:
   - `scan/LensOverlay.kt`: the lens ring, word underlines, and the highlighter sweep.
   - `scan/MeaningSheet.kt`: the meaning card.
   - `scan/ScanScreen.kt`: the screen, reading light, hints, haptics and keep-screen-on.

## Where do I change…

| I want to change… | Go to |
|---|---|
| How long you hold still | `scan/ScanLayout.kt` → `DWELL_MILLIS` |
| Where the lens sits | `scan/ScanLayout.kt` → `LENS_Y_FRACTION` |
| Which word counts as "under the lens" | `text/WordPicker.kt` |
| What counts as a word / punctuation cleanup | `text/WordNormalizer.kt` |
| Inflection rules | `dictionary/Lemmatizer.kt` |
| Meaning ranking / synonyms shown | `dictionary/DictionaryRepository.kt` |
| Dictionary content or schema | `tools/build_dictionary.py` (then rebuild the `.db`) |
| Card layout / copy | `scan/MeaningSheet.kt`, `res/values/strings.xml` |
| Online sources, timeouts, caching | `online/OnlineDictionary.kt`, `AppContainer` in `WordLensApplication.kt` |
| How online + offline are merged | `meaning/MeaningPresenter.kt` |
| Theme / online switch / About | `scan/SettingsMenu.kt`, `settings/AppSettings.kt` |
| "New version available" check, banner, dialog | `update/UpdateChecker.kt`, `update/UpdateManager.kt`, `update/UpdateUi.kt` |
| Open a word's card without a book (debug builds) | `adb shell am start -n com.pranvirsingh.wordlens.debug/com.pranvirsingh.wordlens.MainActivity --es lookup <word>` |
| Colours / type | `ui/theme/Theme.kt` |
| First-run / camera permission screen | `permission/CameraPermissionGate.kt` |
| App version | `app/build.gradle.kts` → `appVersionName` |
