# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A two-player TCG (trading card game) life counter app for Android, written in Kotlin with Jetpack Compose. This is a greenfield project being built as a **hands-on Kotlin tutorial** — see "Teaching Mode" below before doing anything else.

### Feature requirements

- Two players' life totals shown simultaneously (one screen half per player, top half rotated 180° so the opposing player reads it upright).
- Life adjustment buttons (+ / −): single tap changes by 1; press-and-hold changes by 5, then keeps repeating in increments of 5 while held.
- History grouping: life changes to one player made in quick succession accumulate into a single history entry, committed 1s after the last change (debounce). While uncommitted, the pending delta (e.g. "−3") is shown in a smaller font above that player's life total; the life total itself always updates in real time.
- Round timer.
- History log of all (grouped) life changes (who, how much, when, resulting total).

## Teaching Mode (important)

The user is an experienced developer (JS/TS background — see sibling projects) but has **no Kotlin or Android experience**. This project's primary purpose is learning, not shipping fast:

- Work in small, reviewable steps. One concept or feature per step, not a big-bang scaffold.
- Before each edit, explain **why**: the Kotlin/Android concept involved, and the reasoning behind the design choice. Relate concepts to JS/TS equivalents where it helps (e.g. `val`/`var` vs `const`/`let`, coroutines vs async/await, Compose vs React).
- Prefer showing the user what to write and letting them ask questions over silently generating large files. When generating boilerplate (Gradle config, manifest), still walk through what each part does.
- Key concepts to introduce as they naturally come up: Gradle build system, Activity lifecycle, Jetpack Compose (`@Composable`, state hoisting, recomposition), `ViewModel` + `StateFlow`, data classes, sealed classes, coroutines, and Kotlin idioms (null safety, `when`, scope functions).

## Planned Architecture

Single-activity Jetpack Compose app, MVVM:

- `MainActivity` — lone activity, hosts the Compose UI.
- `GameViewModel` — owns all game state as a `StateFlow<GameState>`; UI is a pure function of that state. All mutations (life changes, timer ticks, reset) go through ViewModel functions so history logging happens in one place.
- `GameState` / `LifeChange` — immutable data classes; history is a list of `LifeChange` entries appended on every mutation.
- Press-and-hold repeat behavior implemented with a coroutine launched on press and cancelled on release (`pointerInput` / `detectTapGestures` in Compose).
- Round timer driven by a coroutine in the ViewModel, not the UI layer.
- No persistence layer initially; if added later, prefer DataStore over SharedPreferences.

## Environment & Commands

Android SDK is at `~/Android/Sdk` (platforms 35/36.1/37, build-tools up to 37.0.0, emulator, platform-tools). `ANDROID_HOME` is **not** set — either export it or keep `sdk.dir` in `local.properties` (never commit that file).

**JDK:** system default is JDK 26, but the build is pinned to JDK 21 via `gradle/gradle-daemon-jvm.properties` (Gradle daemon JVM criteria) — plain `./gradlew` works, no `JAVA_HOME` override needed. JDK 21 lives at `/usr/lib/jvm/java-21-openjdk`.

No system Gradle is installed — always use the wrapper (`./gradlew`).

**AGP 9 gotcha:** this project uses AGP 9.2.1 with *built-in Kotlin* — do **not** apply `org.jetbrains.kotlin.android`; it errors under AGP 9. Kotlin compiler options go in a top-level `kotlin { compilerOptions { } }` block if needed. The Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is still a separate plugin, versioned to match the Kotlin compiler AGP bundles (2.2.x for AGP 9.2).

```bash
./gradlew assembleDebug            # build debug APK
./gradlew installDebug             # build + install on connected device/emulator
./gradlew test                     # unit tests (JVM)
./gradlew testDebugUnitTest --tests '*GameViewModelTest*'   # single test class (--tests only works on the variant task, not the umbrella `test` task)
./gradlew connectedAndroidTest     # instrumented tests (needs device/emulator)
./gradlew lint                     # Android lint
~/Android/Sdk/platform-tools/adb devices        # list connected devices
~/Android/Sdk/emulator/emulator -list-avds      # list emulators
```

## Project Status

Scaffolded from Android Studio's **"No Activity"** template (Gradle 9.4.1, AGP 9.2.1, compileSdk 37, minSdk 25, namespace `com.example.life_counter`), then converted to Compose (BOM 2026.06.01, material3, activity-compose; Compose compiler plugin 2.2.10 matching AGP's built-in Kotlin). `MainActivity` + hello-world `@Composable` in place; `./gradlew assembleDebug` builds clean. No emulator AVD or device was available to run it as of step 2.

Tutorial progress:
- [x] Step 1 — project scaffold, CLI build verified
- [x] Step 2 — swap View-system deps for Compose (BOM, activity-compose, compose compiler plugin), add `MainActivity` + first `@Composable`
- [x] Step 3 — `GameState` data class + `GameViewModel` with `StateFlow` (+ `GameViewModelTest`, 4 passing JVM tests)
- [x] Step 4 — two-player counter UI, tap ±1 (OLED-black theme, opponent half rotated 180°, +/− tap zones per half, middle bar with timer placeholder + history/reset buttons, keep-screen-on flag)
- [x] Step 5 — press-and-hold ±5 repeat via coroutine (`detectTapGestures`/`onPress` in `AdjustZone`) + debounced history grouping (`PlayerState.pendingDelta`, per-player commit `Job` in ViewModel, virtual-time tests)
- [x] Step 6 — round timer (counts up; tap the clock to pause/resume, dims while paused; `timerJob` coroutine in ViewModel). Gotcha learned: virtual-time tests must pause the timer before finishing, or `runTest` never drains the task queue and the test hangs.
- [ ] Step 7 — history log
