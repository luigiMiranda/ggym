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

The Workouts section is the reference for how a feature is put together:

```
ui/workouts/  WorkoutsScreen -> RoutineDetailScreen -> DayDetailScreen
              one Screen + ViewModel + UiState per level, plus a ModalBottomSheet editor
ui/exercises/ ExercisePickerScreen (a destination) and the slot editor sheet
ui/common/    EmptyState, dialogs, the row overflow menu, weight formatting and parsing
```

Two conventions are worth stating because they are easy to get wrong: **routes carry ids, never
state** (`routine/{routineId}`, `day/{routineId}/{dayId}`), so a back stack restored after process
death re-reads the current data instead of showing a stale copy; and **the bottom bar belongs to the
tabs**, so the routine tree is a drill-down with its own back path rather than a fifth tab. The bar
is on screen **everywhere**: `Destination.owningTab(route)` lights the tab a drill-down lives under
(routine / day / picker / workout → Workouts, history → Calendar), so Home and Settings stay one tap
away from any depth, and a running workout survives the trip because it lives in the database and in
the rest timer's service rather than on the screen.

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
| Planned weight | `planned_exercise.weightKg`, `Double?`: null means "inherit the exercise's resolved default", so a slot carries its own weight only once the user chose one |
| Target reps | `String` (`"8-10"`, `"10"`, later `"AMRAP"`), parsed where it is needed |
| Exercise identity | unique name, **case-insensitive**, so `"bench press"` cannot become a second exercise with its own history |
| Formatting | always through the active locale (`NumberFormat`, `DateTimeFormatter`) |

## 6. Persistence

Eight tables cover the domain:

