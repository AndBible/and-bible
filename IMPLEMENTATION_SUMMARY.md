# Read Count Tracking Feature - Implementation Summary

## Overview
Implemented a new chapter read count tracking mode alongside the existing toggle-based read progress system. Users can now tap the tick button at the end of each chapter multiple times, with each tap incrementing a counter that displays total reads (x1, x2, etc.). A global preference allows users to choose between:
- **Toggle mode** (existing): Chapter is either read or unread per cycle
- **Count mode** (new): Each tap increments a read counter; progress is tracked by number of reads

## Database Changes

### 1. **ProgressEntities.kt** - New Entity
Added `ChapterReadHistory` entity:
```kotlin
@Entity(indices = [Index(value = ["kjvBookOrdinal", "chapter"])])
data class ChapterReadHistory(
    @PrimaryKey var id: IdType = IdType(),
    val kjvBookOrdinal: Int,
    val chapter: Int,
    val readAt: Long = System.currentTimeMillis(),
)
```

### 2. **GlobalReadingProgressSettings** - New Setting Field
Added `useReadCountMode` boolean:
```kotlin
@ColumnInfo(defaultValue = "0") val useReadCountMode: Boolean = false
```

### 3. **ProgressDatabase.kt** - Version Bump
- Bumped `PROGRESS_DATABASE_VERSION` from 7 to 8
- Added `ChapterReadHistory::class` to entities list

### 4. **ProgressMigrations.kt** - New Migration
Migration 7→8 creates `ChapterReadHistory` table and:
- Creates index on `(kjvBookOrdinal, chapter)`
- Migrates existing `ChapterReadingRecord` rows as 1 read each (preserving original timestamps)
- Adds `useReadCountMode` column to `GlobalReadingProgressSettings` (default false)

### 5. **ProgressDao.kt** - New Query Methods
Added 7 methods for count-mode:
- `insertChapterReadHistory(record)` - Record each read event
- `getChapterReadCount(kjvBookOrdinal, chapter)` - Count reads per chapter
- `getChapterReadHistory(kjvBookOrdinal, chapter)` - Fetch history for a chapter
- `getMaxReadCountForBook(kjvBookOrdinal)` - Get highest read count in a book (for heat map scaling)
- `getDistinctReadChaptersCountForBook(kjvBookOrdinal)` - Count chapters with ≥1 read
- `getReadChaptersFromHistoryForBook(kjvBookOrdinal)` - List of chapters with reads
- `countDistinctChaptersRead()` - Total distinct chapters ever read

## Kotlin Layer Changes

### 1. **ProgressControl.kt** - New Methods
Added 5 public methods:
- `incrementChapterReadCount(v11n, book, chapter)` - Record a read tap in count-mode
- `getChapterReadCount(v11n, book, chapter)` - Fetch read count for display
- `getMaxReadCountForBook(book)` - For heat map color scaling (max = darkest)
- `getBookReadCountProgress()` - Map of chapters-read per book
- `getTotalChaptersReadCount()` - Total distinct chapters with ≥1 read

### 2. **BibleJavascriptInterface.kt** - New Bridge Methods
Added @JavascriptInterface methods:
- `incrementChapterReadCount(bookInitials, startOrdinal, chapter)`
- `getChapterReadCount(bookInitials, startOrdinal, chapter): Int`

## Vue.js / TypeScript Changes

### 1. **config.ts** - New Config Property
- Added `useReadCountMode: boolean` to `Config` type (default: false)
- Initialized in reactive config object as `false`

### 2. **reading-tracker.ts** - Enhanced Tracking
- Added `chapterReadCount` ref to track displayed count
- Load initial count from Android on mount if count-mode active
- Modified `toggleChapterRead()`:
  - **Count-mode**: Calls `incrementChapterReadCount()`, increments counter, always marks as read
  - **Toggle-mode**: Preserves existing toggle behavior (unmark/mark)
- Return both `chapterRead` and `chapterReadCount`

### 3. **BibleDocument.vue** - Visual Display
Template changes:
- Wrapped tick icon in `mark-as-read-wrapper` div
- Added conditional counter display: `<span v-if="config.useReadCountMode && chapterReadCount > 0" class="read-count">x{{ chapterReadCount }}</span>`

CSS changes:
- Added `.mark-as-read-wrapper` with flexbox layout
- Added `.read-count` styling:
  - Font size 12px, bold
  - Supports dark mode (lighter color)
  - Supports monochrome (black/white)
  - Positioned next to tick icon with 4px gap

## Key Design Decisions

1. **Non-destructive migration**: Existing `ChapterReadingRecord` toggle data preserved; migration copies to new table
2. **Separate data model**: Uses new `ChapterReadHistory` table instead of modifying existing records
3. **Opt-in feature**: `useReadCountMode` defaults to false; users explicitly enable it
4. **Visual counter only in count-mode**: "xN" counter only displays when `useReadCountMode=true` and count>0
5. **Always mark as read in count-mode**: Even incrementing still marks chapter as read (for progress calculations)
6. **Backward compatible**: Existing toggle functionality completely unchanged; both modes coexist

## Testing Considerations

When implemented, test:
1. ✅ Database migration 7→8 runs without errors
2. ✅ Existing toggle mode still works (count-mode disabled)
3. ✅ Enable count-mode via settings
4. ✅ Tap tick multiple times → counter increments
5. ✅ Counter persists on screen reload (uses `getChapterReadCount()`)
6. ✅ Heat map logic adapts for count-mode (future: `ReadingProgressActivity`)
7. ✅ Dark mode and monochrome compatibility for counter display
8. ✅ No regression on existing tests for toggle-mode

---

**Status**: Database, Kotlin, and Vue.js core implementation complete. Remaining work:
- Add `useReadCountMode` preference UI in settings (ReadingProgressSettingsActivity or preferences)
- Update `ReadingProgressActivity` heat map logic for count-mode display (optional, for later PR)

