# Compose Multiplatform UI Port — Program Roadmap

**Type:** Program-level roadmap / decomposition document (not a single-feature spec, not an implementation plan)
**Date:** 2026-07-08
**Status:** Approved for decomposition — each phase gets its own spec → plan → implementation cycle

---

## 1. Purpose and framing

AndBible's UI is to be ported from XML/View-based Android UI to **Compose Multiplatform (CMP)**. This is the deliberate first step toward a shared Android + iOS codebase, but **this program is a UI port only**. iOS platform implementations, JSword portability, and the Room data-layer migration are explicitly out of scope here and handled in later, separate programs.

This document is the **decomposition anchor**: it defines the strategy, the cross-cutting KMP-readiness principles, the module architecture, the ordered work batches, and the coordination mechanics that tie together the many per-phase specs/plans that will follow in separate sessions.

### Why a roadmap instead of one plan
The current View layer is ~37k LOC across 35 Activities and ~46 screen classes, 90 XML layouts, 69 custom Views, 19 RecyclerView adapters, 57 dialogs, 44 menu resources, 9 `androidx.preference` screens, and 0 ViewModels (state is carried through 85 files coupled to `ABEventBus`). This is far too large for one spec. It is decomposed into a foundations phase, a series of screen batches, the main reading-view complex, and a cleanup/navigation-unification phase — each its own spec+plan.

---

## 2. Scope

### In scope
- Port all View/XML screens to Compose Multiplatform composables.
- Establish the KMP module structure, theme system, DI, state architecture, string mechanism, and platform seams needed to host Compose UI.
- Enable the iOS **compile** target on the shared modules as an enforcement gate (see §4).
- Keep the Android app continuously runnable and shippable throughout (Strangler Fig, §3).

### Out of scope (deferred to later programs)
- **JSword portability to iOS.** Stays a JVM/Android library in `:app`, reached from shared UI only through interface seams.
- **Room data-layer KMP migration.** Room stays Android-only in `:app`. Shared UI must not import Room/DAO types directly; it goes through repository interfaces so the later Room-KMP migration does not ripple into the UI.
- **Building the iOS app (`:iosApp`).** No `MainViewController`, no iOS platform implementations beyond what is needed to keep the iOS *compile* green. iOS actuals are written when the real iOS port happens.
- **The Vue.js WebView Bible renderer.** Already cross-platform; kept as an Android `WebView` via `AndroidView` interop, behind an `expect`/`actual` (or interface) seam so a future `WKWebView` slots in. Ported as part of the main reading-view complex (Batch 12), not rewritten.
- **Navigation unification / single-Activity.** Deferred to the cleanup phase (Batch Z) by design — it is a small fraction of total effort and is best done once every screen is Compose.

---

## 3. Strategy: Strangler Fig with a runtime toggle

Port **screen by screen (in batches)**, keeping the old XML implementation alongside the new Compose one as an **alternative runtime implementation** until a final cleanup phase.

- **New screens are written from scratch as separate classes/composables in `:sharedUi`.** The old Activity/XML is left untouched as a live reference and runtime fallback, and is removed as one block during cleanup. No `if (flag) setContent() else setContentView()` branching inside the old classes.
- **A central routing indirection** (`ScreenLauncher.open(context, Screen.X, args)`) chooses old vs new per screen based on a flag. **This indirection is the seed of the future unified navigation** — in Batch Z it evolves into the CMP navigation graph, so it is not throwaway work.
- **The toggle is a developer/debug setting** (global default + optional per-screen override) so old and new can be flipped and A/B-compared on-device — the strongest available parity check for theming, gestures, and e-ink behavior that unit tests cannot cover. In release builds a screen's default flips to the new implementation only once it is marked verified.
- **Only the view layer is duplicated.** Both implementations consume the same domain/control layer (JSword, Room, controls, the `ABEventBus`↔Compose bridge). Duplication is transient and removed screen by screen.

Rationale: big-bang and incremental are ~90% the same work; the only difference (navigation unification) is done last regardless. Keeping the old implementation as a runtime fallback preserves the test/feedback loop at near-zero extra cost while still allowing a fast, chunky "sprint" cadence. "Runnable" ≠ "every commit shippable" — large pushes are fine.

