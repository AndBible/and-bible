# Custom Background Image Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users set a personal gallery image as the background behind the Bible text, per workspace and per day/night mode, with an opacity slider for readability.

**Architecture:** Each imported image becomes its own synthetic SWORD module (one module per image), modelled on the existing manually-installed TTF font modules (`TtfBook.kt`). This gives cross-device sync and backup for free via the existing document-sync / module-backup machinery. `WorkspaceEntities.Colors` stores the chosen module's initials (day/night) plus an opacity (day/night). The image is served to the WebView by a new `/background/` asset handler and rendered as a dedicated `.background-image` layer in `BibleView.vue`, hidden in monochrome / e-ink mode.

**Tech Stack:** Kotlin / Android (Room, JSword/SWORD, WebViewAssetLoader, AndroidX Preference), Vue.js 3 + TypeScript (Vite), Vitest, JUnit + Robolectric.

## Global Constraints

- **Copyright header on every new file** (Kotlin, XML, TS): use the year `2026` and the author line `Sykerö Software / Tuomas Airaksinen and the AndBible contributors` — do **not** include "Martin Denham" in new files. Copy the GPL body verbatim from any existing new file (e.g. `TtfBook.kt`). XML files use `<!-- ~ ... -->`; Vue files use `<!-- - ... -->`.
- **Kotlin/Java imports:** always import classes and use simple names — never fully-qualified names inline.
- **All user-facing strings** go through `app/src/main/res/values/strings.xml` (English only during development). No new BibleView-JS (`default.yaml`) strings are needed — the Vue side renders no new text.
- **Every git commit message** ends with the trailer line `Claude-Session: https://claude.ai/code/session_01JZQhCmLAqjvQrVNmNs8S2k` (omitted from the example commands below for brevity — add it).
- **Do not add translations** to non-English locale files.
- **Theme matrix:** the feature must behave correctly in dark, light, monochrome, and e-ink modes. Background images are intentionally **not rendered** in monochrome or e-ink mode.
- **Module marker property:** `AndBibleProvidesBackgroundImage`, value format `"<displayName>;<filename>"` (mirrors `AndBibleProvidesFont`).
- **Build/test runners:**
  - Kotlin unit tests: `./gradlew testStandardGoogleplayDebugUnitTest` (requires `dangerouslyDisableSandbox: true`; the Gradle daemon does not work sandboxed).
  - Vue tests: `cd app/bibleview-js && npm run test:ci`; lint `npm run lint`; types `npm run type-check`.
  - Only run Vue tests for Vue/TS-only changes and Kotlin tests for Kotlin-only changes.

---

## File Structure

**New files**
- `app/src/main/java/net/bible/service/sword/backgroundimage/BackgroundImageBook.kt` — synthetic module builder, driver, discovery, `isBackgroundImageModule`, `backgroundImageFile`, initials helper.
- `app/src/main/java/net/bible/android/view/activity/settings/BackgroundImageChooserActivity.kt` — grid chooser (existing images + None + Import).
- `app/src/main/res/layout/background_image_chooser.xml` — chooser activity layout (RecyclerView).
- `app/src/main/res/layout/background_image_chooser_item.xml` — one thumbnail cell.
- `app/bibleview-js/src/code/background-image.ts` — pure `backgroundImageLayer()` helper.
- Tests: `app/src/test/java/net/bible/service/sword/backgroundimage/BackgroundImageBookTest.kt`, and additions to `app/src/test/java/net/bible/android/control/backup/ModuleBackupRoundTripTest.kt`; `app/bibleview-js/src/__tests__/background-image.spec.js`.

**Modified files**
- `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt` — 4 new `Colors` fields, `merge`, `default`.
- `app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt` — migration 23→24, version bump.
- `app/src/main/java/net/bible/service/common/AndBibleAddons.kt` — `providedBackgroundImages`, `backgroundImageModuleNames`.
- `app/src/main/java/net/bible/android/control/backup/BackupControl.kt` — `addBookToZip` branch + discovery call.
- `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt` — `/background/` path handler.
- `app/src/main/java/net/bible/android/view/activity/installzip/InstallZip.kt` — image MIME types + `installBackgroundImage` branch.
- `app/src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt` — image selection + opacity wiring.
- `app/src/main/res/xml/color_settings.xml` — image + opacity preferences.
- `app/src/main/res/values/strings.xml` — new strings.
- `app/src/main/AndroidManifest.xml` — register chooser activity.
- `app/bibleview-js/src/composables/config.ts` — 4 new `Config.colors` fields + defaults.
- `app/bibleview-js/src/components/BibleView.vue` — `.background-image` layer + computed.

---

## Task 1: Colors data-model fields, merge, default

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/WorkspaceEntities.kt:144-176,360-368`
- Test: `app/src/test/java/net/bible/android/database/ColorsMergeTest.kt` (create)

**Interfaces:**
- Produces: `WorkspaceEntities.Colors` gains constructor params `dayBackgroundImage: String?`, `nightBackgroundImage: String?`, `dayBackgroundImageOpacity: Int?`, `nightBackgroundImageOpacity: Int?` (appended after `nightNoise`). `Colors.merge(override)` falls these back per-field. `TextDisplaySettings.default.colors` sets images `null`, opacities `100`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/net/bible/android/database/ColorsMergeTest.kt` (with the standard 2026 copyright header):

```kotlin
package net.bible.android.database

import net.bible.android.database.WorkspaceEntities.Colors
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorsMergeTest {
    private fun blank() = Colors(null, null, null, null, null, null, null, null, null, null)

    @Test
    fun mergeFallsBackImageFieldsPerField() {
        val base = blank().copy(dayBackgroundImage = "BGIMG_a", dayBackgroundImageOpacity = 40)
        val override = blank().copy(nightBackgroundImage = "BGIMG_b")
        val merged = base.merge(override)
        assertEquals("BGIMG_a", merged.dayBackgroundImage)
        assertEquals("BGIMG_b", merged.nightBackgroundImage)
        assertEquals(40, merged.dayBackgroundImageOpacity)
    }

    @Test
    fun overrideImageWins() {
        val base = blank().copy(dayBackgroundImage = "BGIMG_a")
        val override = blank().copy(dayBackgroundImage = "BGIMG_b")
        assertEquals("BGIMG_b", base.merge(override).dayBackgroundImage)
    }

    @Test
    fun defaultOpacityIsHundredAndImageNull() {
        val colors = WorkspaceEntities.TextDisplaySettings.default.colors!!
        assertEquals(null, colors.dayBackgroundImage)
        assertEquals(null, colors.nightBackgroundImage)
        assertEquals(100, colors.dayBackgroundImageOpacity)
        assertEquals(100, colors.nightBackgroundImageOpacity)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.database.ColorsMergeTest"` (with `dangerouslyDisableSandbox: true`)
