# AndBible AI Agent Instructions

**ALWAYS follow these instructions first and only fallback to additional search and context gathering if the information here is incomplete or found to be in error.**

AndBible is a powerful offline Bible study app for Android built with Kotlin, featuring a hybrid architecture with Vue.js for Bible text rendering and JSword library for Bible data handling.

## Architecture Overview

### Core Components
- **Android App** (`app/`): Main Kotlin/Android application using Room database, Dagger dependency injection, and custom activity lifecycle
- **BibleView-JS** (`app/bibleview-js/`): Vue.js 3 + TypeScript frontend for Bible text rendering, built with Vite and embedded in WebView
- **JSword** (`jsword/`): Java library (AndBible fork) for SWORD Bible format handling and CrossWire Bible Society standards

### Key Architectural Patterns
- **Workspace-Centric Design**: Multiple workspaces contain windows, each with different Bible versions, commentaries, and display settings
- **Window Management**: Split-screen support with synchronized scrolling, pinning, and cross-references between windows
- **Hybrid Web/Native**: Bible text rendered in Vue.js WebView with native Android UI for navigation and settings
- **Database Architecture**: Multiple Room databases (`WorkspaceDatabase`, `BookmarkDatabase`, etc.) with migration support

## Prerequisites and Environment Setup

**CRITICAL**: AndBible requires internet connectivity for Android Gradle builds to download dependencies. The Vue.js components can be built and tested offline after initial npm install.

**Required versions** (validated working):
- Java 17 (OpenJDK 17.0.16+8 or higher)
- Node.js 20.x (tested with v20.19.4)
- npm 10.x (tested with v10.8.2) 
- Android SDK 23+ (API levels 23-36)

**Setup commands**:
```bash
# Verify Java version
java --version  # Should show Java 17

# Verify Node.js and npm
node --version  # Should show v20.x
npm --version   # Should show v10.x

# Verify Android SDK
echo $ANDROID_SDK_ROOT  # Should point to Android SDK installation
```

## Working Effectively - Core Build Commands

### Vue.js Development (INTERNET NOT REQUIRED after npm install)
```bash
# Initial setup (REQUIRES INTERNET)
cd app/bibleview-js
npm install  # Takes ~17 seconds, downloads 666 packages

# Core development commands (NO INTERNET REQUIRED)
npm run dev         # Development server - starts in ~337ms at http://localhost:5173/
npm run test:ci     # Unit tests - takes 5-6 seconds, 140+ tests
npm run lint        # ESLint checking - takes ~4 seconds
npm run lint-fix    # Auto-fix lint issues - takes ~4 seconds
npm run type-check  # TypeScript validation - takes ~7 seconds

# Build commands (NO INTERNET REQUIRED)
npm run build              # Full build (type-check + build-only) - takes ~13 seconds
npm run build-debug        # Debug build - takes ~8 seconds, includes source maps
npm run build-development  # Development build - takes ~6 seconds  
npm run build-production   # Production build - takes ~6 seconds, optimized
```

### Android Gradle Build System (REQUIRES INTERNET)
**CRITICAL**: All Android builds require internet connectivity to download Gradle dependencies.

```bash
# NEVER CANCEL these builds - they can take 10-45 minutes on first run
# Build commands (REQUIRES INTERNET - set timeout to 60+ minutes)
./gradlew assembleStandardGithubDebug    # Debug build - NEVER CANCEL
./gradlew assembleStandardGithubRelease  # Release build - NEVER CANCEL

# Test commands (REQUIRES INTERNET - set timeout to 30+ minutes) 
./gradlew testStandardGoogleplayDebug    # Unit tests only - NEVER CANCEL
./gradlew testStandardGoogleplayRelease  # Release unit tests - NEVER CANCEL
./gradlew check                          # Full test suite - NEVER CANCEL (45+ minutes)

# Instrumented tests (REQUIRES INTERNET + Android emulator)
./gradlew connectedStandardGooglePlayDebugAndroidTest  # NEVER CANCEL (60+ minutes)
```