---

## 4. Cross-cutting KMP-readiness principles

These touch every ported screen and are decided once, in Phase 0, then applied from screen 1. They are what make "multiplatform" real rather than nominal.

### 4.1 Module architecture
Three-module split (adopted from the AndroidMidiRecorder reference), plus a build-time tool:

| Module | Contents | Compose? | Targets |
|--------|----------|----------|---------|
| `:sharedCore` | Portable logic: state holders/controllers, repository interfaces, seam interfaces, pure models | **No** | android, jvm (fast Linux tests), iosArm64, iosSimulatorArm64 |
| `:sharedUi` | All Compose UI: screens, components, theme, `LocalStrings` interface, (later) nav graph | Yes | android, iosArm64, iosSimulatorArm64 |
| `:app` | Android entry point: Application, Activities (old + thin new hosts), Dagger→Koin bridge, Room, JSword bridge, `AndroidStrings`, platform seam impls, `WebView` | Yes | Android application |
| `:strings-gen` | Plain Kotlin/JVM build-time tool that generates the iOS `Strings` holder from `res/values-*` | n/a | JVM tool |

Dependency graph: `:app` → `:sharedUi` → `:sharedCore`; `:sharedUi` uses `:strings-gen` only as a build-time `JavaExec` classpath. Existing `:jsword` module stays under `:app`.

Keeping logic (`:sharedCore`) free of Compose maximizes what is unit-testable on Linux and keeps future iOS-native code Compose-free.

### 4.2 Strings — `LocalStrings` interface + build-time generator (NOT compose-resources)
`strings.xml` in `res/values-*` remains the **single source of truth**. This preserves the existing translation pipeline (`update-translations` skill, Transifex, all locales) 100% untouched — the decisive reason to reject `compose.components.resources`, which would force migrating every string and break that pipeline.

Three layers (from the reference):
1. **`Strings` interface** in `:sharedUi` commonMain; composables read `LocalStrings.current.foo` via a `staticCompositionLocalOf<Strings>`.
2. **`AndroidStrings`** in `:app`: mechanical `R.string` wrapper; provided via a `ProvideAppLocals` composition-local provider.
3. **Generated iOS holder**: `:strings-gen` parses `res/values-*/strings.xml` + the `Strings` interface + the Android impl, and emits a pure-Kotlin `GeneratedStrings` into an `iosMain` source dir. Because it implements the real `Strings` interface, the **iOS compile enforces per-locale coverage**.

⚠️ **Hardening required over the reference:** AndBible uses `<plurals>` (quantity strings) and has larger, messier string usage than the reference (which explicitly skips `<plurals>`). The generator needs real plural/quantity support and more robust parsing. This is a defined sub-task of Phase 0.

Icons: prefer `compose.materialIconsExtended` in commonMain where an existing drawable has a Material equivalent; otherwise keep vector drawables via the resource seam (watch binary size). Fonts (Greek/Hebrew/RTL framework): a `staticCompositionLocalOf<FontFamily>` seam with an Android provider now; iOS provider later.

### 4.3 Dependency injection — Koin (KMP-native)
The reference used hand-rolled manual DI; its own notes warn this does not scale (its iOS container is ~900 lines of hand-wiring for 5 screens). For 35+ screens, use **Koin**. Dagger stays in `:app` for legacy wiring during transition; new shared code resolves dependencies via Koin, and a bridge exposes existing singletons. DI setup lands in Phase 0.

### 4.4 State architecture
Per-screen state holders (plain Kotlin classes with an injected `CoroutineScope`, in `:sharedCore`) — **scoped per screen**, not one mega-state. On Android, thin `AndroidViewModel` wrappers bridge Android-typed surfaces (`Uri`, `Intent`, `Context`). Use the **KMP-capable `androidx.lifecycle` ViewModel** artifact where a lifecycle-scoped VM is wanted. UI state is exposed as `StateFlow`, collected via `collectAsState()`; events flow up via action lambdas (unidirectional, MVI-flavored, no MVI framework). The `ABEventBus`↔`StateFlow` bridge is a Phase 0 deliverable.