Expected: compile failure — `Colors` has no `dayBackgroundImage` parameter.

- [ ] **Step 3: Add the fields**

In `WorkspaceEntities.kt`, extend the `Colors` constructor (after `nightNoise`, line 149):

```kotlin
        @ColumnInfo(defaultValue = "NULL") var nightNoise: Int?,
        @ColumnInfo(defaultValue = "NULL") var dayBackgroundImage: String?,
        @ColumnInfo(defaultValue = "NULL") var nightBackgroundImage: String?,
        @ColumnInfo(defaultValue = "NULL") var dayBackgroundImageOpacity: Int?,
        @ColumnInfo(defaultValue = "NULL") var nightBackgroundImageOpacity: Int?,
```

- [ ] **Step 4: Extend `Colors.merge`**

Add the four fields to the `Colors(...)` returned by `merge` (after `nightNoise = ...`, line 174):

```kotlin
                nightNoise = override.nightNoise ?: nightNoise,
                dayBackgroundImage = override.dayBackgroundImage ?: dayBackgroundImage,
                nightBackgroundImage = override.nightBackgroundImage ?: nightBackgroundImage,
                dayBackgroundImageOpacity = override.dayBackgroundImageOpacity ?: dayBackgroundImageOpacity,
                nightBackgroundImageOpacity = override.nightBackgroundImageOpacity ?: nightBackgroundImageOpacity,
```

- [ ] **Step 5: Extend `default`**

In `TextDisplaySettings.default`, inside the `Colors(...)` block (after `dayNoise = 0,`, line 367):

```kotlin
                    nightNoise = 0,
                    dayNoise = 0,
                    dayBackgroundImage = null,
                    nightBackgroundImage = null,
                    dayBackgroundImageOpacity = 100,
                    nightBackgroundImageOpacity = 100,
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.database.ColorsMergeTest"`
Expected: PASS (3 tests). If Room complains about a schema mismatch at compile time, that is expected and fixed in Task 2 — but this unit test compiles and runs the Kotlin data class directly, so it passes independently.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/database/WorkspaceEntities.kt \
        app/src/test/java/net/bible/android/database/ColorsMergeTest.kt
git commit -m "Add background-image fields to Colors (#3617)"
```

---

## Task 2: Room migration 23→24

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt:290-321`
- Generated: `app/schemas/net.bible.android.database.WorkspaceDatabase/24.json` (Room writes this at build time — commit it)

**Interfaces:**
- Consumes: the four new `Colors` columns from Task 1.
- Produces: `WORKSPACE_DATABASE_VERSION = 24`; migration `addBackgroundImage` registered in `workspacesMigrations`.

The embedded column names follow the existing noise columns: prefix `text_display_settings_colors_`. The columns exist on all three tables that embed `TextDisplaySettings`: `Workspace`, `PageManager`, `GlobalTextDisplaySettings`.

- [ ] **Step 1: Add the migration**

In `WorkspacesMigrations.kt`, after `addShowReadingProgress` (line 294) and before `val workspacesMigrations` (line 296), add:

```kotlin
private val addBackgroundImage = makeMigration(23..24) { _db ->
    for (table in listOf("Workspace", "PageManager", "GlobalTextDisplaySettings")) {
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_dayBackgroundImage` TEXT DEFAULT NULL")
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_nightBackgroundImage` TEXT DEFAULT NULL")
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_dayBackgroundImageOpacity` INTEGER DEFAULT NULL")
        _db.execSQL("ALTER TABLE `$table` ADD COLUMN `text_display_settings_colors_nightBackgroundImageOpacity` INTEGER DEFAULT NULL")
    }
}
```

- [ ] **Step 2: Register the migration and bump the version**

Add `addBackgroundImage,` as the last entry of the `workspacesMigrations` array (after `addShowReadingProgress,`, line 318), and change the version constant (line 321):

```kotlin
    addShowReadingProgress,
    addBackgroundImage,
)

const val WORKSPACE_DATABASE_VERSION = 24
```

- [ ] **Step 3: Build to let Room validate + regenerate the schema**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL. Room validates the entity schema against the migration at build time; a mismatch (wrong column name/type) fails the build. A new `app/schemas/net.bible.android.database.WorkspaceDatabase/24.json` is generated.

- [ ] **Step 4: Sanity-check the migration in a Robolectric test**

Add to `ColorsMergeTest.kt` a DB-open smoke test is NOT needed here (no existing migration-test harness in this repo; the repo relies on Room's build-time schema validation — see the absence of any `MigrationTestHelper` usage). Instead, verify the app DB opens by running the existing workspace-related unit tests:

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*Workspace*"`
Expected: PASS (no schema-validation exception on WorkspaceDatabase creation).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/database/migrations/WorkspacesMigrations.kt \
        app/schemas/net.bible.android.database.WorkspaceDatabase/24.json
git commit -m "Add workspace DB migration for background-image colors (#3617)"
```

---

## Task 3: BackgroundImageBook — synthetic module builder

**Files:**
- Create: `app/src/main/java/net/bible/service/sword/backgroundimage/BackgroundImageBook.kt`
- Test: `app/src/test/java/net/bible/service/sword/backgroundimage/BackgroundImageBookTest.kt`

**Interfaces:**
- Produces (package `net.bible.service.sword.backgroundimage`):
  - `const val BACKGROUND_IMAGE_MARKER = "AndBibleProvidesBackgroundImage"`
  - `const val BACKGROUND_IMAGE_DIR = "background"`
  - `fun backgroundImageModuleInitials(displayName: String, exists: (String) -> Boolean): String`
  - `fun addBackgroundImageBook(file: File)`
  - `fun addManuallyInstalledBackgroundImageBooks()`
  - `val Book.isBackgroundImageModule: Boolean`
  - `val Book.backgroundImageFile: File`
  - `class BackgroundImageSwordDriver : AbstractBookDriver`

- [ ] **Step 1: Write the failing test**

Create `BackgroundImageBookTest.kt` (2026 header):

```kotlin
package net.bible.service.sword.backgroundimage

