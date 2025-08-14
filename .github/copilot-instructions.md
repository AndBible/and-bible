# AndBible AI Agent Instructions

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

## Development Workflows

### Build System
```bash
# Main Android build (uses JSword and Vue.js build as dependencies)
./gradlew assembleStandardGithubDebug

# Vue.js component development
cd app/bibleview-js && npm run dev

# Fast development testing (recommended)
./gradlew testStandardGoogleplayDebug  # Android unit tests only
cd app/bibleview-js && npm run test:ci  # Vue.js unit tests only

# Individual test execution (fastest for development)
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.BookmarkControlTest"      # Specific test class
./gradlew testStandardGoogleplayDebugUnitTest --tests "*.BookmarkControlTest.testAddBookmark*"  # Test methods with pattern

# Full test suite (slow, CI use only)
./gradlew check  # includes all tests and builds
```

### Vue.js Development Workflow
```bash
# Development server with hot reload
cd app/bibleview-js && npm run dev

# Build for different environments
npm run build-debug      # Debug build with extra logging
npm run build-development # Development build 
npm run build-production  # Production build with optimizations

# Testing and linting
npm run test:ci     # Run Vitest unit tests
npm run lint         # ESLint with Vue.js and TypeScript rules
npm run type-check   # Vue.js TypeScript compilation check
```

### Build Flavors
- **Appearance dimension**: `standard` (normal) vs `discrete` (calculator disguise for persecution-sensitive areas)
- **Distribution dimension**: `googleplay`, `fdroid`, `github`, `samsung`, `huawei`, `amazon`
- Use `BuildVariant.Appearance.isDiscrete` and `BuildVariant.DistributionChannel.*` for flavor-specific code

### Key Database Entities
- `WorkspaceEntities.Workspace`: Contains windows, settings, and display preferences
- `WorkspaceEntities.Window`: Individual Bible/commentary/dictionary panes with sync settings
- `WorkspaceEntities.PageManager`: Tracks current document and verse for each window
- `BookmarkEntities.*`: Bookmarks, Labels, StudyPads, and MyNotes with complex relationships
- Room migrations in `app/src/main/java/net/bible/android/database/migrations/`

## Project-Specific Conventions

### Android Architecture
- **Activity Base Classes**: `ActivityBase` → `CustomTitlebarActivityBase` → specific activities
- **Dependency Injection**: Dagger with `ApplicationComponent` and `ActivityComponent` scopes
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
For rapid development cycles, avoid Gradle's `--tests` option as it triggers full compilation. Instead:
- **Preferred**: Only use `./gradlew testStandardGoogleplayDebugUnitTest --tests "*.TestClass"` when necessary
- **Alternative**: Use VS Code's `runTests` tool for individual test classes/methods
- **Alternative**: Use IDE test runners (IntelliJ/Android Studio) for instant test execution

### Common Test Classes
- `CommonUtilsTest`: Core utility functions
- `BookmarkControlTest`: Bookmark management and label relationships  
- `WindowControlTest`, `WindowRepositoryTest`: Workspace and window management
- `BibleTraverserTest`: Verse navigation and versification
- `BookmarkCsvUtilsTest`: Import/export functionality

## Critical Files for Understanding
- `MainBibleActivity.kt`: Central activity managing windows and navigation
- `WindowRepository.kt`: Core workspace and window state management
- `WorkspaceEntities.kt`: Database schema definitions
- `BibleJavascriptInterface.kt`: WebView bridge with @JavascriptInterface methods for Vue.js communication
- `BookmarkControl.kt`: Manages bookmarks, labels, StudyPads, and MyNotes relationships
- `DatabaseContainer.kt`: Singleton managing multiple Room databases with migrations and cloud sync
- `CloudSync.kt`: Multi-device synchronization with Google Drive and NextCloud adapters
- `CommonUtils.kt`: Global app preferences, utilities, and application lifecycle management
- `app/bibleview-js/src/main.ts`: Vue.js entry point and Android bridge initialization
- `app/bibleview-js/src/components/BibleView.vue`: Root Vue.js component managing document rendering and user interaction
- `app/bibleview-js/src/components/documents/DocumentBroker.vue`: Routes different document types to appropriate renderers
- `app/bibleview-js/src/composables/android.ts`: Vue.js composable wrapping all Android interface calls
- `app/bibleview-js/src/eventbus.ts`: Internal Vue.js event system using mitt
- `build.gradle.kts`: Multi-flavor build configuration with JS integration

## Discrete Mode Special Handling
For persecution-sensitive regions, `discrete` flavor transforms app into calculator appearance:
- Different `applicationId` (`com.app.calculator`)
- `CalculatorActivity` as disguise screen
- Hidden Bible functionality accessible via PIN
- Use `BuildVariant.Appearance.isDiscrete` for conditional features