### Build Validation and Timing Expectations
**NEVER CANCEL any build or test command. Wait for completion.**

- **Vue.js npm install**: ~17 seconds (initial setup only)
- **Vue.js tests**: ~5-6 seconds (140+ tests pass)
- **Vue.js linting**: ~4 seconds
- **Vue.js lint-fix**: ~4 seconds (auto-fixes issues)
- **Vue.js type checking**: ~7 seconds
- **Vue.js builds**: ~6-8 seconds (individual variants)
- **Vue.js full build**: ~13 seconds (type-check + build-only in parallel)
- **Vue.js dev server**: ~337ms startup
- **Android Gradle builds**: 10-45 minutes first time, faster subsequent builds
- **Android unit tests**: 15-30 minutes
- **Android instrumented tests**: 60+ minutes
- **Full check**: 45+ minutes

## Validation Scenarios

**ALWAYS run these validation steps after making changes to ensure functionality works correctly:**

### Vue.js Component Validation
```bash
cd app/bibleview-js
# Validate all Vue.js components work
npm run test:ci      # Must pass all 140+ tests
npm run lint         # Must pass without errors
npm run type-check   # Must pass TypeScript validation
npm run build-debug  # Must build successfully

# MANUAL VALIDATION: Start dev server and verify
npm run dev  # Should start on http://localhost:5173/ in ~337ms
# In browser: verify Bible text rendering, bookmark functionality, and no console errors
```

### Android Application Validation
**Note**: Android builds require internet connectivity and take significant time.

```bash
# Unit test validation (REQUIRES INTERNET - 15-30 minutes)
./gradlew testStandardGoogleplayDebug  # NEVER CANCEL

# Build validation (REQUIRES INTERNET - 10-45 minutes) 
./gradlew assembleStandardGithubDebug  # NEVER CANCEL

# MANUAL VALIDATION scenarios to test after successful build:
# 1. Install APK on device/emulator
# 2. Test basic Bible reading functionality
# 3. Test bookmark creation and management
# 4. Test workspace and window management
# 5. Test search functionality
# 6. Verify no crashes during navigation
```

### Development Workflow Validation
```bash
# Fast development cycle (Vue.js only - NO INTERNET REQUIRED)
cd app/bibleview-js
npm run test:ci && npm run lint && npm run type-check && npm run build-debug
# Should complete in ~25 seconds total

# Fastest validation cycle (recommended for rapid iteration)
cd app/bibleview-js  
npm run test:ci && npm run lint  # ~10 seconds - catches most issues

# Full Vue.js validation cycle
cd app/bibleview-js
npm run build  # ~13 seconds - includes type-check and production build

# Pre-commit validation
cd app/bibleview-js && npm run lint  # Fix any linting errors
# Run specific Android tests only if making Android changes
```

### Build Flavors and Android Architecture
- **Appearance dimension**: `standard` (normal) vs `discrete` (calculator disguise for persecution-sensitive areas)
- **Distribution dimension**: `googleplay`, `fdroid`, `github`, `samsung`, `huawei`, `amazon`
- Use `BuildVariant.Appearance.isDiscrete` and `BuildVariant.DistributionChannel.*` for flavor-specific code

### Android Build Variants (validated working)
```bash
# Main build variants used in CI
./gradlew assembleStandardGithubDebug     # Standard debug build
./gradlew assembleStandardGithubRelease   # Standard release build  
./gradlew assembleDiscreteGithubRelease   # Calculator disguise build

# Test variants
./gradlew testStandardGoogleplayDebug     # Unit tests for debug
./gradlew testStandardGoogleplayRelease   # Unit tests for release
```

### Key Database Entities
- `WorkspaceEntities.Workspace`: Contains windows, settings, and display preferences
- `WorkspaceEntities.Window`: Individual Bible/commentary/dictionary panes with sync settings
- `WorkspaceEntities.PageManager`: Tracks current document and verse for each window
- `BookmarkEntities.*`: Bookmarks, Labels, StudyPads, and MyNotes with complex relationships
- Room migrations in `app/src/main/java/net/bible/android/database/migrations/`