import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BackgroundImageBookTest {
    private val dir get() = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR)

    @Before
    fun setUp() {
        SwordBookPath.setDownloadDir(SharedConstants.modulesDir)
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        for (b in Books.installed().books.filter { it.isBackgroundImageModule }) {
            Books.installed().removeBook(b)
        }
        dir.deleteRecursively()
    }

    @Test
    fun registersModuleFromImageFile() {
        File(dir, "sunset.jpg").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        addManuallyInstalledBackgroundImageBooks()

        val book = Books.installed().books.firstOrNull { it.isBackgroundImageModule }
        assertNotNull("A background-image module should register", book)
        assertTrue(book!!.initials.startsWith("BGIMG_"))
        assertEquals("sunset.jpg", book.backgroundImageFile.name)
        assertTrue(book.backgroundImageFile.exists())
    }

    @Test
    fun initialsAreSanitizedAndDeduped() {
        val existing = mutableSetOf("BGIMG_my_photo")
        val a = backgroundImageModuleInitials("my photo") { it in existing }
        assertEquals("BGIMG_my_photo_2", a)
        assertFalse("initials must be URL-safe", Regex("[^A-Za-z0-9_]").containsMatchIn(a))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.sword.backgroundimage.BackgroundImageBookTest"`
Expected: compile failure — unresolved references.

- [ ] **Step 3: Implement `BackgroundImageBook.kt`**

```kotlin
package net.bible.service.sword.backgroundimage

import android.util.Log
import net.bible.android.SharedConstants
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.File

private const val TAG = "BackgroundImageBook"

const val BACKGROUND_IMAGE_MARKER = "AndBibleProvidesBackgroundImage"
const val BACKGROUND_IMAGE_DIR = "background"

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

class BackgroundImageSwordDriver : AbstractBookDriver() {
    override fun getBooks(): Array<Book> = emptyArray()
    override fun getDriverName(): String = "BackgroundImageSwordDriver"
    override fun isDeletable(book: Book): Boolean = book.backgroundImageFile.canWrite()
    override fun delete(book: Book) {
        book.backgroundImageFile.delete()
        Books.installed().removeBook(book)
    }
}

val Book.backgroundImageFile: File get() {
    val marker = bookMetaData.getProperty(BACKGROUND_IMAGE_MARKER) ?: ""
    val fileName = marker.split(";").getOrNull(1) ?: ""
    return File(File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR), fileName)
}

/**
 * A manually-installed background-image module: a synthetic book created by
 * [addBackgroundImageBook] from a single image file dropped into `modulesDir/background`.
 * Its metadata is byte-array-constructed, so it has no on-disk `.conf`
 * ([SwordBookMetaData.getConfigFile] is null). As with [net.bible.service.sword.ttf], the
 * marker property alone is not sufficient to distinguish it from a hypothetical downloaded
 * add-on carrying the same marker — we additionally require the absence of a config file.
 */
val Book.isBackgroundImageModule get() =
    bookMetaData.getProperty(BACKGROUND_IMAGE_MARKER) != null &&
        (bookMetaData as? SwordBookMetaData)?.configFile == null

/** Build a URL-safe, unique module initials from a display name. */
fun backgroundImageModuleInitials(displayName: String, exists: (String) -> Boolean): String {
    val sanitized = displayName.substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .ifEmpty { "image" }
    val base = "BGIMG_$sanitized"
    if (!exists(base)) return base
    var i = 2
    while (exists("${base}_$i")) i++
    return "${base}_$i"
}

fun addBackgroundImageBook(file: File) {
    if (!(file.canRead() && file.isFile && file.extension.lowercase() in imageExtensions)) return

    val displayName = file.nameWithoutExtension
    val moduleInitials = backgroundImageModuleInitials(file.name) {
        Books.installed().getBook(it) != null
    }
    if (Books.installed().getBook(moduleInitials) != null) return

    val conf = """
[$moduleInitials]
Description=$displayName
Category=And Bible
ModDrv=RawGenBook
DataPath=./$BACKGROUND_IMAGE_DIR/
Encoding=UTF-8
$BACKGROUND_IMAGE_MARKER=$displayName;${file.name}
AndBibleMinimumVersion=892
"""
    Log.i(TAG, "Creating background-image module $moduleInitials, $displayName")

    val metadata = SwordBookMetaData(conf.toByteArray(), moduleInitials)
    metadata.location = file.parentFile.toURI()
    metadata.driver = BackgroundImageSwordDriver()
    val book = SwordBook(metadata, NullBackend())
    Books.installed().addBook(book)
}

fun addManuallyInstalledBackgroundImageBooks() {
    val dir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR)
    if (!(dir.isDirectory && dir.canRead())) return
    for (f in dir.walkTopDown()) {
        if (f.isFile && f.canRead() && f.extension.lowercase() in imageExtensions) {
            addBackgroundImageBook(f)
        }
    }
}
```

Note on the test's `backgroundImageModuleInitials("my photo")` call: the test passes a display name with no extension. `substringBeforeLast('.')` on `"my photo"` returns `"my photo"` unchanged (no dot), so the base becomes `BGIMG_my_photo`, which already exists → `BGIMG_my_photo_2`. Matches the assertion.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.sword.backgroundimage.BackgroundImageBookTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/sword/backgroundimage/BackgroundImageBook.kt \
        app/src/test/java/net/bible/service/sword/backgroundimage/BackgroundImageBookTest.kt
git commit -m "Add synthetic background-image SWORD module builder (#3617)"
```

---

## Task 4: AndBibleAddons background-image registry

**Files:**
- Modify: `app/src/main/java/net/bible/service/common/AndBibleAddons.kt:29-31,112-120`

**Interfaces:**
- Consumes: `BACKGROUND_IMAGE_MARKER` from Task 3.
- Produces:
  - `class ProvidedBackgroundImage(val book: Book, val name: String, val path: String)` with `val file: File`
  - `AndBibleAddons.providedBackgroundImages: Map<String, ProvidedBackgroundImage>` (keyed by **module initials**)
  - `AndBibleAddons.backgroundImageModuleNames: List<String>`

- [ ] **Step 1: Write the failing test**

Add to `BackgroundImageBookTest.kt`:

```kotlin
    @Test
    fun addonsRegistryExposesInstalledImage() {
        File(dir, "hills.png").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        addManuallyInstalledBackgroundImageBooks()
        net.bible.service.common.AndBibleAddons.clearCaches()

        val book = Books.installed().books.first { it.isBackgroundImageModule }
        val provided = net.bible.service.common.AndBibleAddons.providedBackgroundImages[book.initials]
        assertNotNull("registry should expose the image by module initials", provided)
        assertEquals("hills", provided!!.name)
        assertTrue(net.bible.service.common.AndBibleAddons.backgroundImageModuleNames.contains(book.initials))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.sword.backgroundimage.BackgroundImageBookTest"`
Expected: compile failure — `providedBackgroundImages` unresolved.

- [ ] **Step 3: Implement the registry**

In `AndBibleAddons.kt`, after the `ProvidedFont` class (line 31), add:

```kotlin
class ProvidedBackgroundImage(val book: Book, val name: String, val path: String) {
    val file: File get() = File(File(book.bookMetaData.location), path)
}
```

Inside `object AndBibleAddons`, after `fontsByModule` (line 76), add:

```kotlin
    /** Background-image add-on modules, keyed by module initials (one image per module). */
    val providedBackgroundImages: Map<String, ProvidedBackgroundImage> get() {
        val byModule = mutableMapOf<String, ProvidedBackgroundImage>()
        for (book in addons) {
            val marker = book.bookMetaData.getValues("AndBibleProvidesBackgroundImage")?.firstOrNull() ?: continue
            val values = marker.split(";")
            val provided = ProvidedBackgroundImage(book, values[0], values.getOrElse(1) { "" })
            if (provided.file.canRead()) {
                byModule[book.initials] = provided
            } else {
                Log.e(TAG, "Could not read background image file ${provided.file}")
            }
        }
        return byModule
    }
```

And after `fontModuleNames` (line 113), add:

```kotlin
    val backgroundImageModuleNames: List<String> get() =
        addons.filter { it.bookMetaData.getValues("AndBibleProvidesBackgroundImage") != null }.map { it.initials }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.sword.backgroundimage.BackgroundImageBookTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/common/AndBibleAddons.kt \
        app/src/test/java/net/bible/service/sword/backgroundimage/BackgroundImageBookTest.kt
git commit -m "Expose background-image modules in AndBibleAddons registry (#3617)"
```

---

## Task 5: Backup & sync integration

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/backup/BackupControl.kt:405-415,879-886`
- Test: `app/src/test/java/net/bible/android/control/backup/ModuleBackupRoundTripTest.kt`

**Interfaces:**
- Consumes: `Book.isBackgroundImageModule`, `Book.backgroundImageFile`, `addManuallyInstalledBackgroundImageBooks` from Task 3.

Sync requires **no** code: the module passes `DocumentSync.installedSyncableBooks()` (`!isPseudoBook && !isMyDocument`) automatically. This task only wires backup packaging + restore discovery, verified by a round-trip test that also proves the sync packaging path (`createSingleModuleZip` → `installModuleArchive`, the exact path `DocumentArchiver` uses).

- [ ] **Step 1: Write the failing test**

Add to `ModuleBackupRoundTripTest.kt`. First extend the imports and `tearDown` cleanup, then add the test:

Imports (top of file, alongside the ttf imports):

```kotlin
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
import net.bible.service.sword.backgroundimage.isBackgroundImageModule
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
```

In `tearDown`, after the ttf cleanup, add:

```kotlin
        for (b in Books.installed().books.filter { it.isBackgroundImageModule }) {
            Books.installed().removeBook(b)
        }
        File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR).deleteRecursively()
```

New test:

```kotlin
    @Test
    fun backgroundImageRoundTrips() = runBlocking {
        val imgDir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR).apply { mkdirs() }
        File(imgDir, "sunset.jpg").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03))

        addManuallyInstalledBackgroundImageBooks()
        val book = Books.installed().books.first { it.isBackgroundImageModule }
        val initials = book.initials

        val zipFile = File(CommonUtils.tmpDir, "bgimg-roundtrip.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)

        ZipFile(zipFile).use { zf ->
            assertNotNull(
                "packaged zip must contain the image at its modulesDir-relative path",
                zf.getEntry("$BACKGROUND_IMAGE_DIR/sunset.jpg")
            )
        }

        imgDir.deleteRecursively()
        Books.installed().removeBook(book)
        assertNull(Books.installed().getBook(initials))

        val installed = BackupControl.installModuleArchive(zipFile, initials)
        assertTrue("background image should reinstall from the archive", installed)
        assertNotNull(Books.installed().getBook(initials))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.control.backup.ModuleBackupRoundTripTest.backgroundImageRoundTrips"`
Expected: FAIL — the zip has no `background/sunset.jpg` entry (falls into the generic SWORD branch, whose `bmd.configFile` is null → NPE or wrong packaging).

- [ ] **Step 3: Add the `addBookToZip` branch**

In `BackupControl.kt`, add a branch in `addBookToZip` after the `isManuallyInstalledTtf` branch (line 415, before the final `else`):

```kotlin
        } else if (b.isBackgroundImageModule) {
            val imageFile = b.backgroundImageFile
            if (imageFile.exists()) {
                addModuleFile(outFile, imageFile)
            } else {
                Log.w(TAG, "Skipping background-image module ${b.initials}: file not found ${imageFile.path}")
            }
```

Add the imports at the top of `BackupControl.kt`:

```kotlin
import net.bible.service.sword.backgroundimage.backgroundImageFile
import net.bible.service.sword.backgroundimage.isBackgroundImageModule
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
```

- [ ] **Step 4: Add the restore discovery call**

In `extractAndRegisterModuleArchive`, after `addManuallyInstalledTtfBooks()` (line 884):

```kotlin
        addManuallyInstalledTtfBooks()
        addManuallyInstalledBackgroundImageBooks()
        addManuallyInstalledCsvPromptBooks()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.control.backup.ModuleBackupRoundTripTest"`
Expected: PASS (all tests in the class, including `backgroundImageRoundTrips`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/android/control/backup/BackupControl.kt \
        app/src/test/java/net/bible/android/control/backup/ModuleBackupRoundTripTest.kt
git commit -m "Package and restore background-image modules in backup/sync (#3617)"
```

---

## Task 6: `/background/` WebView asset handler

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/page/BibleView.kt:1085,1127-1135`

**Interfaces:**
- Consumes: `AndBibleAddons.providedBackgroundImages` from Task 4.
- Produces: URL scheme `https://.../background/<moduleInitials>` serving the image bytes.

The handler receives the path **after** the `/background/` prefix — for URL `/background/BGIMG_foo` the `path` argument is `BGIMG_foo` (a single segment, no resource sub-path needed since there is one image per module).

- [ ] **Step 1: Add the handler class**

In `BibleView.kt`, after `FontsAssetHandler` (line 1085), add:

```kotlin
    class BackgroundImageAssetHandler: PathHandler {
        override fun handle(path: String): WebResourceResponse {
            val moduleName = path.trim('/')
            val provided = AndBibleAddons.providedBackgroundImages[moduleName] ?: return notFound
            val f = provided.file
            return if (f.isFile && f.exists()) {
                WebResourceResponse(URLConnection.guessContentTypeFromName(f.name), null, f.inputStream())
            } else notFound
        }
    }
```

Ensure `AndBibleAddons` is imported (it is already used elsewhere in the file via `fontsByModule`; add `import net.bible.service.common.AndBibleAddons` if not present).

- [ ] **Step 2: Register the handler**

In the `assetLoader` builder (line 1130), add the handler alongside `/fonts/`:

```kotlin
        .addPathHandler("/fonts/", FontsAssetHandler())
        .addPathHandler("/background/", BackgroundImageAssetHandler())
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

There is no unit test for the WebView handler (WebViewAssetLoader/WebResourceResponse are Android-framework types not exercised in the JVM unit tests here, matching the untested `FontsAssetHandler`). It is verified end-to-end via the Vue integration in Task 8 and manual device testing in Task 11.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/page/BibleView.kt
git commit -m "Serve background-image module files to the WebView (#3617)"
```

---

## Task 7: Vue pure helper `backgroundImageLayer`

**Files:**
- Create: `app/bibleview-js/src/code/background-image.ts`
- Test: `app/bibleview-js/src/__tests__/background-image.spec.js`

**Interfaces:**
- Produces:
  - `type BackgroundImageColors = { dayBackgroundImage: string | null, nightBackgroundImage: string | null, dayBackgroundImageOpacity: number, nightBackgroundImageOpacity: number }`
  - `type BackgroundImageContext = { nightMode: boolean, monochromeMode: boolean, einkMode: boolean }`
  - `function backgroundImageLayer(colors, ctx): { url: string, opacity: number } | null`

- [ ] **Step 1: Write the failing test**

Create `app/bibleview-js/src/__tests__/background-image.spec.js`:

```javascript
import {describe, it, expect} from "vitest";
import {backgroundImageLayer} from "@/code/background-image";

const colors = {
    dayBackgroundImage: "BGIMG_day",
    nightBackgroundImage: "BGIMG_night",
    dayBackgroundImageOpacity: 80,
    nightBackgroundImageOpacity: 40,
};
const ctx = {nightMode: false, monochromeMode: false, einkMode: false};

describe("backgroundImageLayer", () => {
    it("returns the day image url and opacity in day mode", () => {
        const r = backgroundImageLayer(colors, ctx);
        expect(r).toEqual({url: "/background/BGIMG_day", opacity: 0.8});
    });

    it("returns the night image in night mode", () => {
        const r = backgroundImageLayer(colors, {...ctx, nightMode: true});
        expect(r).toEqual({url: "/background/BGIMG_night", opacity: 0.4});
    });

    it("returns null in monochrome mode", () => {
        expect(backgroundImageLayer(colors, {...ctx, monochromeMode: true})).toBeNull();
    });

    it("returns null in e-ink mode", () => {
        expect(backgroundImageLayer(colors, {...ctx, einkMode: true})).toBeNull();
    });

    it("returns null when no image is set for the current mode", () => {
        expect(backgroundImageLayer({...colors, dayBackgroundImage: null}, ctx)).toBeNull();
        expect(backgroundImageLayer({...colors, dayBackgroundImage: ""}, ctx)).toBeNull();
    });

    it("defaults opacity to 1 when opacity is missing", () => {
        const r = backgroundImageLayer({...colors, dayBackgroundImageOpacity: undefined}, ctx);
        expect(r.opacity).toBe(1);
    });

    it("url-encodes the module initials", () => {
        const r = backgroundImageLayer({...colors, dayBackgroundImage: "BG IMG"}, ctx);
        expect(r.url).toBe("/background/BG%20IMG");
    });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd app/bibleview-js && npm run test:ci -- background-image`
Expected: FAIL — cannot resolve `@/code/background-image`.

- [ ] **Step 3: Implement the helper**

Create `app/bibleview-js/src/code/background-image.ts` (2026 Vue-style header comment):

```typescript
export type BackgroundImageColors = {
    dayBackgroundImage: string | null,
    nightBackgroundImage: string | null,
    dayBackgroundImageOpacity: number,
    nightBackgroundImageOpacity: number,
}

export type BackgroundImageContext = {
    nightMode: boolean,
    monochromeMode: boolean,
    einkMode: boolean,
}

export type BackgroundImageLayerResult = { url: string, opacity: number }

/**
 * Compute the background-image layer for the current display mode, or null when no image
 * should be shown. Images are intentionally hidden in monochrome and e-ink modes.
 */
export function backgroundImageLayer(
    colors: BackgroundImageColors,
    ctx: BackgroundImageContext,
): BackgroundImageLayerResult | null {
    if (ctx.monochromeMode || ctx.einkMode) return null;
    const initials = ctx.nightMode ? colors.nightBackgroundImage : colors.dayBackgroundImage;
    if (!initials) return null;
    const opacityPercent = ctx.nightMode ? colors.nightBackgroundImageOpacity : colors.dayBackgroundImageOpacity;
    const opacity = (opacityPercent ?? 100) / 100;
    return {url: `/background/${encodeURIComponent(initials)}`, opacity};
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd app/bibleview-js && npm run test:ci -- background-image`
Expected: PASS (7 tests).

- [ ] **Step 5: Lint + type-check + commit**

```bash
cd app/bibleview-js && npm run lint && npm run type-check
```
Expected: no errors.

```bash
git add app/bibleview-js/src/code/background-image.ts \
        app/bibleview-js/src/__tests__/background-image.spec.js
git commit -m "Add backgroundImageLayer helper for BibleView (#3617)"
```

---

## Task 8: Vue rendering — config fields + `.background-image` layer

**Files:**
- Modify: `app/bibleview-js/src/composables/config.ts:74-81,185-192`
- Modify: `app/bibleview-js/src/components/BibleView.vue:25,326-335,480-505 (template/script/style)`

**Interfaces:**
- Consumes: `backgroundImageLayer` from Task 7; the four `colors` fields serialized by Kotlin `Colors.toJson()` (arriving via the existing `set_config` merge, which replaces `config.colors` wholesale).

- [ ] **Step 1: Extend the `Config.colors` type**

In `config.ts`, add to the `colors` block of the `Config` type (after `nightTextColor: number,`, line 80):

```typescript
    colors: {
        dayBackground: number,
        dayNoise: number,
        dayTextColor: number,
        nightBackground: number,
        nightNoise: number,
        nightTextColor: number,
        dayBackgroundImage: string | null,
        nightBackgroundImage: string | null,
        dayBackgroundImageOpacity: number,
        nightBackgroundImageOpacity: number,
    },
```

- [ ] **Step 2: Extend the reactive default**

In `useConfig`, add to the `colors` default (after `nightTextColor: white,`, line 191):

```typescript
        colors: {
            dayBackground: white,
            dayNoise: 0,
            dayTextColor: black,
            nightBackground: black,
            nightNoise: 0,
            nightTextColor: white,
            dayBackgroundImage: null,
            nightBackgroundImage: null,
            dayBackgroundImageOpacity: 100,
            nightBackgroundImageOpacity: 100,
        },
```

- [ ] **Step 3: Add the computed + template layer in `BibleView.vue`**

Add the import in the `<script setup>` block (near the other `@/code` imports):

```typescript
import {backgroundImageLayer} from "@/code/background-image";
```

After the `backgroundStyle` computed (line 335), add:

```typescript
const backgroundImageStyle = computed(() => {
    const layer = backgroundImageLayer(config.colors, {
        nightMode: appSettings.nightMode,
        monochromeMode: appSettings.monochromeMode,
        einkMode: appSettings.einkMode,
    });
    if (layer === null) return null;
    return `background-image: url('${layer.url}'); opacity: ${layer.opacity};`;
});
```

In the template, immediately after the `.background` div (line 25):

```html
    <div class="background" :style="backgroundStyle"/>
    <div v-if="backgroundImageStyle" class="background-image" :style="backgroundImageStyle"/>
```

- [ ] **Step 4: Add the CSS**

In the `<style>` block, after the `.background` rule (line 505):

```scss
.background-image {
  z-index: -2;
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  pointer-events: none;
}
```

(Layering: native WebView background colour < `.background` noise at `z-index:-3` < `.background-image` at `z-index:-2` < `#content`. At opacity < 1 the image blends toward the WebView's native background colour, which `BibleView.kt` paints via `setBackgroundColor` — this is the readability fade.)

- [ ] **Step 5: Verify no regressions + types**

Run: `cd app/bibleview-js && npm run test:ci && npm run lint && npm run type-check`
Expected: all existing tests pass; no lint/type errors.

- [ ] **Step 6: Commit**

```bash
git add app/bibleview-js/src/composables/config.ts app/bibleview-js/src/components/BibleView.vue
git commit -m "Render background-image layer in BibleView (#3617)"
```

---

## Task 9: Image import in InstallZip

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/installzip/InstallZip.kt:358-372,434-447,594-656 (add installBackgroundImage)`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `addManuallyInstalledBackgroundImageBooks`, `BACKGROUND_IMAGE_DIR` from Task 3.
- Produces: `InstallZip` can import an image file and register it as a background-image module. `installBackgroundImage(uri, displayName)` returns `Boolean`.

- [ ] **Step 1: Add image MIME types to the picker allow-list**

In `getFileFromUserAndInstall`, extend `EXTRA_MIME_TYPES` (line 372, before the closing `))`):

```kotlin
            "text/csv",
            "text/comma-separated-values",
            "image/png",
            "image/jpeg",
            "image/webp",
        ))
