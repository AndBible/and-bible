# CLAUDE.md

@docs/superpowers/CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AndBible is a powerful offline Bible study app for Android built with Kotlin, featuring a hybrid architecture with Vue.js for Bible text rendering and JSword library for Bible data handling.

## Architecture

### Core Components
- **Android App** (`app/`): Kotlin/Android application using Room database, Dagger dependency injection
- **BibleView-JS** (`app/bibleview-js/`): Vue.js 3 + TypeScript frontend for Bible text rendering, built with Vite and embedded in WebView
- **JSword**: Java library (AndBible fork) for SWORD Bible format handling

### Key Patterns
- **Workspace-Centric Design**: Multiple workspaces contain windows, each with different Bible versions, commentaries, and display settings
- **Window Management**: Split-screen support with synchronized scrolling, pinning, and cross-references between windows
- **Hybrid Web/Native**: Bible text rendered in Vue.js WebView with native Android UI for navigation and settings
- **Database Architecture**: Multiple Room databases (`WorkspaceDatabase`, `BookmarkDatabase`, etc.) managed by `DatabaseContainer.kt` singleton

### Android ↔ Vue.js Communication
- Android → Vue.js: `evaluateJavascriptOnUiThread("bibleView.emit('event', data)")`
- Vue.js → Android: `window.android.*` methods via `BibleJavascriptInterface.kt`
- Data serialization: Kotlin `asHashMap` properties → TypeScript interfaces in `documents.ts` and `client-objects.ts`

## Build System

### Prerequisites
- Java toolchain 17 (Gradle `jvmToolchain(17)`; a JDK 17 must be installed even if the default JDK is newer)
- Node.js 24.x (tested with v24.20.0)
- npm 11.x (tested with v11.19.0)
- Android SDK 23+ (API levels 23-36)

### Build Flavors
- **Appearance dimension**: `standard` (normal) vs `discrete` (calculator disguise for persecution-sensitive areas)
- **Distribution dimension**: `googleplay`, `fdroid`, `github`, `samsung`, `huawei`, `amazon`, `accrescent`

### Common Build Commands

**Vue.js Development (works offline after initial setup)**
```bash
cd app/bibleview-js
npm install              # Initial setup (requires internet)
npm run dev              # Development server at http://localhost:5173/
npm run test:ci          # Unit tests (~5-6 seconds, 140+ tests)
npm run lint             # ESLint checking
npm run lint-fix         # Auto-fix lint issues
npm run type-check       # TypeScript validation
npm run build-debug      # Debug build with source maps
npm run build-production # Production build
```

**Android Gradle Build**
```bash
./gradlew assembleStandardGithubDebug     # Debug build
./gradlew assembleStandardGithubRelease   # Release build
./gradlew testStandardGoogleplayDebug     # Unit tests
./gradlew check                           # Full test suite (includes Vue.js tests)
./gradlew connectedStandardGooglePlayDebugAndroidTest  # Instrumented tests (requires emulator)
```

**Makefile Commands**
```bash
make increment-version      # Bump version number
make instrumented-tests     # Run Android instrumented tests
make accrescent            # Build Accrescent release APK
```

### Fast Development Workflow
For rapid iteration, prefer Vue.js development:
```bash
cd app/bibleview-js
npm run test:ci && npm run lint  # Quick validation (~10 seconds)
npm run test:ci && npm run lint && npm run type-check  # Full validation (~16 seconds)
```

Only run Android builds when testing Android-specific integration.

## Testing

**When implementing new features or fixing bugs, always consider adding tests.** Tests should be added whenever reasonably possible — which is almost always. This applies to both Vue.js and Android changes.

**Test quality guidelines:**
- Write tests that genuinely verify logic — not trivial getter/setter tests or tests that just confirm the code compiles
- Use good judgment: think about what could actually break and write tests that catch those cases
- Cover edge cases, boundary conditions, and error paths — not just the happy path
- Unit tests for isolated logic, integration tests when testing component interaction or data flow
- Tests should be meaningful enough that a failing test signals a real problem
- Prefer testing behavior and outcomes over implementation details — tests should survive refactoring
- For bug fixes: write a test that reproduces the bug first (red), then fix it (green)

**IMPORTANT: Only run tests relevant to the changes made.** If only Kotlin/Java files changed, run Android tests. If only Vue.js/TypeScript files changed, run Vue.js tests. Do not run Vue.js tests for Kotlin-only changes or vice versa.

**Vue.js Tests (for Vue.js/TypeScript changes)**
- Test files: `app/bibleview-js/src/__tests__/*.spec.js`
- Run: `cd app/bibleview-js && npm run test:ci`
- Coverage: 140+ tests including DOM manipulation, bookmarks, verse rendering, colors

