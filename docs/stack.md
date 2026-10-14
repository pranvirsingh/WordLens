# Stack (settled)

| Piece | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) | Native Android: the fastest camera and on-device ML path, modern declarative UI |
| Camera | CameraX `LifecycleCameraController` + `PreviewView` | Handles lifecycle, rotation, pinch-zoom and tap-to-focus for us |
| Reading text | ML Kit Text Recognition (Latin), **bundled** model | Fully on-device and offline, with no first-run model download. `MlKitAnalyzer` returns word boxes already in screen pixels |
| Dictionary | WordNet 3.0 → compact SQLite file inside the APK | Offline, instant, free to redistribute. Senses are ranked by real-world frequency |
| Online meanings | OkHttp → Simple English Wiktionary (MediaWiki API), English Wiktionary (REST), Datamuse | Free, no account or key, no daily limit; plain-English wording |
| Pronunciation | Android `TextToSpeech` | Built into the phone, nothing to ship |
| Build | Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.0, JDK 17, compile/target SDK 35, min SDK 26 | Stable, well-supported set |
| CI / releases | GitHub Actions | `ci.yml` on every PR; `release.yml` builds the signed APK when a release is published |

## Deliberate decisions

- **Bundled ML Kit model, not the Play-services one.** The APK is ~10 MB
  bigger, but text reading works the moment the app is installed, even
  offline and even on phones without Google Play.
- **ARM-only native libraries** (`arm64-v8a`, `armeabi-v7a`). x86 is only for
  emulators, and dropping it saves about 23 MB.
- **No dependency-injection framework.** One small `AppContainer` wires the
  app together, with less build complexity on a modest machine.
- **Dictionary data** is regenerated with `tools/build_dictionary.py` from the
  WordNet 3.0 database files, and the output is committed. WordNet's licence
  permits redistribution with its notice (see the About/licence notes in
  `README.md`).