```
exercise                 the global dictionary: name, currentWorkingWeight, defaultRestSeconds
routine                  a named collection of training days
routine_day              a day inside a routine, ordered
planned_exercise         an exercise slot in a day: target sets/reps, rest, order, its own weight
workout_session          one workout — finished, or in progress while `isDraft` is set — with date,
                         routine/day name snapshots, duration and (for a draft) its start time
workout_exercise_log     one exercise inside a session: name snapshot, completed flag, order, and the
                         rest time and target reps snapshotted for its sets
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

The dictionary itself is addressed by id and its `name` is unique **case-insensitively**
(`COLLATE NOCASE`). Renaming an entry is a single `UPDATE` judged by that index: a name another
entry already holds is **refused** rather than merged — merging would mean repointing every planned
slot and every history row that referenced one of them — and the refusal comes back as a typed
result the dialog explains without losing what the user typed. A rename never rewrites a snapshot:
a past workout, and a workout already in progress, keep the name they started with.

**Migrations.** `exportSchema` is on and the schema JSON is committed under `app/schemas/`, so
migrations are written explicitly and verified against the schema that shipped. Destructive
fallbacks are not used: they would silently delete a user's training history. Every migration so
far is an `ALTER TABLE` addition that leaves each existing row as it was, and each is exercised by
a test that builds the older database from its committed snapshot, seeds it, and opens it with the
current build: `v1 → v2` adds `planned_exercise.weightKg`; `v2 → v3` adds
`workout_session.isDraft` / `workout_session.startedAtMillis` and
`workout_exercise_log.restSeconds` — the columns that let a workout exist while it is still being
performed; `v3 → v4` adds `workout_exercise_log.targetReps`, the written target snapshotted so
the workout can show what the plan asks next to the actual being entered (the sets' own integer reps
only hold the target's first number, so `8-10` would come back as `8`); and `v4 → v5` adds
`workout_exercise_log.plannedExerciseId`, the soft pointer from a workout's exercise log back to the
routine slot it came from, so a completion can move the trained day's weight as well as the
dictionary's. Older rows read back with the meaning they had: finished workouts with no start time,
the default rest time, a blank target and no slot pointer — exactly what they were.

**Workouts in progress.** A workout under way is a *draft* session: written the moment it starts,
updated set by set, and invisible to every history read (`observeSessionsInRange`,
`observeSession`, `latestCompletedSet`), so the calendar and "what did I lift last" keep meaning
*finished* workouts. Finishing writes the finished session as a fresh row and deletes the draft in
one transaction; discarding just deletes the draft. A backup file carries the draft too, so
restoring on a new phone can resume the workout instead of losing it. `workout_session.isDraft` is
the only place that state lives: it is a property of the stored session, not of a screen.

**Running a workout.** `domain/workout/ActiveWorkoutUseCases` owns what happens between start and
finish. Starting *materializes* the training day into the draft: the routine and day names, each
exercise's name and rest time, and one pre-filled set per planned set, with the weight
`WorkingWeightUseCases` resolves for that slot and the first number its written target carries
(`"8-10"` starts at eight). Each exercise log also records the *slot it came from*
(`plannedExerciseId`, a soft pointer with no foreign key — it is a breadcrumb, not a relationship),
which is what lets a completion find that day's weight again. From then on the routine is never
consulted again, so editing or deleting it cannot move a workout in progress. Completing a set
writes the set, hands its weight to `WorkingWeightUseCases.recordCompletedSetWeight` — which makes
it the exercise's working weight *and* the planned weight of the slot the workout was built from
when it is greater than zero, because a bodyweight set must not erase a real number — and replaces
the group default in the exercise's uncompleted sets, the ones still carrying the weight the
completed set had before, so one edit moves the sets the user is about to do and a set they typed
into keeps its own number. Writing the slot supersedes M3's "the plan is the plan": only the day
that was actually trained moves, so a deliberate per-day difference survives (Push A trained to
85 kg, Push B still planned at 70 kg), and a log with no pointer (history written before v5, a
draft restored from an older backup) simply updates the dictionary as it always did. Only
uncompleted sets can be removed, and a completed one can be **taken back**: un-completing a
set clears the flag and nothing else — the set keeps its numbers, and neither the exercise's working
weight nor the slot's is reverted (nothing remembers what they were); the filled sets are not
un-filled either. The dictionary is a "load next time" hint, not a log; the workout is what happened.
Finishing writes the completed sets as a fresh session
(duration = finish − start, sets renumbered from position), drops the prefills nobody performed,
marks an exercise with no completed set as skipped and deletes the draft in one transaction;
discarding only deletes the draft. One workout runs at a time: a second start is refused with the
running workout's id, so the screen can offer to resume or discard it instead of quietly starting a
second one. Every write re-reads the draft first, so an action that arrives after the workout ended
is a no-op rather than a write to the wrong workout.

**The active workout screen.** `ui/workout/ActiveWorkoutScreen` is one destination with two modes:
the exercise list (each card carries the name, "2 / 4 sets", a progress bar and the weight to load
next) and the focused view, one page per exercise in a `HorizontalPager` with the weight as the
largest interactive element on screen. The modes are internal state rather than two routes, because
swiping between exercises is not navigation and everything that matters — the selected set, the
running rest, what has been performed — is shared between them. The hero edits the selected set:
`−2.5` / `+2.5`, typed text, and the reps stepper change what is on screen and nothing else, and a
set becomes the user's data when it is *completed*. That is deliberate: the completion is the write
that carries the working-weight rule, and it can only recognise the sets that still hold the plan's
weight if nobody has rewritten them from the keyboard. The chips below show what the hero shows,
with a "remove" that names its own set for unperformed ones and `+ Add set` at the end of the strip.
System back is a path rather than an escape — focused → overview → the finish sheet — and the sheet
is the summary of what will be saved: sets completed, exercises skipped, duration, with
"Finish & save" and "Discard workout" behind the shared confirmation dialog. The rest banner is drawn
in both modes and recomputed from the stored deadline on every tick; the screen owns no countdown.

**History and the calendar.** The Calendar tab is one destination: a month grid whose dots mark
days with a finished workout, and the selected day's workouts underneath it, so a day is chosen
while the month that explains it stays on screen. The grid is built by
`domain/history/CalendarGrid`, a pure function from a `YearMonth` to six Monday-first weeks — six
always, so paging through months does not resize the calendar — with the adjacent months' days
included and dimmed. The month is read through the repository's existing inclusive range query, and
the read is tagged with the month it belongs to so a page turn never draws the previous month's
sessions. A draft is never on the calendar: `observeSessionsInRange` excludes it, because "a workout
happened" means a finished one. Selection has three rules in priority order — today when the shown
month contains it, otherwise the month's first trained day, otherwise the first — so a month with no
workouts still has a focus for the grid and a date for the line under it. A tapped day in an
adjacent month's row turns the page to that month first, so the selection never names a day whose
sessions were not read. "Edit log" opens `history/{sessionId}`: one screen that reads a finished
session and can correct a set's weight and reps or remove a set. It deliberately has no path to add
a set, add an exercise, or change the date or duration — history is corrected, never extended — and
`wasCompleted` is not recomputed by a correction. A correction is `updateSet`, a removal is
`deleteSet`, and both re-read the session through its flow. Deleting a workout is one cascade behind
the same confirmation dialog the rest of the app uses.

**Stats and the body-weight log.** The Stats tab is three charts and a log. The charts are drawn on
a Compose `Canvas` rather than by a charting library: a bar series and two line series do not justify
another styling language, another dependency to keep aligned with AGP, or a library that might bring
network capability into an app with no `INTERNET` permission. The arithmetic that decides what is
drawn is pure code in `domain/stats` and tested on the JVM: `CalendarMonths` (the last twelve
months), `AttendanceCounts` (distinct trained days per month, zero-filled — two workouts on one day
is one day of attendance), `BodyWeightStats` (a window's points and their min/average/max),
`ExerciseProgression` (one point per date, the day's maximum completed weight, and a least-squares
trend only from three points) and `downsample` (first/last/min/max and an even stride, capped at 240
points for the *path only*). The statistics are always computed over every point in the window, not
over the reduced line, so a drawing decision never moves a number the user reads. Every chart is
decorative to accessibility and sits beside a text summary with the same numbers. Two read-only
queries feed the tab — the dates of finished sessions in a range, and one exercise's completed sets
with their session dates — so counting a year of attendance does not load a year of set trees, and
progression excludes drafts and pre-filled sets by construction. Body weight is entered through the
Stats tab's sheet, the only place a body weight enters the app by hand. `domain/stats/BodyWeightUseCases`
is its only writer and owns the rule the schema does not: **one measurement per day**, where logging
a day that already has a row updates it, and moving an edit onto an occupied day merges rather than
duplicating. The range filters (1M, 3M, 6M, 1Y, All) are anchored to today, not to the latest
weigh-in, so "the last three months" does not silently stretch when the user stops weighing. The sheet
defaults to today, opens the platform date picker, and disables Save for a value outside the same
`BodyWeightLimits` the repository enforces. The route takes an optional `exerciseId` query
argument so another screen can open the chart already on one exercise; a tab declared with an
argument reports its route *pattern* rather than the plain route, which is why the bottom bar
matches tabs on the base route (everything before the `?`) instead of the whole string.

**The rest timer.** A rest is a *deadline* kept in its own DataStore file: `RestTimerState`
(`endsAtMillis`, `totalSeconds`, `exerciseLogId`, `exerciseName`) as wall-clock epoch millis,
written as one tuple and cleared as one. A remaining count would have to be decremented by a ticker,
and a ticker stops when the process does; a deadline is still right after the app is killed, the
screen is locked or the phone reboots, and every countdown — in the app and in the notification — is
computed from it. There is exactly one writer, the foreground service that shows the notification,
so the banner, the shade and a restarted process cannot disagree about what is running.
`domain/workout/RestTimerUseCases` holds the rules, in two halves. The app's half asks
`RestTimerCommands` to do something and never writes the store itself: `startForSet` applies the
master switch (`WorkoutPreferences.restTimerEnabled`; off means a completed set is logged and no
countdown is started) and the set's own `restSeconds`, where zero means the same; `extend`, `skip` and
`stop` send nothing at all when no countdown is running, so a stale tap cannot start one. The
service's half is what a command means: `stateForSet` builds the deadline, `moveDeadline` applies
`±15 s` and returns `null` once the moved deadline is at or past now — that is a rest *finishing*,
which the service turns into the completion alert, whereas `skip` ends a rest silently — and
`remainingMillis` / `hasElapsed` are the arithmetic both the banner and the service use.
`totalSeconds` is the length the set asked for; the deadline is the truth.

**Running in the background.** The countdown's other half is `service/RestTimerService`, a foreground
service with the `specialUse` type (a rest is none of the named cases) and a low-importance silent
notification: the countdown is drawn by the system (`usesChronometer` + `chronometerCountDown`, based
on the stored deadline), and its three actions — `+15 s`, `-15 s`, `Skip` — send the same intents the
app's own banner sends. Tapping a timer notification opens the app. There are two channels: the
running one silent and ongoing, and a high-importance one with vibration and no sound for the alert,
whose tone the app plays itself (`AndroidRestAlertPlayer`, a `ToneGenerator` beep while holding
`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, so the user's music dips instead of stopping). Tapping the
notification opens the running workout, not just the app: `RestTimerNotifications` puts
`EXTRA_OPEN_WORKOUT` on the activity intent, the activity counts such requests in state (so a
configuration change does not replay one) and the nav host navigates to `workout/{sessionId}` for the
draft that is still open. A notification whose workout has since ended opens the app where it was.