## Troubleshooting Common Issues

### Internet Connectivity Problems
```bash
# If Android builds fail with network errors:
# - Check internet connectivity: curl -I https://dl.google.com/dl/android/maven2/
# - Android builds CANNOT work offline - all Gradle dependencies require internet
# - Vue.js builds work offline after initial npm install

# Error: "Could not resolve com.android.tools.build:gradle"
# Solution: Ensure internet connectivity and retry build
```

### Build Environment Issues
```bash
# If Java version is wrong:
java --version  # Must be Java 17
# Update JAVA_HOME if needed

# If Node.js version is wrong:
node --version  # Must be 20.x
# Install correct Node.js version

# If Android SDK is missing:
echo $ANDROID_SDK_ROOT  # Must point to valid Android SDK
# Set ANDROID_SDK_ROOT environment variable
```

### Common Build Failures
```bash
# Vue.js TypeScript compilation errors:
cd app/bibleview-js && npm run type-check  # Shows exact errors
# Fix TypeScript errors before proceeding

# Vue.js linting errors:
cd app/bibleview-js && npm run lint        # Shows linting issues
cd app/bibleview-js && npm run lint-fix    # Auto-fixes some issues

# Android build cache issues (if builds work but tests fail):
./gradlew clean  # Clear build cache (REQUIRES INTERNET)
```

## Project-Specific Conventions

### Working Without Internet Access
When internet connectivity is not available:
- **Vue.js development**: Fully functional after initial `npm install`
- **Android development**: NOT POSSIBLE - all Gradle builds require internet for dependencies
- **Testing**: Vue.js tests work offline, Android tests require internet
- **Linting**: Vue.js linting works offline, Android linting requires Gradle build

### Fast Development Workflow (Recommended)
For rapid iteration cycles, focus on Vue.js components when possible:
```bash
# Fast cycle (NO INTERNET after initial setup)
cd app/bibleview-js
npm run test:ci && npm run lint && npm run build-debug  # ~25 seconds total

# Only run Android builds when specifically testing Android integration
# Android builds take 10-45 minutes and require internet
```
### Android Architecture
- **Activity Base Classes**: `ActivityBase` → `CustomTitlebarActivityBase` → specific activities
- **Event Bus**: Custom `ABEventBus` for cross-component communication
- **Settings Architecture**: 
  - Global app preferences via `CommonUtils.settings` (stored in SettingsDatabase)
  - Text rendering settings via `TextDisplaySettings` (workspace/window specific)
  - Cloud sync settings via `CloudSync` with multi-device synchronization

### Vue.js Frontend (`app/bibleview-js/`)
- **Architecture**: Vue.js 3 Composition API with TypeScript, built with Vite
- **Main Components**: 
  - `BibleView.vue`: Root component managing documents, scrolling, bookmarks, and Android bridge
  - `DocumentBroker.vue`: Routes document types (bible, osis, notes, journal, multi, error, memorize)
  - Document-specific components for rendering different content types
- **Communication Patterns**:
  - Android ↔ Vue.js bridge via `BibleJavascriptInterface` exposed as `window.android`
  - Internal Vue communication via `eventbus.ts` (mitt-based event system)
  - Composables pattern for shared logic (`useAndroid`, `useScroll`, `useBookmarks`, etc.)
- **Build modes**: `debug`, `development`, `production` with different optimization levels
- **Key Composables**:
  - `useAndroid()`: Wraps all Android interface calls, handles async responses
  - `useScroll()`: Manages scrolling animations, position tracking, and verse navigation
  - `useGlobalBookmarks()`: Bookmark state management and label relationships
  - `useConfig()`: Settings and display configuration with inheritance patterns