```

- [ ] **Step 2: Dispatch images to `installBackgroundImage`**

In `installFromFile`, after the TTF check (line 442), add:

```kotlin
        // Check for image files (background images)
        if (mimeType?.startsWith("image/") == true ||
            listOf(".png", ".jpg", ".jpeg", ".webp").any { displayName.lowercase().endsWith(it) }) {
            return installBackgroundImage(uri, displayName)
        }
```

- [ ] **Step 3: Implement `installBackgroundImage`**

Add a method modelled on `installTtf` (after `installTtf`, line 656). It copies the picked image into `modulesDir/background` and registers it:

```kotlin
    private suspend fun installBackgroundImage(uri: Uri, displayName_: String?): Boolean = withContext(Dispatchers.IO) {
        val displayName = displayName_ ?: UUID.randomUUID().toString()
        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.VISIBLE
        }
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: throw FileNotFound()
            inputStream.use { fIn ->
                val outDir = File(SharedConstants.modulesDir, BACKGROUND_IMAGE_DIR)
                outDir.mkdirs()
                val outFile = File(outDir, displayName)

                if (outFile.exists()) {
                    val doInstall = withContext(Dispatchers.Main) {
                        suspendCoroutine {
                            AlertDialog.Builder(this@InstallZip)
                                .setTitle(R.string.overwrite_files_title)
                                .setMessage(getString(R.string.overwrite_files, "$BACKGROUND_IMAGE_DIR/$displayName"))
                                .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                                .setNeutralButton(R.string.cancel) { _, _ -> it.resume(false) }
                                .setOnCancelListener { _ -> it.resume(false) }
                                .show()
                        }
                    }
                    if (!doInstall) {
                        withContext(Dispatchers.Main) {
                            ABEventBus.post(ToastEvent(R.string.install_zip_canceled))
                            binding.loadingIndicator.visibility = View.GONE
                        }
                        return@withContext false
                    }
                }

                if ((outFile.exists() && !outFile.canWrite()) || (!outFile.exists() && !outDir.canWrite())) {
                    throw CantWrite()
                }

                withContext(Dispatchers.IO) {
                    val out = FileOutputStream(outFile)
                    fIn.copyTo(out)
                    out.close()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException when reading image file", e)
            withContext(Dispatchers.Main) {
                binding.loadingIndicator.visibility = View.GONE
            }
            throw FileNotFound()
        }

        addManuallyInstalledBackgroundImageBooks()

        withContext(Dispatchers.Main) {
            binding.loadingIndicator.visibility = View.GONE
            ABEventBus.post(ToastEvent(R.string.install_zip_successfull))
            AndBibleAddons.clearCaches()
            setResult(RESULT_OK)
            finish()
        }
        true
    }
