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

## v1.1.0 — In your sentence

- An AI explanation of what the word means **in this exact sentence**, in very
  simple words. It uses the sentence WordLens already captures.
- Uses the owner's own free Gemini API key, entered in a new Settings screen
  and stored encrypted on the phone. The offline meaning still shows
  instantly; the AI part loads in below it and never blocks.
- Settings: AI on/off, key, hold-still speed.

## v1.2.0 — My words

- Every looked-up word is saved with its sentence: a personal word list.
- Browse, search, and delete entries, with "looked up 3 times" counts.

## Later (backlog, not committed)

- Freeze frame: hold the current view still to read a long card.
- Group words by book or reading session.
- Export to flashcards (Anki / CSV).
- Other book languages (ML Kit supports more scripts).