- **Architecture Patterns**:
  - Composables-first design for shared logic across components
  - Provide/inject pattern for dependency injection and global state
  - Event-driven architecture with mitt-based eventbus for component communication
  - Reactive state management with Vue.js 3 Composition API
  - Document type routing via DocumentBroker for different content types

### Database Patterns
- Use `IdType` (UUID-based) for all primary keys
- Embedded entities for complex settings (e.g., `@Embedded(prefix="text_display_settings_")`)
- All settings support inheritance: window → workspace → global defaults
- Database version constants: `WORKSPACE_DATABASE_VERSION`, `BOOKMARK_DATABASE_VERSION`, etc.
- Multiple specialized databases via `DatabaseContainer.kt` singleton:
  - `BookmarkDatabase`: Bookmarks, labels, StudyPads, and MyNotes
  - `WorkspaceDatabase`: Workspaces, windows, and display settings
  - `ReadingPlanDatabase`: Reading plans and progress tracking
  - `RepoDatabase`: Document repositories and metadata
  - `SettingsDatabase`: Application-level settings
  - `TemporaryDatabase`: Temporary data (downloads, document selection)

### Data Flow Architecture: Kotlin ↔ TypeScript
- **Document Serialization**: Kotlin `Document` classes in `ClientPageObjects.kt` serialize to JSON via `asHashMap` property
  - `BibleDocument` → `BibleDocumentType` (TypeScript) → `BibleDocument.vue`
  - `OsisDocument` → `OsisDocument` (TypeScript) → `OsisDocument.vue`
  - `StudyPadDocument` → `StudyPadDocument` (TypeScript) → `StudyPadDocument.vue`
  - `MyNotesDocument` → `MyNotesDocument` (TypeScript) → `MyNotesDocument.vue`
  - `ErrorDocument` → `ErrorDocument` (TypeScript) → `ErrorDocument.vue`
  - `MultiFragmentDocument` → `MultiFragmentDocument` (TypeScript) → `MultiDocument.vue`
- **Bookmark Objects**: 
  - `ClientBibleBookmark.kt` → `BibleBookmark` (TypeScript) with verse ranges and OSIS references
  - `ClientGenericBookmark.kt` → `GenericBookmark` (TypeScript) for non-Bible bookmarks
  - `ClientBookmarkLabel.kt` → `Label` (TypeScript) with styling and behavior properties
- **Type Safety**: TypeScript `documents.ts` and `client-objects.ts` mirror Kotlin class structures
- **DocumentBroker Pattern**: Vue.js `DocumentBroker.vue` routes document types to specific rendering components

### Key Integration Points
- **WebView Bridge**: `BibleJavascriptInterface.kt` provides @JavascriptInterface methods for Android ↔ Vue.js communication
  - Android calls Vue.js via `evaluateJavascript()` and event emission
  - Vue.js calls Android via `window.android.*` methods with async response handling
  - Bi-directional communication for bookmarks, scrolling, text selection, and navigation
- **Document Rendering**: Vue.js DocumentBroker routes different document types (Bible, OSIS, MyNotes, StudyPad, Multi-reference)
- **JSword Integration**: SWORD module loading, verse parsing, cross-references via Books.installed()
- **Bookmark System**: Complex entity relationships with Labels, StudyPads, and MyNotes via BookmarkControl
- **Settings Inheritance**: Complex cascade from workspace → window-specific settings
- **Cloud Sync**: Multi-device database synchronization via `CloudSync.kt` with Google Drive/NextCloud support

## Common Patterns

### Vue.js Component Architecture
```typescript
// Composables provide reusable logic
const android = useAndroid(globalBookmarks, config)
const scroll = useScroll(config, appSettings, calculatedConfig, verseHighlight, documentPromise)
const globalBookmarks = useGlobalBookmarks(config)

// Provide/inject for global state sharing
provide(androidKey, android)
const android = inject(androidKey)!

// Event-driven communication within Vue.js
import {emit, setupEventBusListener} from "@/eventbus"
emit('set_document', documentData)
setupEventBusListener('clear_document', () => { /* handle event */ })
```