```

Add imports at the top of `InstallZip.kt`:

```kotlin
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import net.bible.service.sword.backgroundimage.addManuallyInstalledBackgroundImageBooks
```

(`AndBibleAddons`, `SharedConstants`, `FileOutputStream`, `CantWrite`, `FileNotFound`, `ToastEvent`, `suspendCoroutine` are already imported — used by `installTtf`.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/installzip/InstallZip.kt
git commit -m "Import gallery images as background-image modules (#3617)"
```

---

## Task 10: Background image chooser activity

**Files:**
- Create: `app/src/main/java/net/bible/android/view/activity/settings/BackgroundImageChooserActivity.kt`
- Create: `app/src/main/res/layout/background_image_chooser.xml`
- Create: `app/src/main/res/layout/background_image_chooser_item.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AndBibleAddons.providedBackgroundImages` (Task 4), `InstallZip` (Task 9).
- Produces: an `ActivityBase` that returns, via `setResult`, extra `"selectedInitials"` — a `String?` where `null` means "None" and any other value is a background-image module's initials. Started with no input extras; on "Import from gallery…" it launches `InstallZip` and refreshes.

This is UI: verified by build + manual test (Task 11). The list logic (existing images + two synthetic rows) is the only branching; keep it simple.

- [ ] **Step 1: Create the item layout**