**Entering a workout, and the rest timer's switch.** A workout starts from the training day's
"Start workout" container, which carries the day's shape (exercises · sets) and is disabled when
nothing is planned. A second start does not write: `ActiveWorkoutUseCases.start` returns the running
workout's id, and the day screen turns that into a dialog that names the running day and its routine
and offers *resume* or *discard and start* — the discard path also stops the rest that belonged to
the workout it throws away. Workouts lists the running workout above the routines (day name, "3 of 12
sets", elapsed time, refreshed by a ticker because it is the one thing on that screen that moves
without a write), read from the draft's own name snapshots so a workout survives its routine being
edited or deleted. Settings owns the rest timer's master switch and writes it through
`RestTimerUseCases.setEnabled`, never straight into the preference, so "off" also ends a rest that is
running. The notification permission is requested once, the first time a workout is on screen
(`WorkoutPreferences.notificationPromptShown`), never at launch, and denial is an ordinary outcome:
Settings grows a row that says what is missing and links to the system page. That row is driven by
the system state, read by the screen on every resume (`rememberNotificationsAllowed`), so no
ViewModel needs an Android dependency to know it.

A rest ends through whichever path arrives first: the service's own task for the deadline, or the
exact alarm (`RestTimerAlarms`, `setExactAndAllowWhileIdle`) that wakes a suspended, dozing or dead
process and delivers a broadcast to `RestTimerAlertReceiver`. Both go through `RestTimerCompletion`,
so the alert, the notification and the stored state cannot disagree, and a completion is precise
about which rest it ends — a set completed exactly as the previous rest runs out must not lose its
new countdown. The receiver is what makes the backstop work in the worst case: a manifest receiver is
always allowed to play a tone, write the store and post a notification, while starting a foreground
service from the background is not. If the alarm turns out to be early (a wall clock that moved), the
rest is not over and the alarm is simply re-armed. Denied `POST_NOTIFICATIONS` is an ordinary
outcome: the countdown, the in-app banner and the tone all work, only the drawer notification is
missing.