**Android Tests (for Kotlin/Java changes)**
- Test files: `app/src/test/java/**/*Test.kt`
- Run: `./gradlew testStandardGoogleplayDebugUnitTest`
- For specific tests: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.BookmarkControlTest"`

## Key Files

### Core Application
- `app/src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt`: Central activity managing windows and navigation
- `app/src/main/java/net/bible/android/control/page/window/WindowRepository.kt`: Core workspace and window state management
- `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt`: Database schema definitions
- `app/src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt`: WebView bridge with @JavascriptInterface methods
- `app/src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt`: Manages bookmarks, labels, StudyPads, and MyNotes
- `app/src/main/java/net/bible/service/db/DatabaseContainer.kt`: Singleton managing multiple Room databases
- `app/src/main/java/net/bible/service/common/CommonUtils.kt`: Global app preferences and utilities

### Vue.js Frontend
- `app/bibleview-js/src/main.ts`: Vue.js entry point and Android bridge initialization
- `app/bibleview-js/src/components/BibleView.vue`: Root Vue.js component
- `app/bibleview-js/src/components/documents/DocumentBroker.vue`: Routes document types to appropriate renderers
- `app/bibleview-js/src/composables/android.ts`: Vue.js composable wrapping all Android interface calls
- `app/bibleview-js/src/eventbus.ts`: Internal Vue.js event system using mitt

### Build Configuration
- `build.gradle.kts`: Multi-flavor build configuration
- `app/build.gradle.kts`: Android app-specific build configuration with Vue.js integration
- `app/bibleview-js/vite.config.mts`: Vue.js build configuration using Vite
- `app/bibleview-js/package.json`: Vue.js dependencies and build scripts

## Kotlin Multiplatform / Compose structure

- `:sharedCore` holds the shared logic and models.
- `:sharedUi` holds the Compose Multiplatform screens; they read user-facing text through the `Strings` interface (`LocalStrings`).
- `:strings-gen` does not write that interface. `Strings.kt` (`:sharedUi`) and `AndroidStrings.kt` (`:app`) are hand-maintained; `:strings-gen` (task `generateIosStrings`, wired into `:sharedUi`) only derives the iOS string holder from them and the resources. A new string is therefore: the resource in `strings.xml`, a member in `Strings.kt`, and the override in `AndroidStrings.kt`. Check the generator's "N interface members, N mapped overrides" line to see both sides agree.
- Debug builds get an `applicationIdSuffix` so they install beside a release build: currently `.compose` by default, overridable with `APP_SUFFIX` in `local.properties` (see `app/build.gradle.kts`).
- Golden screenshots are in the submodule described below.

## Test infrastructure traps

- `TEST_SDK` (`app/src/test/java/net/bible/android/TestBibleApplication.kt`) is 33, so code behind API 35+ checks is not exercised by default. A test for such a branch needs `@Config(sdk = [35])`, and should be seen failing before the fix.
- The `:app` unit suite runs in a single JVM (no `maxParallelForks`). A green `--tests` run can miss global-state pollution (Koin overrides, library globals); only the full suite catches it. Restore any global you swap, in `finally`.
- Many unit tests need real Sword modules in `~/.sword`. Extract your copy of the test modules there first (`mkdir -p ~/.sword && unzip -o -d ~/.sword <testmods.zip>`); without them hundreds of tests fail with "no module installed" symptoms such as `IndexOutOfBoundsException` from an empty book list. CI downloads them from an encrypted secret.

## Emulator and WebView debugging

See `docs/emulator-and-webview-debugging.md` (adb model, AVD, `scripts/andbible-emu.sh`, `scripts/webview-cdp.sh`).

## Golden screenshots and process docs (submodules)

- Roborazzi goldens live in `AndBible/and-bible-goldens`, checked out at
  `app/src/test/roborazzi` (`git submodule update --init`). Blessing goldens is two commits:
  record, commit the PNGs **inside** the submodule, then commit the gitlink bump here.
- Specs, plans and history live in the private `AndBible/and-bible-superpowers`, mounted at
  `docs/superpowers` with `update = none` (CI and contributors skip it). Fetch it with
  `git submodule update --init --checkout docs/superpowers`.

## Code Documentation

Add KDoc/Javadoc-style documentation to new classes, functions, and methods when it provides value beyond what the name already conveys. If the name is self-explanatory, documentation is unnecessary. However, explanatory documentation is valuable and expected for complex logic, non-obvious behavior, and larger components.

## Translations / Localization

**During development, only English strings are needed.** Do not add translations to other languages — for languages supported by Claude, translations are handled separately using AI translation tools (see the `update-translations` skill). Transifex is used only for review rounds, not as the primary translation method.