`app/src/main/res/layout/background_image_chooser_item.xml` (XML copyright header):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="8dp"
    android:gravity="center">

    <ImageView
        android:id="@+id/thumbnail"
        android:layout_width="120dp"
        android:layout_height="120dp"
        android:scaleType="centerCrop"
        android:contentDescription="@string/background_image_title"/>

    <TextView
        android:id="@+id/label"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:maxLines="1"
        android:ellipsize="end"
        android:paddingTop="4dp"/>
</LinearLayout>
```

- [ ] **Step 2: Create the activity layout**

`app/src/main/res/layout/background_image_chooser.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.recyclerview.widget.RecyclerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/recyclerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="8dp"
    android:clipToPadding="false"/>
```

- [ ] **Step 3: Add strings**

In `strings.xml`, add:

```xml
    <string name="background_image_title">Background image</string>
    <string name="background_image_none">None</string>
    <string name="background_image_import">Import from gallery…</string>
    <string name="background_image_day">Background image (day)</string>
    <string name="background_image_night">Background image (night)</string>
    <string name="background_image_opacity_day">Background image opacity (day)</string>
    <string name="background_image_opacity_night">Background image opacity (night)</string>
```

- [ ] **Step 4: Implement the activity**

`BackgroundImageChooserActivity.kt` (2026 header). A `GridLayoutManager` RecyclerView; the data list is `[None, Import] + installed images`; thumbnails decoded with `BitmapFactory` + `inSampleSize`.

```kotlin
package net.bible.android.view.activity.settings

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.bible.android.activity.R
import net.bible.android.activity.databinding.BackgroundImageChooserBinding
import net.bible.android.activity.databinding.BackgroundImageChooserItemBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.installzip.InstallZip
import net.bible.service.common.AndBibleAddons
import java.io.File

