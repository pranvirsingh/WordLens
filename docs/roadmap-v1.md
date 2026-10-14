# Roadmap — the v1 line

Each version ships through the full flow in [shipping.md](shipping.md).

## v1.0.0 — Point and hold

The core loop, fully offline.

- Live camera lens. A faint underline marks every word the app can read.
- Rest the lens on a word: a highlighter sweeps across it, and in about 0.65 s
  the meaning card slides up. Or tap any word directly.
- Offline WordNet dictionary (117k meanings), with the most common reading
  first, examples, similar words, and "ran → run" style base forms.
- Rejoins words hyphenated across lines, and shows the sentence it came from.
- Pronunciation, a reading light, pinch-zoom, tap-to-focus, keep-screen-on,
  and a haptic tick.
- Welcome / camera-permission screen, including the "denied for good" path.
- CI on every PR, plus a signed APK attached to every GitHub release.

## v1.1.0 — Simple meanings (done)

The owner's feedback on v1.0.0: the offline dictionary's wording is too
formal. They wanted meanings that read like Google's (plain words, short
examples, similar and opposite words), with no AI and no key or usage limits.

- **Offline first, always.** The WordNet meaning still appears instantly, and
  WordNet antonyms are now included.
- **Online upgrade, no key.** Simple English Wiktionary supplies everyday
  definitions and examples, with the full English Wiktionary as a fallback.
  Datamuse adds common synonyms and opposites. All three are asked in
  parallel, with tight timeouts and in-memory caching.
- **Feels instant.** Fetching starts as soon as the lens settles on a word,
  usually before the hold-still timer finishes.
- New card layout: pronunciation, example bubbles, and **Similar** and
  **Opposite** chips. Tapping a chip opens that word, and Back returns.
- ⋮ menu: theme (Match phone / Light / Dark), an online meanings on/off
  switch, and About (credits and privacy).

AI explanations (Gemini) were considered and **dropped by the owner's
choice**: daily limits, and a key to manage.

## v1.2.0 — Update alerts (done)

- The app checks the repo's latest GitHub release (at most every 3 hours) and
  shows an "update available" banner, a ⋮ menu item, and a What's-new dialog.
  The Download button opens the release APK.
- The repo went public under the MIT licence, which is what makes the release
  feed readable without an account.

## v1.3.0 — My words

- Every looked-up word is saved with its sentence: a personal word list.
- Browse, search, and delete entries, with "looked up 3 times" counts.

## Later (backlog, not committed)

- Freeze frame: hold the current view still to read a long card.
- Group words by book or reading session.
- Export to flashcards (Anki / CSV).
- Other book languages (ML Kit supports more scripts).