However, **all user-facing strings must go through the translation system**:
- **Android**: Add strings to `app/src/main/res/values/strings.xml`
- **Vue.js/BibleView**: Add strings to `app/bibleview-js/src/lang/default.yaml`

Never hardcode user-visible text directly in code.

## Website and user documentation (`website/`)

andbible.org (landing page, blog, docs at `/docs/`, video catalog) is built from `website/` and
deployed to GitHub Pages from `current-stable`. Authoring rules: `website/content/README.md`.

- **User documentation lives in `website/content/en/docs/`.** A user-visible change (UI, setting,
  feature, behavior) updates the matching docs page in the same PR. A new page goes into the
  `website/zensical.toml` nav.
- App links into the docs are built with `DocsLinks.page("<page>", "<anchor>")`
  (`sharedCore`, `net.bible.sharedcore.docs`); `website/tests/test_app_deep_links.py` checks every
  one against the built docs, so renaming a heading the app links to fails CI.
- Before writing a blog post, read `website/content/README.md`. Blog media goes to the
  `website/media` submodule (`AndBible/andbible-website-media`); commit there, then bump the gitlink.
- Validate any `website/` change with `make site site-check` (needs `uv`).
- `docs/` at the repo root is developer documentation, not user documentation.

## Theme and Display Modes

**Always consider all theme/display variants when making UI changes.** AndBible supports multiple visual modes that must all work correctly:

- **Color themes**: Both **dark** and **light** themes must be supported. Test that colors, contrast, and readability work in both.
- **Monochrome mode**: Designed for **black-and-white e-ink devices**. In this mode, virtually everything should be grayscale — no color hues. This applies especially to `MainBibleActivity` (Android side) and the BibleView-JS (Vue.js side). Avoid introducing colored elements that would look broken on e-ink.
- **No animations setting**: Users can disable animations. Ensure new animations respect this setting and degrade gracefully when disabled.

When adding or modifying UI elements (buttons, highlights, backgrounds, icons, etc.), verify they look correct across all four combinations: dark, light, monochrome, and no-animations.

## Code Patterns

### View Bindings
Always use [View Binding](https://developer.android.com/topic/libraries/view-binding) in new Activities and Fragments. Access views through the generated binding object instead of `findViewById`.

### Kotlin Idioms
Use Kotlin scope functions (`apply`, `let`, `with`, `also`, `run`) to reduce repetition. For example, prefer `binding.apply { ... }` over repeating `binding.` on every line. Apply this consistently whenever it improves readability.

### Settings Management
```kotlin
// Global app preferences
CommonUtils.settings.setBoolean("key", value)

// Window text display setting inheritance
val actualSetting = TextDisplaySettings.actual(windowSettings, workspaceSettings)
```

**Adding a new TextDisplaySetting:** See [docs/adding-text-display-setting.md](docs/adding-text-display-setting.md) for the full checklist (database, migration, XML, strings, Kotlin, Vue.js).

### Database Access
```kotlin
val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
```

### Vue.js Composables
```typescript
// Composables provide reusable logic
const android = useAndroid(globalBookmarks, config)
const scroll = useScroll(config, appSettings, calculatedConfig, verseHighlight, documentPromise)
const globalBookmarks = useGlobalBookmarks(config)

// Provide/inject for global state sharing
provide(androidKey, android)
const android = inject(androidKey)!
```

### FontAwesome Icons in Vue.js
When using `FontAwesomeIcon` in Vue.js components, always import the specific icon object from `@fortawesome/free-solid-svg-icons` and pass it as a bound prop:
```typescript
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {faEdit} from "@fortawesome/free-solid-svg-icons";
// Use: <FontAwesomeIcon :icon="faEdit" />
// NOT: <FontAwesomeIcon icon="edit" />  (string form is unreliable)
```

### Android ↔ Vue.js Communication
```typescript
// Vue.js → Android calls
window.android.scrolledToOrdinal(key, ordinal)
window.android.addBookmark(bookInitials, startOrdinal, endOrdinal, addNote)

// Async operations with deferred responses
async function refChooserDialog(): Promise<string> {
    return await deferredCall((callId) => window.android.refChooserDialog(callId))
}
```

## Database Structure

Multiple specialized databases managed by `DatabaseContainer.kt`:
- `BookmarkDatabase`: Bookmarks, labels, StudyPads, and MyNotes
- `WorkspaceDatabase`: Workspaces, windows, and display settings
- `ReadingPlanDatabase`: Reading plans and progress tracking
- `RepoDatabase`: Document repositories and metadata
- `SettingsDatabase`: Application-level settings
- `TemporaryDatabase`: Temporary data (downloads, document selection)

### Key Entities
- `WorkspaceEntities.Workspace`: Contains windows, settings, and display preferences
- `WorkspaceEntities.Window`: Individual Bible/commentary/dictionary panes with sync settings
- `WorkspaceEntities.PageManager`: Tracks current document and verse for each window
- `BookmarkEntities.*`: Bookmarks, Labels, StudyPads, and MyNotes with complex relationships

All entities use `IdType` (UUID-based) for primary keys.

## Common Development Tasks

### Making Vue.js Changes
1. Make code changes in `app/bibleview-js/src/`
2. Run `npm run test:ci && npm run lint` to validate (~10 seconds)
3. Test in browser with `npm run dev` if needed
4. Build with `npm run build-debug`

### Making Android Changes
1. Make code changes in `app/src/main/java/`
2. For Vue.js integration changes, also rebuild Vue.js: `cd app/bibleview-js && npm run build-debug`
3. Run relevant tests: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*YourTest*"`
4. Build APK: `./gradlew assembleStandardGithubDebug`

### Adding Database Migrations
1. Update entity classes in `WorkspaceEntities.kt` or `BookmarkEntities.kt`
2. Increment database version constant (e.g., `WORKSPACE_DATABASE_VERSION`)
3. Create migration class in `app/src/main/java/net/bible/android/database/migrations/`
4. Add the migration to its database's own migrations array (e.g. `bookmarkMigrations` in
   `BookmarkMigrations.kt`, `workspacesMigrations` in `WorkspacesMigrations.kt`) — that array
   is the actual registration point; `DatabaseContainer.kt` just spreads it
   (`.addMigrations(*bookmarkMigrations)`) and needs no edit of its own.