### Android ↔ Vue.js Communication
```typescript
// Vue.js → Android calls via window.android interface
window.android.scrolledToOrdinal(key, ordinal)
window.android.addBookmark(bookInitials, startOrdinal, endOrdinal, addNote)

// Android → Vue.js calls via evaluateJavascript
evaluateJavascriptOnUiThread("bibleView.emit('clear_document')")
evaluateJavascriptOnUiThread("bibleView.emit('add_documents', ...docs)")

// Async operations with deferred responses
async function refChooserDialog(): Promise<string> {
    return await deferredCall((callId) => window.android.refChooserDialog(callId))
}
```

### Data Serialization: Kotlin → TypeScript
```kotlin
// Kotlin Document classes implement asHashMap for JSON serialization
interface Document {
    val asJson: String get() = asHashMap.map {(key, value) -> "'$key': $value"}.joinToString(",", "{", "}")
    val asHashMap: Map<String, Any>
}

// ClientBibleBookmark serializes database entities to JS-compatible format
class ClientBibleBookmark(val bookmark: BookmarkEntities.BibleBookmarkWithNotes, val v11n: Versification?) {
    override val asHashMap: Map<String, String> get() = mapOf(
        "id" to wrapString(bookmark.id.toString()),
        "ordinalRange" to json.encodeToString(serializer(), listOf(start.ordinal, end.ordinal)),
        "notes" to wrapString(bookmark.notes, true),
        // ... other properties
    )
}
```

```typescript
// TypeScript interfaces mirror Kotlin class structure
interface BibleDocumentType extends BaseOsisDocument {
    type: "bible"
    bookmarks: BibleBookmark[]
    bibleBookName: string
    chapterNumber: number
    originalOrdinalRange: OrdinalRange
}

// Vue.js components consume TypeScript-typed documents
const props = defineProps<{ document: BibleDocumentType }>()
const {bookmarks, bookInitials, ordinalRange} = props.document
```

### Common Development Patterns
```kotlin
// Settings Management
CommonUtils.settings.setBoolean("key", value)  // Global app preferences
val actualSetting = TextDisplaySettings.actual(windowSettings, workspaceSettings)  // Window text display setting inheritance pattern

// Database Access
val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()
val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

// JavaScript Interface
@JavascriptInterface
fun scrolledToOrdinal(keyStr: String, ordinal: Int) { ... }

// Async operations
scope.launch(Dispatchers.Main) {
    linkControl.loadApplicationUrl(bibleLink)
}

// Bookmark System
bookmarkControl.toggleBookmarkLabel(bookmark, labelId)
bookmarkControl.createStudyPadEntry(labelId, entryOrderNumber)
```

## Testing Strategy
- Android unit tests in `app/src/test/`
- Vue.js unit tests with Vitest in `app/bibleview-js/src/__tests__/`
- Integration tests cover database migrations and workspace management
- Use `BuildConfig.FLAVOR_*` for build variant testing

### Fast Individual Test Execution
For rapid development cycles, prioritize Vue.js tests over Android tests:

**Vue.js Tests (RECOMMENDED - NO INTERNET after npm install)**
```bash
cd app/bibleview-js
npm run test:ci     # 5-6 seconds, 140+ tests
npm run lint        # 4 seconds  
npm run type-check  # 7 seconds
# Total: ~16 seconds for full Vue.js validation
```

**Android Tests (REQUIRES INTERNET - use sparingly during development)**
```bash
# Individual test execution (fastest Android option, but still requires internet)
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.BookmarkControlTest"      # Specific test class
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.BookmarkControlTest.testAddBookmark*"  # Test methods with pattern

# Full Android test suite (CI use only - NEVER CANCEL - 45+ minutes)
./gradlew check  # includes all tests and builds
```