**Backup format** is human-readable JSON carrying `schemaVersion`, `exportedAt` and `appVersion`,
with one array per table. Every row carries its primary key, so a restore reproduces the database
exactly — including the identity of rows — and children are nested inside their parent instead of
referencing it, so a file cannot contradict itself. A field the current version does not know, or
a version it does not support, is refused rather than ignored. The format version is its own
number, not the database's: the reader accepts every format the app has ever written (1 through 5
today), so a backup file outlives the build that produced it. A file may hold at most one workout
in progress; two of them is a contradiction, and the validator says so before anything is written.

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

Three rules are enforced by tests rather than by discipline:

1. Chartreuse is never body text on a light surface — it is about 1.3:1 on white. That is why the
   light scheme's primary is a deep olive (`#4A5D00`, ~7:1) and chartreuse survives there as an
   accent and as the hero colour on intentionally dark cards.
2. Every text pair used in either scheme clears WCAG AA. `BrandPaletteTest` computes the contrast
   ratios from the real `ColorScheme` values, so a future palette tweak that makes text
   unreadable fails the build.
3. The launcher mark fits inside the **66dp circle in the middle of the 108dp adaptive-icon
   canvas** — the only area every launcher mask is guaranteed to show. `IconGeometryTest` renders
   the real vector drawables and measures the farthest opaque pixel; the content scale is 64dp of
   the canvas (`scaleX/scaleY = 0.071111`). It was 70.2dp until M6.5, which put the outer barbell
   plates 35.10dp from the centre against a 33dp radius, so a circular launcher cut them short.
4. The barbell plates are **light steel** (`#949AA6 → #6E737D → #545962`), not a ghost. On the
   near-black icon background the first dark-metal gradient faded to roughly 1:1 at its darkest,
   which read as a shadow rather than a plate; the same test now also asserts the darkest plate
   pixel clears 2:1 against the background, so a later "tidy-up" cannot dim it back.

The UI follows an approved screen preview that is the layout contract for every screen. Its
tokens live once, in `ui/common/BrandUi.kt` and `ui/theme/`: 27sp screen titles, uppercase 11.5sp
section labels, 12sp pills, 20dp cards and 28dp hero plates. The shell itself uses Material 3's
`NavigationBar` with the brand primary indicator (a deliberate preference over the preview's
custom pill bar). The hero plate
(`GgymHeroColors`, `LocalGgymDarkTheme`) is near-black in **both** colour schemes — chartreuse on
the off-white page would be ~1.3:1 — which is what lets the working weight stay the loudest thing
on screen in light mode too.