5. Commit the KSP-generated schema export it produces at `app/schemas/<Database class>/<new
   version>.json` (e.g. `app/schemas/net.bible.android.database.BookmarkDatabase/13.json`). It is
   generated on build (`room.schemaLocation`, `app/build.gradle.kts`) but is not auto-staged by git —
   forgetting it leaves the new version's schema unfrozen, and any test that reads a schema export by
   path (the migration-test pattern below) or a future migration test spanning this version will not
   find it.
6. Check whether the table you're changing is **pinned somewhere that names its columns
   explicitly**, not just referenced generically:
   - An **older migration with an explicit column list** — e.g. `deduplicateSpecialLabels` in
     `BookmarkMigrations.kt` names six now-retired `Label` style columns by hand. It stays correct
     unmodified only because it belongs to an earlier version range and runs before your new
     migration in sequence — but any test that exercises it against a *later*-version database (as
     `BookmarkControlTest` does) needs to re-add the columns it expects first.
   - **`and-bible-ios`'s transcription of the Android schema** (e.g.
     `AndroidBookmarkDatabaseContract.swift`) — it pins a specific schema version's DDL/identity hash
     byte-exactly and has no migrator for every database, so a schema change on the Android side can
     silently stop cross-platform sync for that table's category until iOS is updated to match (see
     `docs/superpowers/history/compose-port-03-rounds-8-17.md`'s "Round 9b — Label style schema migration"
     entry for a worked example of this gate).

## Troubleshooting

### Internet Connectivity
- **Vue.js development**: Works offline after initial `npm install`
- **Android development**: Requires internet for all Gradle builds (dependency downloads)
- If Android builds fail with network errors, check internet connectivity

### Build Issues
```bash
# Vue.js TypeScript errors
cd app/bibleview-js && npm run type-check

# Vue.js linting errors
cd app/bibleview-js && npm run lint-fix  # Auto-fix some issues

# Android build cache issues
./gradlew clean  # Clear build cache (requires internet)
```

### Version Mismatches
```bash
# Verify Java version (must be Java 17)
java --version

# Verify Node.js version (must be 24.x)
node --version

# Verify Android SDK
echo $ANDROID_SDK_ROOT
```

## Git Conventions

- When fixing a GitHub issue, start the commit message with `Fixes #NNN (short bug description)` so GitHub auto-closes the issue. Additional details go on subsequent lines. Example:
  ```
  Fixes #3626 (popup menu search returning 0 results)

  SearchResults now falls back to SEARCH_DOCUMENT when
  SELECTED_TRANSLATIONS is not provided.
  ```

## Notes

- `current-stable` is the stable release branch; feature work happens on topic branches
- Never cancel long-running Gradle builds - they can take 10-45 minutes on first run
- Prefer Vue.js tests for rapid development feedback (5-6 seconds vs minutes for Android tests)
- Always use the repository's standard testing tools (`npm run test:ci`, `./gradlew check`)