### 4.5 Platform seams
- **Interfaces (injected)** for stateful, testable, multi-impl services: JSword/Bible document provider, storage, settings, sharing, clipboard, permissions, WebView host. These are pure Kotlin in commonMain and need **no iOS stub** to compile.
- **`expect`/`actual`** only for narrow, stateless primitives. Minimize these to keep the iOS-compile gate stub-free.
- Shared UI must never reference JSword, Room, or Android-only types directly — always through a seam.

### 4.6 iOS compile gate (enforcement, no iOS app)
`:sharedCore` and `:sharedUi` carry `iosArm64` + `iosSimulatorArm64` targets from Phase 0. `compileKotlinIosSimulatorArm64` runs on **Linux** as a headless gate. It compiles only shared code (commonMain/iosMain); JSword/Room/Dagger/WebView live in `:app` and are never iOS-compiled, so they cannot block it. The gate's value is precisely scoped: it fails immediately if non-portable code (Android-only API, JSword type, non-MP library) leaks into the shared layer — making KMP-readiness real for the code being written now, at ~zero stub cost when seams are plain interfaces. No `:iosApp`, no `MainViewController`, no iOS actuals beyond what compiles.

### 4.7 Testing gates (Linux-first, run after every task)
1. `:sharedCore:jvmTest` (fast shared-logic loop)
2. `compileKotlinIosSimulatorArm64` (iOS-readiness gate, Linux)
3. `:app` unit tests (`testStandardGoogleplayDebugUnitTest`)
4. `:app:assembleStandardGithubDebug` (Android still builds)

Device verification is batched: on-device toggle A/B parity across the four theme modes (dark, light, monochrome/e-ink, no-animations) + RTL is the definition-of-done gate per screen.

---

## 5. Phase 0 — Foundations

One spec + plan. No user-visible screens beyond a pilot. Deliverables:

1. **Module scaffold:** create `:sharedCore`, `:sharedUi`, `:strings-gen`; wire `:app` → `:sharedUi` → `:sharedCore`; add KMP + CMP Gradle plugins and the iOS compile targets; version-catalog entries.
2. **Strings mechanism:** `Strings` interface + `LocalStrings`; `AndroidStrings` `R.string` wrapper + `ProvideAppLocals`; `:strings-gen` generator **with `<plurals>` support** and a real-repo coverage test.
3. **Theme system:** Compose theme covering all four modes (dark/light/monochrome/no-animations), matching current visual output.
4. **DI:** Koin setup in shared modules + bridge to existing Dagger/`DatabaseContainer` singletons.
5. **State architecture + event bridge:** the per-screen state-holder pattern and the `ABEventBus`↔`StateFlow` bridge.
6. **Routing indirection + debug toggle:** `ScreenLauncher` and the developer setting for old/new selection.
7. **Design-system starter:** the handful of shared components (buttons, list scaffolding, dialog scaffolding, app bar) needed by the first batches.
8. **Pilot screen — `discrete/CalculatorActivity`:** fully isolated, ~0 domain coupling; proves the whole toolchain (module, theme, strings, toggle, launcher, all four gates) end to end.

---

## 6. Screen batches (recommended order: easiest → hardest; shared base classes grouped)

Batch size flexes; a large batch may split across multiple plans. This order also teaches the patterns harder screens need.

