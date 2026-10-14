# WordLens

**Meanings without leaving the page.** WordLens is an Android app for people
who read physical books. Hold your phone over the page and rest the lens on a
word: a highlighter sweeps across it, and its meaning slides up in under a
second. No typing, no searching, and the book stays in your hands.

- Works fully **offline**. Text is read on the phone, and the dictionary is
  built in.
- The most common meaning comes first, with an example, similar words and
  pronunciation.
- Rejoins words hyphenated across lines, and shows the sentence the word came from.
- Reading light, pinch-to-zoom, tap any word, and the screen stays awake while
  you read.

## Install

Download the latest `WordLens-vX.Y.Z.apk` from
[Releases](https://github.com/pranvirsingh/WordLens/releases) and open it on
your phone. Android 8.0 or newer is required.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The offline dictionary is generated from the WordNet 3.0 database files:

```
py -I tools/build_dictionary.py <wordnet-dict-dir> app/src/main/assets/dictionary/wordnet.db
```

## Credits and licences

The dictionary data is **WordNet 3.0**, Copyright 2006 by Princeton
University, used under the WordNet licence. The full notice ships inside the
app at `app/src/main/assets/licenses/wordnet-license.txt`.