private sealed class ChooserItem {
    object None : ChooserItem()
    object Import : ChooserItem()
    class Image(val initials: String, val name: String, val file: File) : ChooserItem()
}

class BackgroundImageChooserActivity : ActivityBase() {
    private lateinit var binding: BackgroundImageChooserBinding
    private lateinit var adapter: Adapter

    private val importLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from InstallZip, refresh so a freshly imported image appears.
        AndBibleAddons.clearCaches()
        adapter.submit(buildItems())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = BackgroundImageChooserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.background_image_title)

        adapter = Adapter(::onItemClick)
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@BackgroundImageChooserActivity, 2)
            adapter = this@BackgroundImageChooserActivity.adapter
        }
        adapter.submit(buildItems())
    }

    private fun buildItems(): List<ChooserItem> {
        val images = AndBibleAddons.providedBackgroundImages.map { (initials, p) ->
            ChooserItem.Image(initials, p.name, p.file)
        }.sortedBy { it.name.lowercase() }
        return listOf(ChooserItem.None, ChooserItem.Import) + images
    }

    private fun onItemClick(item: ChooserItem) {
        when (item) {
            is ChooserItem.None -> finishWith(null)
            is ChooserItem.Import -> importLauncher.launch(Intent(this, InstallZip::class.java))
            is ChooserItem.Image -> finishWith(item.initials)
        }
    }

    private fun finishWith(initials: String?) {
        setResult(Activity.RESULT_OK, Intent().putExtra("selectedInitials", initials))
        finish()
    }

    private inner class Adapter(val onClick: (ChooserItem) -> Unit) :
        RecyclerView.Adapter<Adapter.VH>() {
        private var items: List<ChooserItem> = emptyList()

        fun submit(newItems: List<ChooserItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(val itemBinding: BackgroundImageChooserItemBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val itemBinding = BackgroundImageChooserItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(itemBinding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.itemBinding.apply {
                when (item) {
                    is ChooserItem.None -> {
                        label.text = getString(R.string.background_image_none)
                        thumbnail.setImageDrawable(null)
                    }
                    is ChooserItem.Import -> {
                        label.text = getString(R.string.background_image_import)
                        thumbnail.setImageDrawable(null)
                    }
                    is ChooserItem.Image -> {
                        label.text = item.name
                        thumbnail.setImageBitmap(decodeThumbnail(item.file))
                    }
                }
                root.setOnClickListener { onClick(item) }
            }
        }

        private fun decodeThumbnail(file: File) = BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, this)
            var sample = 1
            while (outWidth / sample > 240 || outHeight / sample > 240) sample *= 2
            inJustDecodeBounds = false
            inSampleSize = sample
            BitmapFactory.decodeFile(file.path, this)
        }
    }
}
```

- [ ] **Step 5: Register the activity in the manifest**

In `app/src/main/AndroidManifest.xml`, add alongside the other settings activities (search for `ColorSettingsActivity` and add near it):

```xml
        <activity android:name=".view.activity.settings.BackgroundImageChooserActivity"
            android:label="@string/background_image_title" />
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL (view-binding classes `BackgroundImageChooserBinding` / `BackgroundImageChooserItemBinding` are generated from the layouts).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/settings/BackgroundImageChooserActivity.kt \
        app/src/main/res/layout/background_image_chooser.xml \
        app/src/main/res/layout/background_image_chooser_item.xml \
        app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "Add background-image chooser activity (#3617)"