### Validated Test Classes and Timing
**Vue.js Tests** (verified working, 5-6 seconds total):
- `dom.spec.js`: 62 tests - DOM manipulation and text processing
- `bookmarks.spec.js`: 39 tests - Bookmark functionality 
- `wordscramble.spec.js`: 15 tests - Word scramble game component
- `memorizedocument.spec.js`: 8 tests - Memory verse functionality
- `wordblur.spec.js`: 6 tests - Word blur effects
- `verse.spec.js`: 13 tests - Verse rendering component
- `colors.spec.js`: 17 tests - Color utility functions

**Android Test Classes** (require internet connectivity):
- `CommonUtilsTest`: Core utility functions
- `BookmarkControlTest`: Bookmark management and label relationships  
- `WindowControlTest`, `WindowRepositoryTest`: Workspace and window management
- `BibleTraverserTest`: Verse navigation and versification
- `BookmarkCsvUtilsTest`: Import/export functionality

## Critical Files for Understanding

### Core Application Files
- `app/src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt`: Central activity managing windows and navigation
- `app/src/main/java/net/bible/android/control/page/window/WindowRepository.kt`: Core workspace and window state management
- `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt`: Database schema definitions
- `app/src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt`: WebView bridge with @JavascriptInterface methods for Vue.js communication
- `app/src/main/java/net/bible/android/control/bookmark/BookmarkControl.kt`: Manages bookmarks, labels, StudyPads, and MyNotes relationships
- `app/src/main/java/net/bible/service/db/DatabaseContainer.kt`: Singleton managing multiple Room databases with migrations and cloud sync
- `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt`: Multi-device synchronization with Google Drive and NextCloud adapters
- `app/src/main/java/net/bible/service/common/CommonUtils.kt`: Global app preferences, utilities, and application lifecycle management

### Vue.js Frontend Files
- `app/bibleview-js/src/main.ts`: Vue.js entry point and Android bridge initialization
- `app/bibleview-js/src/components/BibleView.vue`: Root Vue.js component managing document rendering and user interaction
- `app/bibleview-js/src/components/documents/DocumentBroker.vue`: Routes different document types to appropriate renderers
- `app/bibleview-js/src/composables/android.ts`: Vue.js composable wrapping all Android interface calls
- `app/bibleview-js/src/eventbus.ts`: Internal Vue.js event system using mitt
- `app/bibleview-js/package.json`: Vue.js dependencies and build scripts

### Build Configuration Files
- `build.gradle.kts`: Multi-flavor build configuration with JS integration
- `app/build.gradle.kts`: Android app-specific build configuration and Vue.js integration
- `app/bibleview-js/vite.config.mts`: Vue.js build configuration using Vite
- `app/bibleview-js/tsconfig.json`: TypeScript configuration for Vue.js components

### Test Files Structure
- `app/src/test/java/`: Android unit tests (requires internet for Gradle)
- `app/src/androidTest/java/`: Android instrumented tests (requires internet + emulator)
- `app/bibleview-js/src/__tests__/`: Vue.js unit tests (works offline after npm install)

### Common Repository Navigation
```bash
# Repository root files
ls -la  # Shows: build.gradle.kts, settings.gradle.kts, Makefile, README.md, etc.

# Android source code
find app/src/main/java -name "*.kt" | head -10  # Kotlin source files
find app/src/test/java -name "*.kt" | head -5   # Unit test files

# Vue.js source code  
find app/bibleview-js/src -name "*.vue" -o -name "*.ts" | head -10  # Vue.js components
find app/bibleview-js/src/__tests__ -name "*.spec.*" | head -5      # Vue.js test files

# Build and configuration files
find . -name "*.gradle*" -o -name "package.json" -o -name "*.config.*"
```

## Discrete Mode Special Handling
For persecution-sensitive regions, `discrete` flavor transforms app into calculator appearance:
- Different `applicationId` (`com.app.calculator`)
- `CalculatorActivity` as disguise screen
- Hidden Bible functionality accessible via PIN
- Use `BuildVariant.Appearance.isDiscrete` for conditional features
