# GGym — Architecture

How the pieces fit together, and why they are built the way they are. Everything here is
implementation-facing: for what the app does, see the [README](README.md).

## 1. Constraints that shape the design

- **100% offline.** No network permission is declared anywhere in the manifest, no cloud account,
  no telemetry. Auto Backup and device-to-device transfer are excluded in
  `res/xml/data_extraction_rules.xml`, so data leaves the device only through the user's own
  export file.
- **Single user, single device.** No accounts, no multi-user concerns, no conflict resolution.
- **Sideloaded or distributed as an APK** through GitHub Releases — no Play Store constraints.
- **Localized** in English and Italian, with an in-app language switch.
- **Material 3 Expressive** is a product requirement, not a nice-to-have.

## 2. Build stack

| Component | Version | Note |
|---|---|---|
| AGP | 9.3.1 | AGP 9 ships **built-in Kotlin**, so the `kotlin-android` plugin is not applied |
| Gradle | 9.6.0 | required by AGP 9.3 |
| Kotlin | 2.4.21 | raised above the KGP 2.2.10 that AGP bundles, through the top-level `buildscript` classpath documented by AGP |
| Compose compiler plugin | 2.4.21 | `org.jetbrains.kotlin.plugin.compose`; its version must equal the Kotlin version |
| Compose BOM | 2026.09.00 | manages `compose.ui`, `foundation`, `animation` |
| material3 | **1.5.0-beta01** (overrides the BOM) | see below |
| Room | 2.8.5 | KSP, no kapt |
| KSP | 2.3.12 | no longer versioned with Kotlin (see below) |
| kotlinx-serialization | 1.11.0 | the backup file format; the compiler plugin matches the Kotlin version |
| JDK | 21 | what `scripts/bootstrap-toolchain.sh` installs |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 | |

### Why material3 is a beta

The Material 3 Expressive API is *not usable* from the stable 1.4.0 release: `MotionScheme`,
`MotionScheme.expressive()`, `MaterialTheme(motionScheme = …)`, `MaterialTheme.motionScheme`,
`expressiveLightColorScheme()` and `MaterialExpressiveTheme` are all `internal` in that version
(their bytecode is present but mangled with the `$material3` module suffix, so the Kotlin compiler
refuses them). They become public API on the 1.5.0 line, whose release notes describe promoting
`materialExpressTheme` and `expressiveLightColorScheme`.

Since expressive theming is a requirement for this app, material3 is pinned to `1.5.0-beta01`
explicitly, overriding the Compose BOM constraint, and the expressive opt-in is declared once:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}
```

This is a deliberate, recorded trade: a beta dependency in exchange for the design system the app
is built around. It should be re-reviewed when material3 1.5.0 goes stable, at which point the
pin can be dropped and the BOM can own the version again.

Similarly, the Kotlin Gradle Plugin is pinned to 2.4.21 because the Compose compiler plugin must
match it exactly, while AGP 9's bundled KGP is 2.2.10.

KSP is pinned on its own version rather than a Kotlin-matching one. From KSP 2.3.0 it is a
standalone tool built on stable compiler APIs instead of a compiler plugin, so its version no
longer tracks Kotlin's — there is no `2.4.21-2.0.x` release — and it is wired through the same
top-level `buildscript` classpath as the Kotlin plugin, which is the documented mechanism for AGP
9's built-in Kotlin.

## 3. Module and package layout

One module (`:app`), packaged by feature. The layering is one-directional:

```
com.ggym.app
├── GgymApplication      creates the dependency container
├── MainActivity         the single activity; hosts the Compose tree
├── di/                  AppContainer — the composition root
├── domain/              models, and the few use-cases that carry real logic
├── data/
│   ├── room/            entities, DAOs, database, mappers
│   ├── prefs/           DataStore-backed preferences
│   └── backup/          JSON export/import
├── service/             the rest timer's foreground service and notifications
└── ui/
    ├── theme/           Color, Type, Shape, Motion, Theme — the brand system
    ├── nav/             destinations and the navigation host
    └── <feature>/       one package per screen: Screen + ViewModel + UiState
