# Media Master testing strategy

> v1.9.0 (testing-setup skill). No Hilt, no AGP upgrade, no new mocking
> library — the stack below is what `gradle/libs.versions.toml` already
> declares. Business rule: respect the current stack unless a gap forces an
> addition, and then prefer a fake over a mock.

## Stack

| Layer | Choice | Version | Notes |
|---|---|---|---|
| Unit | JUnit4 | 4.13.2 | all `src/test` cases |
| Coroutines | kotlinx-coroutines-test | 1.10.2 | `runTest` for flows |
| Robolectric | Robolectric | 4.16.1 | platform fakes (#1), Compose behavior (#2), Roborazzi (#3) |
| Compose behavior | ui-test-junit4 (BOM) | 2025.05.00 | `createComposeRule` in `src/test` |
| Screenshots | Roborazzi + plugin | 1.59.0 | local, reference images in `src/test/screenshots/` |
| Coverage | JaCoCo | Gradle bundled | `jacocoTestReport` |
| DI | none (manual fakes) | — | ViewModels take `Application` and `new` their repos; new logic that needs tests goes behind pure seams (e.g. `discoveredToDraft`) with fakes in `test/` |

Deliberately **not** installed: Hilt/KSP (churn across a UI remake; revisit only if
constructor injection becomes necessary), MockK (no case where a fake was
impossible yet), Espresso/UIAutomator/Dropshots (`androidTest/` stays a
template — device E2E is ~5% and only on request), Paparazzi/Compose Preview
Screenshot plugin (Roborazzi already declared; test-suites path needs
AGP ≥ 9.5.0-alpha03, project stays on 9.1.1).

## Commands

```bash
export ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:testDebugUnitTest      # all local tests (unit + behavior + screenshots)
./gradlew :app:jacocoTestReport       # coverage XML/HTML under app/build/reports/jacoco/
./gradlew :app:lintDebug              # must be 0 errors before release
./gradlew :app:assembleRelease        # R8 + shrink; install on emulator before any release
```

Screenshot references: `app/src/test/screenshots/`. First run records,
subsequent runs verify. Delete a stale PNG to re-record after an intentional
visual change, then review the diff before committing.

## Conventions (from the skill, adapted)

- Semantic matchers first; `testTag` only when >3 matchers would be needed.
- `createComposeRule` + `ComponentActivity` resources via Robolectric; no
  `androidTest/` for behavior that Robolectric can cover.
- Controlled components (e.g. `SwitchRow`) are tested by callback capture +
  external-state recomposition, not by internal state.
- State restoration: no `rememberSaveable` in remade components (state is
  hoisted to NavController/ViewModel), so restoration is covered by
  recomposition-with-new-state tests instead.
- Window sizes: compact/medium/expanded behavior is asserted through the
  existing `DesktopMode` pure-logic tests + the 600dp gate, not device farms.
- Navigation: `DeepLinks.resolve` pure mapping is unit-tested; the phone
  `BottomNavBar` route contract (`TOP_DESTINATIONS` ↔ NavHost routes) is
  behavior-tested. Full NavHost back-stack journeys stay manual on emulator.
