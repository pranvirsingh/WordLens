---
name: testing
description: Write, extend, or run WordLens's tests (JUnit unit tests on the JVM, plus Android lint). Use when a feature or fix is implementation-complete, when asked to "test this"/"run the tests", or before calling any version done. Covers unit, regression, and denial/negative tests. Never calls real network services from tests.
---

# WordLens testing

`docs/quality-bar.md` treats "actually tested" as part of done.

## Run

JDK 17 is required (the machine default is Java 8):

```
export JAVA_HOME="$HOME/.jdks/jdk-17.0.20.1+1"; export PATH="$JAVA_HOME/bin:$PATH"
./gradlew lintDebug testDebugUnitTest assembleDebug
```

Lint runs with **warnings as errors** — fix warnings, don't suppress them
without a one-line reason next to the suppression.

## Where logic lives, and why it's testable

Everything with real decisions in it is plain Kotlin with no Android types, so
it runs as a fast JVM unit test:

- `text/` — word cleanup, picking the word under the lens, the hold-still
  timer, rejoining hyphenated words, sentence recovery. Build fake pages with
  `page("line one\nline two", "next paragraph")` from `TestFrames.kt`.
- `dictionary/` — lemmatizer and lookup ranking, against `FakeLexicon`.
- `BundledDictionaryTest` runs real words through the **actual shipped
  dictionary file** (via sqlite-jdbc) with the app's own SQL — it catches data
  or schema mistakes that fakes can't.

Android-bound glue (CameraX/ML Kit mapping, Compose UI) is verified on a real
phone, not mocked into meaninglessness.

## Write all three kinds

1. **Unit** — one function, including boundaries (exactly at the dwell
   threshold, not just clearly over it).
2. **Regression** — named after the scenario it prevents
   (`does not join across paragraphs`), using generic placeholder text —
   never a real book passage or a narrated real-world bug.
3. **Denial / negative** — bad input and failing dependencies: numbers and
   symbols, empty frames, unknown words, a dictionary that throws, (from
   v1.1) an AI service that times out, returns malformed JSON, or rejects the key.

## Never

- Never call a real network API from a test. HTTP clients are tested against
  OkHttp `MockWebServer` only.
- Never weaken an assertion just to make a failing test pass — first decide
  whether the code or the expectation is wrong, and say which.