```

Rules that keep this honest:

- `ui/` never imports Room or DataStore types; it sees `domain/` models.
- `domain/` never imports Android UI or notification classes.
- `data/` implements interfaces declared next to their consumer, so the UI depends on behaviour
  rather than on storage details.
- Features do not reach into each other; anything shared moves to `domain/` or `data/`.

## 4. State, threading and dependency injection

- **Unidirectional data flow.** Each screen has one immutable `<X>UiState` exposed as
  `StateFlow`, plus a `SharedFlow<Event>` for one-shot effects. No `LiveData`, no `Channel` used
  for state.
- **ViewModels** receive dependencies through `AppContainer` and an explicit
  `viewModelFactory { initializer { … } }`. No service locator inside composables.
- **Room** DAOs return `Flow` for reads and `suspend` for writes, so the database is the single
  source of truth and the UI re-renders from it.
- Flows feeding the UI use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- Nothing in `data/` holds hard references to a `Context` beyond the application context, so the
  layers below the UI remain testable on the JVM.

## 5. Data conventions

| Concern | Convention |
|---|---|
| Primary keys | `Long`, auto-generated |
| Timestamps | epoch milliseconds (`Long`) |
| Date-only values | normalized to local midnight at the data boundary, so calendar bucketing is exact |
| Weights | `Double`, canonical unit **kg** |
| Target reps | `String` (`"8-10"`, `"10"`, later `"AMRAP"`), parsed where it is needed |
| Exercise identity | unique name, **case-insensitive**, so `"bench press"` cannot become a second exercise with its own history |
| Formatting | always through the active locale (`NumberFormat`, `DateTimeFormatter`) |

## 6. Persistence

Eight tables cover the domain:

```
exercise                 the global dictionary: name, currentWorkingWeight, defaultRestSeconds
routine                  a named collection of training days
routine_day              a day inside a routine, ordered
planned_exercise         an exercise slot in a day: target sets/reps, rest, order
workout_session          one completed workout: date, routine/day name snapshots, duration
workout_exercise_log     one exercise inside a session: name snapshot, completed flag, order
workout_set_log          one performed set: set number, weight, reps, completed flag
body_weight_log          a body-weight measurement with its date
```

**Foreign-key policy.** Deleting things must never silently destroy work, and the two kinds of
loss need two different answers:

- `planned_exercise.exerciseId` → `RESTRICT`. A routine slot without its exercise is meaningless,
  so the database refuses the delete and the repository surfaces a typed error that the UI turns
  into "this exercise is used in Push Day and Pull Day".
- `workout_exercise_log.exerciseId` → nullable, `SET_NULL`. Training history must survive
  everything: the snapshot name keeps the log readable even after the dictionary entry is gone, so
  no logged set is ever destroyed by a delete somewhere else.
- `routine → routine_day → planned_exercise` and `session → log → set` cascade, so removing a
  parent leaves no orphans.

**Migrations.** `exportSchema` is on and the schema JSON is committed under `app/schemas/`, so
migrations are written explicitly and verified against the schema that shipped. Destructive
fallbacks are not used: they would silently delete a user's training history.

**Backup format** is human-readable JSON carrying `schemaVersion`, `exportedAt` and `appVersion`,
with one array per table. Every row carries its primary key, so a restore reproduces the database
exactly — including the identity of rows — and children are nested inside their parent instead of
referencing it, so a file cannot contradict itself. A field the current version does not know, or
a version it does not support, is refused rather than ignored.

Import is *validate, then replace inside a single transaction*: the file is parsed, its version,
values and referential integrity are checked, and only then are the tables replaced. A failure
rolls back, leaving the existing data untouched — restoring a bad file can never leave a
half-empty database. Restoring an empty file is how a user starts over, and it is the same code
path as any other restore.

The file describes the *database*: routines, dictionary, history and body weight. Preferences
(theme, language, the timer toggle) are not part of it yet, so a restore does not carry them to a
new device.

## 7. Brand system

The palette is derived from the app icon: a near-black canvas carrying a neon chartreuse
(`#D5FF00`, hero tone `#EFFF46`, shade `#B0E600`).

Two rules are enforced by tests rather than by discipline:

1. Chartreuse is never body text on a light surface — it is about 1.3:1 on white. That is why the
   light scheme's primary is a deep olive (`#4A5D00`, ~7:1) and chartreuse survives there as an
   accent and as the hero colour on intentionally dark cards.
2. Every text pair used in either scheme clears WCAG AA. `BrandPaletteTest` computes the contrast
   ratios from the real `ColorScheme` values, so a future palette tweak that makes text
   unreadable fails the build.

The expressive checklist the UI is held to:

- `MaterialExpressiveTheme` with `MotionScheme.expressive()`; spatial motion (springs that
  overshoot) for things that move — pager swipes, set-completion morphs, nav changes — and
  effects motion (fast, damped) for colour and value changes.
- heavy display weights for hero data (working weight, rest countdown), with tabular figures so
  digits do not reflow while typing;
- deliberately mixed shapes: soft containers, squarer controls, pill actions, and shape used as
  *state* (a set button morphing from pill to cookie when the set is done);
- tonal containment with `surfaceContainerHigh` / `surfaceContainerHighest` to group related sets
  and cards;
- light / dark / system theming, plus optional wallpaper colours (Material You) as an opt-in
  preference — off by default, because it replaces the brand palette by definition.

## 8. Testing

- JVM unit tests are the default target: domain rules, mappers, backup round-trips and palette
  assertions all run without a device.
- Anything that needs the Android framework takes its dependency as a constructor parameter
  (`DataStore<Preferences>`, DAO interfaces) rather than a `Context`, so tests can inject a
  temp-file DataStore or an in-memory database. `ThemePreferencesTest` demonstrates the pattern.
- Room DAO tests use Robolectric plus `Room.inMemoryDatabaseBuilder`, which keeps them on the JVM
  and out of an emulator. Robolectric needs `--add-opens` flags on JDK 17+ (`java.lang`, `java.io`,
  `jdk.internal.access`, …); they are declared once, next to `isIncludeAndroidResources` in
  `app/build.gradle.kts`, and without them every Robolectric test dies in application start-up.
- Tests that do not need a SQLite runtime do not pay for one: date conversions, mappers, the
  backup validator and the file format itself are plain JVM tests.
- `GgymDatabaseTest` re-opens the committed schema snapshot under `app/schemas/` and compares its
  identity hash with the one Room writes into the installed database, so the snapshot cannot drift
  from the schema a build actually creates.
- Coroutine tests use `kotlinx-coroutines-test`; Flow assertions use Turbine.
- No test depends on wall-clock time or on the host machine's locale.
- Lint must stay at zero findings. The two intentional suppressions live in `app/lint.xml`, each
  with the reason written next to it.

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:lintDebug           # lint
./gradlew :app:assembleDebug       # debug APK
```
