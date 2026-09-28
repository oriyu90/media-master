# AGENTS.md — Media Master

## Testing

See [docs/testing.md](docs/testing.md) for the full strategy (JUnit4 +
Robolectric + Compose behavior + Roborazzi + JaCoCo, no Hilt, manual fakes).

```bash
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug   # 0 errors required
```

## Skills vendored for this project

- `.agent/skills/material-3/` — Material Design 3 implementation + audit
  (MIT, source: https://github.com/hamen/material-3-skill).
- `.agent/skills/testing-setup/` — Android testing strategy
  (Apache-2.0, source: https://github.com/android/skills).
  License texts kept alongside each skill.

## UI remake rules (v1.9.0)

- MD3 tokens primary; Hallmark spacing/radii (`CommonUi.Hallmark`) kept.
- Do not touch: viewer pager/OCR gestures, `PlaybackManager`/player/PiP,
  `DesktopMode` detection + width gate, dynamic-color-off default.
- Every user-visible string needs `values`, `values-ja`, `values-zh`,
  `values-ar`, `values-nl` (parity test enforces it).