```

---

## Task 11: ColorSettings integration (preferences + wiring)

**Files:**
- Modify: `app/src/main/res/xml/color_settings.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt`

**Interfaces:**
- Consumes: `BackgroundImageChooserActivity` (Task 10), `AndBibleAddons.providedBackgroundImages` (Task 4), the `Colors` image/opacity fields (Task 1).
- Produces: the color-settings screen persists day/night background-image initials + opacity into `activity.colors`, propagated back via the existing `setResult` (`colors.toJson()`).

The image opacity uses the DataStore (`Int`, like `noise_day`). The image *selection* is not a standard preference widget — it is a plain `Preference` whose click launches the chooser; the returned initials is written directly into `activity.colors` and the preference summary updated.

- [ ] **Step 1: Add preferences to `color_settings.xml`**

In the day `PreferenceCategory`, after the `noise_day` SeekBarPreference (line 48):

```xml
        <Preference
            android:key="background_image_day"
            android:title="@string/background_image_day"/>
        <SeekBarPreference
            android:defaultValue="100"
            android:max="100"
            android:key="background_image_opacity_day"
            android:title="@string/background_image_opacity_day"
            app:showSeekBarValue="true"/>
```

In the night `PreferenceCategory`, after the `noise_night` SeekBarPreference (line 70):

```xml
        <Preference
            android:key="background_image_night"
            android:title="@string/background_image_night"/>
        <SeekBarPreference
            android:defaultValue="100"
            android:max="100"
            android:key="background_image_opacity_night"
            android:title="@string/background_image_opacity_night"
            app:showSeekBarValue="true"/>
```

- [ ] **Step 2: Handle opacity keys in the DataStore**

In `ColorSettingsDataStore`, add to `putInt` (after `"noise_night" -> ...`, line 45):

```kotlin
            "background_image_opacity_day" -> colors.dayBackgroundImageOpacity = value
            "background_image_opacity_night" -> colors.nightBackgroundImageOpacity = value
```

And to `getInt` (after `"noise_night" -> ...`, line 58):

```kotlin
            "background_image_opacity_day" -> colors.dayBackgroundImageOpacity ?: defValue
            "background_image_opacity_night" -> colors.nightBackgroundImageOpacity ?: defValue
```

- [ ] **Step 3: Wire the chooser preferences in the fragment**

Replace `ColorSettingsFragment` in `ColorSettings.kt` with a version that launches the chooser and updates `colors` + summary. Add the needed imports (`Intent`, `androidx.activity.result.contract.ActivityResultContracts`, `net.bible.service.common.AndBibleAddons`):

```kotlin
class ColorSettingsFragment(val isWindow: Boolean = false): PreferenceFragmentCompat() {
    private var editingNight = false

    private val chooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val activity = activity as ColorSettingsActivity
        // extras contains "selectedInitials" (may be absent → treat as null = None)
        val initials = if (result.data?.hasExtra("selectedInitials") == true)
            result.data?.getStringExtra("selectedInitials") else null
        if (editingNight) activity.colors.nightBackgroundImage = initials
        else activity.colors.dayBackgroundImage = initials
        activity.setDirty()
        updateImageSummaries()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as ColorSettingsActivity
        preferenceManager.preferenceDataStore = ColorSettingsDataStore(activity)
        setPreferencesFromResource(R.xml.color_settings, rootKey)
        if(isWindow) {
            findPreference<Preference>("workspace_color")?.isVisible = false
        }
        findPreference<Preference>("background_image_day")?.setOnPreferenceClickListener {
            editingNight = false
            chooserLauncher.launch(Intent(activity, BackgroundImageChooserActivity::class.java))
            true
        }
        findPreference<Preference>("background_image_night")?.setOnPreferenceClickListener {
            editingNight = true
            chooserLauncher.launch(Intent(activity, BackgroundImageChooserActivity::class.java))
            true
        }
        updateImageSummaries()
    }

    private fun nameFor(initials: String?): String {
        if (initials == null) return getString(R.string.background_image_none)
        return AndBibleAddons.providedBackgroundImages[initials]?.name ?: initials
    }

    private fun updateImageSummaries() {
        val activity = activity as ColorSettingsActivity
        findPreference<Preference>("background_image_day")?.summary = nameFor(activity.colors.dayBackgroundImage)
        findPreference<Preference>("background_image_night")?.summary = nameFor(activity.colors.nightBackgroundImage)
    }
}
```

- [ ] **Step 4: Build the debug APK**

Run: `./gradlew assembleStandardGithubDebug` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification on device (all four theme modes)**

Install and drive the flow (see `ai-local/CLAUDE.md` for `adb install -r`):
1. Open a workspace → text settings → Colours.
2. Tap "Background image (day)" → chooser opens → "Import from gallery…" → pick an image → returns and the image is selected; summary shows its name.
3. Bible text now shows the image behind it (day mode). Adjust "Background image opacity (day)" → image fades toward the background colour.
4. Switch to night mode → set a different night image → verify it applies independently.
5. Enable monochrome mode and e-ink mode → verify **no** image is shown (only background colour).
6. Verify the image module appears as an "And Bible" addon in Documents/Downloads, and (if sync enabled) syncs to another device.

Report results. If any mode misbehaves, fix before committing (per systematic-debugging).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/xml/color_settings.xml \
        app/src/main/java/net/bible/android/view/activity/settings/ColorSettings.kt
git commit -m "Wire background-image selection and opacity into colour settings (#3617)"
```

---

## Self-Review (completed during authoring)

**Spec coverage:**
- Gallery-only source → Task 9 (image MIME import). ✓
- Image = SWORD module (sync + backup) → Tasks 3, 5. ✓
- Full inheritance + day/night in Colors → Tasks 1, 2. ✓
- Single opacity slider fading to background colour → Tasks 1, 8, 11 (native WebView colour is the fade target). ✓
- Hidden in monochrome/e-ink → Task 7 helper + Task 8 render. ✓
- Reusable library + import-new chooser → Task 10. ✓
- Visible as addons like fonts → automatic (Category=And Bible), no hiding code; noted in Task 4/Task 11 manual check. ✓
- Serving to WebView → Task 6. ✓
- Stale reference graceful fallback → handler returns `notFound` (Task 6) → Vue shows colour (Task 8); no cleanup code, matching the spec's accepted edge case. ✓
- Tests (Kotlin + Vue) → Tasks 1, 3, 4, 5, 7. ✓

**Type consistency:** `dayBackgroundImage`/`nightBackgroundImage` (`String?`/`string|null`), `dayBackgroundImageOpacity`/`nightBackgroundImageOpacity` (`Int?`/`number`), `BACKGROUND_IMAGE_MARKER`, `BACKGROUND_IMAGE_DIR`, `isBackgroundImageModule`, `backgroundImageFile`, `addManuallyInstalledBackgroundImageBooks`, `providedBackgroundImages`, `backgroundImageLayer`, `BackgroundImageChooserActivity`, extra key `"selectedInitials"` — used identically across all tasks. ✓

**Placeholder scan:** none — every code step contains complete code. ✓