Every input is the same `GgymField`: one tonal container, the label small and uppercase **above**
the value, an optional unit suffix and an optional pair of ± step buttons on the same row. A number
can therefore always be typed as well as stepped, and the exercise form, the body-weight sheet and
the history editor cannot drift into three different field styles again.

The expressive checklist the UI is held to:

- `MaterialExpressiveTheme` with `MotionScheme.expressive()`, and every animation names which
  scheme it is: **spatial** (a little overshoot, so the eye follows it) for things that have a
  place — pager swipes, a set row's corner and tint as it is performed, the rest banner entering
  and leaving, the page dots — and **effects** (fast, damped) for colours and value changes.
  Destinations cross-fade on the way **forward** on purpose: the tabs have no left-to-right order,
  so a direction would be invented. **Back** is different — it is a direction the user made — so the
  `NavHost`'s four pop transitions (including the predictive pair an Android 16+ back gesture runs)
  are Material's shared axis X: the page leaves towards the trailing edge and the one behind it
  arrives from the leading edge, nothing changes size.
- heavy display weights for hero data (working weight, rest countdown), with tabular figures so
  digits do not reflow while typing;
- deliberately mixed shapes: soft containers, squarer controls, pill actions, and shape used as
  *state* — a set row is a squarer, tinted control while it is still a plan and rounds into a softer
  shape once it has been performed, which is an animated radius because a `Shape` cannot be
  interpolated;
- tonal containment with `surfaceContainerHigh` / `surfaceContainerHighest` to group related sets
  and cards;
- reordering as a direct manipulation: a routine's days and a day's exercises are dragged by a
  handle (`ui/common/ReorderableColumn`), the row body stays a button, and the whole new order is
  written once on drop. The handle carries "Move up" / "Move down" accessibility actions, because a
  gesture is never the only way to do something;
- light / dark / system theming, plus optional wallpaper colours (Material You) as an opt-in
  preference — off by default, because it replaces the brand palette by definition.

## 8. Localization

The app ships **English** (`values/`, the default) and **Italian** (`values-it/`), declared to the
platform through `android:localeConfig`, and the user can choose between them — or leave it to the
device — from **Settings → Language**. Every user-visible string lives in
`strings.xml`; `MissingTranslation` makes an incomplete translation a build failure, so the two files
cannot drift.

One rule is worth stating because it is the opposite of the obvious choice. The choice is stored in
the app's own preferences (`AppLanguage` in the same DataStore as the theme), **not** in Android 13's
per-app language. `LocaleManager` looks like the right home for it, but its public API
(`getApplicationLocales` / `setApplicationLocales`) has no way to *observe* a change: an app that
trusted it as the store could not notice a switch made in the system's own App-languages screen. A
single store that the app controls, plus `Context.withAppLanguage`, is what makes the picker and the
screens agree.

That helper is applied at the two places where copy reaches the user: the activity's
`attachBaseContext` (every screen, and `Locale.setDefault` alongside it so the view models that
format weights and dates agree with the UI) and the rest-timer notification, which is built with the
application context and so is never covered by the activity's wrapper. `System` means *leave the
context alone*, so a device in Italian gets an Italian app with no setting touched.

## 9. Testing

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
- **UI tests run on the JVM under Robolectric too** (`androidx.compose.ui:ui-test-junit4`, with
  `ui-test-manifest` on the debug variant so the test rule has an activity to host). They cover what
  a ViewModel test structurally cannot: composition wiring — which slot a sheet is bound to, which
  node a tap reaches — and they exist because exactly such a bug shipped once. Two environment
  facts come with them: they pin `@Config(sdk = [36])`, because Espresso's idling path reflects on
  the hidden `android.hardware.input.InputManager.getInstance()` that AOSP removed in Android 17 (the
  Room tests keep the app's API 37); and a test that waits for a `viewModelScope` write must poll
  with `waitForIdle()`, since that dispatcher only progresses while the main looper is idled.
- No test depends on wall-clock time or on the host machine's locale.
- Lint must stay at zero findings. The two intentional suppressions live in `app/lint.xml`, each
  with the reason written next to it.

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:lintDebug           # lint
./gradlew :app:assembleDebug       # debug APK
```