| # | Batch | Screens | Rationale |
|---|-------|---------|-----------|
| **1** | Light leaves | `ErrorActivity`, `installzip/InstallZip`, `navigation/History`, `progress/SearchIndexProgressStatus` | Establish list + dialog patterns at low risk. |
| **2** | Reading plan | `readingplan/*` (3) | Independent, list-heavy, medium-light. |
| **3** | Navigation choosers | `navigation/GridChoose*` (3) + `ChooseDictionaryWord` + `genbookmap/*` (shared `ChooseKeyBase`) | Grid/chooser pattern once. |
| **4** | Document management | `download/DownloadActivity`, `mydocuments/*` (2), `cloud/CloudDocumentsActivity`, `navigation/ChooseDocument` (shared `DocumentSelectionBase`) | Big shared base ported once. |
| **5** | Search | `search/*` (6, incl. Epub) | Self-contained area. |
| **6** | Speak | `speak/*` (3, shared `AbstractSpeakActivity`) | Independent media controls. |
| **7** | Bookmarks & labels | `bookmark/Bookmarks`, `ManageLabels`, `LabelEditActivity` | Medium-heavy, complex state. |
| **8** | Workspaces + reading progress | `WorkspaceSelectorActivity`, `progress/ReadingProgressActivity` | Heavy but independent. |
| **9** | AI | `ai/*` (11) | Large but independent; partly modern already. |
| **10** | Settings | `settings/SettingsActivity` + 9 preference XMLs | Needs a Compose settings sub-framework first. |
| **11** | Startup flow | `StartupActivity` | Sensitive (permissions, migrations); do once patterns are stable. |
| **12** | 👑 Reading-view complex | `MainBibleActivity`, `SplitBibleArea`, action bar, `OptionsMenuItems`, `WebView` interop, gestures, drawer, window management | Hardest; all patterns in hand. WebView kept via `AndroidView` behind a seam. |
| **Z** | Cleanup + navigation | Remove old implementations; evolve `ScreenLauncher` into the CMP navigation graph (JetBrains androidx-nav, type-safe destinations, per-feature graphs, screen-scoped state holders); single-Activity | Last. |

---

## 7. Coordination mechanics

1. **Branching:** Phase 0 (build-system changes) on a feature branch off `current-stable`; merge once the four gates are green and the pilot works. Subsequent batches land as normal PRs to `current-stable` — the toggle keeps release builds safe (default = old until a screen is verified).
2. **Release toggle:** debug builds flip per screen; a release default flips to new only when the screen is marked verified in the status doc.
3. **Parity tracking:** a living table `docs/compose-port-status.md` — per screen `old-only / both / new-verified / cleaned`. Drives when each old implementation can be deleted.
4. **Definition of done per screen:** feature parity in all four theme modes + RTL, on-device toggle A/B verified against old, tests for state/logic, all four gates green, old implementation removable.
5. **Drift with `current-stable`:** keep batch cycles short so changes to old screens are easy to mirror. The shared domain layer means most ongoing changes don't touch the view layer.
6. **Session hygiene:** each batch is its own session with its own spec+plan; re-read this roadmap at the start (context-decay discipline). Adopt the reference's per-phase notes/handover doc pattern for cross-session continuity.

---

## 8. Decisions adopted from the AndroidMidiRecorder reference

The reference (`fi.sykero.midirecorder`, same author) is a shipped Android+iOS KMP app with a fully documented 6-phase in-place migration in its `docs/`. Decisions carried in:

- 3-module split (`sharedCore`/`sharedUi`/`app` + build-time `strings-gen`).
- `LocalStrings` interface + generated iOS holder instead of compose-resources (preserves the translation pipeline).
- Interfaces for stateful seams; `expect`/`actual` only for narrow primitives.
- `StateFlow` + action-lambda boundary; plain-class controllers + thin Android VM wrappers.
- Room-KMP-behind-repositories with the Android framework driver to preserve existing users' DB — **for the later Room migration**, noted here so the UI keeps Room behind interfaces now.
- Linux-first four-gate discipline, incl. the iOS-compile gate on Linux.
- JetBrains androidx-nav as the navigation library (Voyager fallback), for Batch Z.

Decisions **diverging** from the reference because it does not scale to 35 screens:
- **Koin instead of manual DI** (reference's manual container is unmaintainable at this scale).
- **Per-screen state/actions and per-feature nav graphs** instead of one mega-state and one monolithic NavHost.
- **Hardened `strings-gen`** with `<plurals>` support and robust parsing.

---

## 9. Deferred / open questions (resolved in the relevant later phase)

- Exact Koin module structure (per-feature vs layered) — Phase 0 spec.
- Type-safe navigation destination modeling — Batch Z spec.
- WebView seam shape (`expect`/`actual` vs interface) and gesture handling in Compose — Batch 12 spec.
- Compose settings sub-framework design — Batch 10 spec.
- JSword iOS strategy, Room-KMP migration, `:iosApp` build — **separate future programs, not this one.**

---

## 10. Next step

This roadmap is the anchor. The next session picks it up and produces the **Phase 0 spec + plan** (foundations), then implementation. Each subsequent batch repeats the spec → plan → implement cycle against this document.
