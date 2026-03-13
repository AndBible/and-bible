# CLAUDE.md

@./ai-local/CLAUDE.md

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
- Java 17 (OpenJDK 17.0.16+8 or higher)
- Node.js 20.x (tested with v20.19.4)
- npm 10.x (tested with v10.8.2)
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

**Android Gradle Build (requires internet)**
```bash
# IMPORTANT: All Android builds require internet connectivity for dependencies
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

## Code Documentation

Add KDoc/Javadoc-style documentation to new classes, functions, and methods when it provides value beyond what the name already conveys. If the name is self-explanatory, documentation is unnecessary. However, explanatory documentation is valuable and expected for complex logic, non-obvious behavior, and larger components.

## Code Patterns

### Settings Management
```kotlin
// Global app preferences
CommonUtils.settings.setBoolean("key", value)

// Window text display setting inheritance
val actualSetting = TextDisplaySettings.actual(windowSettings, workspaceSettings)
```

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
4. Register migration in `DatabaseContainer.kt`

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

# Verify Node.js version (must be 20.x)
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

- Current stable branch that where most development is also done currently: `current-stable`
- Never cancel long-running Gradle builds - they can take 10-45 minutes on first run
- Prefer Vue.js tests for rapid development feedback (5-6 seconds vs minutes for Android tests)
- Always use the repository's standard testing tools (`npm run test:ci`, `./gradlew check`)
