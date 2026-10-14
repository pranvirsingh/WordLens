# Quality bar (non-negotiable)

The owner's words: "the bestest of the bestest", "completely bug free". Every
version holds to this, not just a final polish pass.

- **Feels instant.** The meaning should appear within about a second of
  resting the lens on a word, on a mid-range phone. Nothing slow runs on the
  main thread: text recognition happens on its own thread, and dictionary
  lookups run on the IO dispatcher.
- **Never breaks the reading flow.** The card never covers the lens, the
  screen doesn't sleep mid-page, there's a haptic tick when the meaning lands,
  the back button closes the card, and the reading light remembers its real
  on/off state.
- **Distinctive, not generic.** It has its own identity (paper-and-ink colours,
  a serif headword, the highlighter-sweep lens). Every significant UI piece has
  deliberate layout, type and motion, not stock defaults.
- **Both themes.** Every screen is checked in light *and* dark mode. Camera
  overlays stay readable on bright white pages and on dim, yellowed ones.
- **Real failure states.** No permission, permission denied for good, no text
  in view, word not in the dictionary, dictionary failed to open, no TTS
  voice: each gets a clear, friendly message, never a crash or a blank.
- **Tested.** Unit, regression and negative tests for all logic (see the
  `testing` skill). Lint runs with warnings as errors. The real dictionary
  file is tested with real words.
- **Verified on a real phone** before any version that changes what a reader
  sees is called done. "It compiles" is not proof it works.
- **Small and private.** Release builds are shrunk (R8) and ARM-only. The
  camera image never leaves the phone.
