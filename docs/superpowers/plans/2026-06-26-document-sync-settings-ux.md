# Document Sync Settings UX Simplification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify document-sync settings to a single "on = automatic, sync everything" model: remove the Automatic toggle, relocate the remaining preferences, replace the in-activity setup screen with an enable-time AlertDialog, make Synced Documents reachable whenever a cloud adapter is configured, and cache the cloud listing locally.

**Architecture:** Document sync already has a settings object (`DocumentSyncSettings`), a sync engine (`DocumentSync` + `DocumentSyncService` + resolver), and a management activity (`CloudDocumentsActivity`). This plan removes the `automatic` flag from every layer, restructures `sync_settings.xml` into the two existing preference groups, adds a cloud-listing cache in the non-backed-up `TemporaryDatabase`, and moves the enable-time summary into an AlertDialog in `SyncSettings`.

**Tech Stack:** Kotlin, Android, Room (`TemporaryDatabase`), AndroidX preferences, JUnit (`./gradlew testStandardGoogleplayDebugUnitTest`).

## Global Constraints

- Build/verify: `./gradlew assembleStandardGithubDebug` and unit tests via `./gradlew testStandardGoogleplayDebugUnitTest` (Gradle requires `dangerouslyDisableSandbox: true`).
- Kotlin/Java: import classes, use simple names (no fully-qualified names in code).
- New files copyright header: `Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.` (no "Martin Denham"). Edited existing files: bump year to 2026 and use "Sykerö Software / Tuomas Airaksinen" (keep "Martin Denham" if present).
- All user-facing strings go through `app/src/main/res/values/strings.xml` (English only).
- Commit each task separately after its tests/build pass. Do NOT push.
- Commit message footer: `Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV`
- Current branch: `document-sync`. Make all changes on the current branch (no worktrees).

---

## Task 1: Remove the `automatic` flag from all layers

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt`
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt:36-37`
- Modify: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt:50-55`
- Modify: `app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt` (the `shouldAutoUpload(...)` call)
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt:129-131` (`pullDocuments`)
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt:129-162` (toggle listener — remove auto/manual branch)

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces:
  - `shouldAutoUpload(enabled: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean` (the `automatic` parameter is removed).
  - `DocumentSyncSettings` no longer has an `automatic` property.

- [ ] **Step 1: Update the `shouldAutoUpload` test to the new 3-arg signature (failing)**

In `DocumentSyncOpsTest.kt`, replace the body of `shouldAutoUploadOnlyWhenAllConditionsMet` (lines 50-55) with:

```kotlin
    fun shouldAutoUploadOnlyWhenAllConditionsMet() {
        assertTrue(shouldAutoUpload(enabled = true, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = false, blocked = false, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, blocked = true, autoTransferAllowed = true))
        assertFalse(shouldAutoUpload(enabled = true, blocked = false, autoTransferAllowed = false))
    }
```

- [ ] **Step 2: Run the test — expect a compile failure**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncOpsTest*"` (dangerouslyDisableSandbox: true)
Expected: compilation error — `shouldAutoUpload` still has 4 params / `automatic` unresolved.

- [ ] **Step 3: Drop the `automatic` parameter from `shouldAutoUpload`**

In `DocumentSyncOps.kt` replace lines 35-37 with:

```kotlin
/** Whether an installed document should be auto-uploaded on install. */
fun shouldAutoUpload(enabled: Boolean, blocked: Boolean, autoTransferAllowed: Boolean): Boolean =
    enabled && !blocked && autoTransferAllowed
```

- [ ] **Step 4: Remove the `automatic` property from `DocumentSyncSettings`**

In `DocumentSyncSettings.kt` delete the `AUTOMATIC` const (line 26) and the entire `automatic` property (lines 35-37).

- [ ] **Step 5: Update the `BookInstallWatcher` call site**

In `BookInstallWatcher.kt`, the `shouldAutoUpload(...)` call currently passes `DocumentSyncSettings.automatic` as the second argument. Remove that argument so the call is:

```kotlin
if (shouldAutoUpload(
        DocumentSyncSettings.enabled,
        DocumentSyncSettings.blockList.isBlocked(book.initials),
        DocumentSyncSettings.isAutoTransferAllowed,
    )
) {
```

- [ ] **Step 6: Update `DocumentSync.pullDocuments` guard**

In `DocumentSync.kt`, replace the guard at lines 130-131 with:

```kotlin
        if (!DocumentSyncSettings.enabled) return
        if (automaticOnly && !DocumentSyncSettings.isAutoTransferAllowed) return
```

(The `automaticOnly` parameter stays — it distinguishes the scheduled pull from the manual "Sync now" added in Task 6.)

- [ ] **Step 7: Simplify the `SyncSettings` toggle listener (interim)**

In `SyncSettings.kt` the `sync_enable_documents` change listener (lines 129-162) branches on `DocumentSyncSettings.automatic`. Replace the whole listener body with the single (formerly "manual") path — Task 5 replaces this again with the AlertDialog flow:

```kotlin
preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")!!.run {
    setOnPreferenceChangeListener { _, newValue ->
        val enable = newValue as Boolean
        if (enable) {
            lifecycleScope.launch {
                var signedIn = CloudSync.signedIn
                if (!signedIn) signedIn = CloudSync.signIn(activity as ActivityBase) == true
                if (signedIn) {
                    DocumentSyncSettings.enabled = true
                    startActivity(Intent(requireContext(), CloudDocumentsActivity::class.java))
                }
            }
            false
        } else {
            DocumentSyncSettings.enabled = false
            true
        }
    }
}
```

If removing `EXTRA_SETUP_MODE` usages here causes an unused-import warning, leave the import — Task 6 removes the constant.

- [ ] **Step 8: Run the test — expect pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncOpsTest*"` (dangerouslyDisableSandbox: true)
Expected: PASS.

- [ ] **Step 9: Build to confirm no remaining `automatic` references**

Run: `./gradlew assembleStandardGithubDebug` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL. If it fails on an unresolved `DocumentSyncSettings.automatic`, fix that call site (grep `DocumentSyncSettings.automatic` and `\.automatic`).

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt \
        app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncOps.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncOpsTest.kt \
        app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt \
        app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt \
        app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt
git commit -m "Remove the automatic document-sync flag (sync is always automatic when enabled)

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV"
```

---

## Task 2: Cloud listing cache — entity, DAO, mapping, DB wiring

**Files:**
- Create: `app/src/main/java/net/bible/android/database/CachedCloudDocument.kt`
- Modify: `app/src/main/java/net/bible/android/database/Databases.kt` (TemporaryDatabase entities + version + migration)
- Modify: `app/src/main/java/net/bible/service/db/DatabaseContainer.kt:231-249` (new instance)
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/CloudDocumentCacheMapping.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/CloudDocumentCacheMappingTest.kt`

**Interfaces:**
- Consumes: `DocumentSyncMeta` (`initials, name, documentType, version, size, language, category, sourceDevice, timestamp, cipherKey, deleted`) and `DocumentType` from `DocumentSyncMeta.kt`.
- Produces:
  - `CachedCloudDocument` entity (table `CachedCloudDocument`, `@PrimaryKey initials`).
  - `CloudDocumentCacheDao` with `replaceAll(items)`, `all(): List<CachedCloudDocument>`, `clear()`.
  - `DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()`.
  - `DocumentSyncMeta.toCacheEntity(): CachedCloudDocument` and `CachedCloudDocument.toMeta(): DocumentSyncMeta`.

- [ ] **Step 1: Write the mapping round-trip test (failing)**

Create `CloudDocumentCacheMappingTest.kt`:

```kotlin
package net.bible.service.cloudsync.documents

import net.bible.android.database.toMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudDocumentCacheMappingTest {
    private val meta = DocumentSyncMeta(
        initials = "KJV", name = "King James Version", documentType = DocumentType.SWORD,
        version = "2.6", size = 16384000, language = "en", category = "BIBLE",
        sourceDevice = "dev1", timestamp = 1740000000000, cipherKey = "abc", deleted = true,
    )

    @Test fun roundTripsThroughCacheEntity() {
        assertEquals(meta, meta.toCacheEntity().toMeta())
    }

    @Test fun defaultsSurvive() {
        val minimal = DocumentSyncMeta(
            initials = "X", name = "X", documentType = DocumentType.EPUB, version = "1.0",
            size = 0, language = "", sourceDevice = "d", timestamp = 0,
        )
        val back = minimal.toCacheEntity().toMeta()
        assertEquals("", back.category)
        assertEquals(null, back.cipherKey)
        assertEquals(false, back.deleted)
    }
}
```

- [ ] **Step 2: Run the test — expect compile failure**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*CloudDocumentCacheMappingTest*"` (dangerouslyDisableSandbox: true)
Expected: compile error — `CachedCloudDocument` / `toCacheEntity` / `toMeta` unresolved.

- [ ] **Step 3: Create the entity + DAO**

Create `app/src/main/java/net/bible/android/database/CachedCloudDocument.kt` (with the 2026 Sykerö copyright header):

```kotlin
package net.bible.android.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * A cached snapshot of one cloud document's metadata (mirrors DocumentSyncMeta).
 * Lives in TemporaryDatabase — never backed up, never synced. Pure derived data.
 */
@Entity(tableName = "CachedCloudDocument")
data class CachedCloudDocument(
    @PrimaryKey val initials: String,
    val name: String,
    val documentType: String,
    val version: String,
    val size: Long,
    val language: String,
    val category: String,
    val sourceDevice: String,
    val timestamp: Long,
    val cipherKey: String?,
    val deleted: Boolean,
)

@Dao
interface CloudDocumentCacheDao {
    @Query("SELECT * FROM CachedCloudDocument")
    fun all(): List<CachedCloudDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<CachedCloudDocument>)

    @Query("DELETE FROM CachedCloudDocument")
    fun clear()

    @Transaction
    fun replaceAll(items: List<CachedCloudDocument>) {
        clear()
        insertAll(items)
    }
}
```

- [ ] **Step 4: Create the mapping functions**

Create `app/src/main/java/net/bible/service/cloudsync/documents/CloudDocumentCacheMapping.kt` (2026 Sykerö header):

```kotlin
package net.bible.service.cloudsync.documents

import net.bible.android.database.CachedCloudDocument

fun DocumentSyncMeta.toCacheEntity(): CachedCloudDocument = CachedCloudDocument(
    initials = initials,
    name = name,
    documentType = documentType.name,
    version = version,
    size = size,
    language = language,
    category = category,
    sourceDevice = sourceDevice,
    timestamp = timestamp,
    cipherKey = cipherKey,
    deleted = deleted,
)
```

In `CachedCloudDocument.kt` add the reverse mapping at file end (it needs `DocumentSyncMeta`/`DocumentType` from the cloudsync package, so import them):

```kotlin
// at top imports:
import net.bible.service.cloudsync.documents.DocumentSyncMeta
import net.bible.service.cloudsync.documents.DocumentType

// at file end:
fun CachedCloudDocument.toMeta(): DocumentSyncMeta = DocumentSyncMeta(
    initials = initials,
    name = name,
    documentType = DocumentType.valueOf(documentType),
    version = version,
    size = size,
    language = language,
    category = category,
    sourceDevice = sourceDevice,
    timestamp = timestamp,
    cipherKey = cipherKey,
    deleted = deleted,
)
```

- [ ] **Step 5: Register the entity in TemporaryDatabase + bump version + migration**

In `Databases.kt`, find the `TEMPORARY_DATABASE_VERSION` / `temporaryMigrations` / `TemporaryDatabase` block. Change:

```kotlin
val temporaryMigrations: Array<Migration> = arrayOf(
    makeMigration(1, 2) { db ->
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `CachedCloudDocument` (" +
                "`initials` TEXT NOT NULL, `name` TEXT NOT NULL, `documentType` TEXT NOT NULL, " +
                "`version` TEXT NOT NULL, `size` INTEGER NOT NULL, `language` TEXT NOT NULL, " +
                "`category` TEXT NOT NULL, `sourceDevice` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`cipherKey` TEXT, `deleted` INTEGER NOT NULL, PRIMARY KEY(`initials`))"
        )
    },
)

const val TEMPORARY_DATABASE_VERSION = 2

@Database(
    entities = [
        DocumentSearch::class,
        CachedCloudDocument::class,
    ],
    version = TEMPORARY_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class TemporaryDatabase: RoomDatabase() {
    abstract fun documentSearchDao(): DocumentSearchDao
    abstract fun cloudDocumentCacheDao(): CloudDocumentCacheDao
}
```

Verify `makeMigration` is already imported in `Databases.kt` (it is used by other DBs). The exact `CREATE TABLE` column SQL must match Room's expected schema; if Room throws a schema-mismatch at runtime, copy the statement Room prints in the exception.

- [ ] **Step 6: Add the DatabaseContainer instance**

In `DatabaseContainer.kt`, after the `chooseDocumentsDb` block (line 249), add:

```kotlin
    val cloudDocumentsCacheDb: TemporaryDatabase =
        Room.databaseBuilder(
            application, TemporaryDatabase::class.java, "cloud-documents-cache.sqlite3"
        )
            .allowMainThreadQueries()
            .addMigrations(*temporaryMigrations)
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
```

Add the import `import net.bible.android.database.CachedCloudDocument` only if referenced here (it is not — skip). Ensure `temporaryMigrations` is imported (it is already used at lines 236/246).

- [ ] **Step 7: Run the mapping test — expect pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*CloudDocumentCacheMappingTest*"` (dangerouslyDisableSandbox: true)
Expected: PASS.

- [ ] **Step 8: Build**

Run: `./gradlew assembleStandardGithubDebug` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL (Room KSP processes the new entity/DAO).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/net/bible/android/database/CachedCloudDocument.kt \
        app/src/main/java/net/bible/android/database/Databases.kt \
        app/src/main/java/net/bible/service/db/DatabaseContainer.kt \
        app/src/main/java/net/bible/service/cloudsync/documents/CloudDocumentCacheMapping.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/CloudDocumentCacheMappingTest.kt
git commit -m "Add cloud-listing cache table in TemporaryDatabase

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV"
```

---

## Task 3: Wire the cache into `DocumentSync.scan()`

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt` (`scan()` at lines 64-89, plus a private cache read/write helper and a status-builder extraction)

**Interfaces:**
- Consumes: `CloudDocumentCacheDao` (`replaceAll`, `all`), `toCacheEntity()`, `toMeta()` (Task 2); `DocumentStatusItem` (existing).
- Produces:
  - `scan()` unchanged signature `suspend fun scan(): List<DocumentStatusItem>` but now backed by cache when the network listing is unavailable.
  - `private fun buildStatusItems(cloudMetas: List<DocumentSyncMeta>, local: Map<String, Book>): List<DocumentStatusItem>` (pure-ish builder reused for both the live and cached paths).

- [ ] **Step 1: Extract the status-item builder and add cache read/write in `scan()`**

Replace `scan()` (lines 64-89) with:

```kotlin
    suspend fun scan(): List<DocumentStatusItem> {
        val cacheDao = DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
        val store = store()
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudMetas: List<DocumentSyncMeta> = if (store != null) {
            val live = store.listDocuments()
            cacheDao.replaceAll(live.map { it.toCacheEntity() })   // refresh cache from network
            live
        } else {
            cacheDao.all().map { it.toMeta() }                     // offline / not signed in: use cache
        }
        return buildStatusItems(cloudMetas, local)
    }

    private fun buildStatusItems(
        cloudMetas: List<DocumentSyncMeta>,
        local: Map<String, Book>,
    ): List<DocumentStatusItem> {
        val cloud = cloudMetas.filter { !it.deleted }.associateBy { it.initials }
        val blocked = DocumentSyncSettings.blockList.all()
        val allInitials = (cloud.keys + local.keys).toSortedSet()
        return allInitials.map { initials ->
            val c = cloud[initials]; val b = local[initials]
            val localVersion = b?.let { DocumentArchiver.documentVersion(it) }
            val update = c != null && localVersion != null && versionIsNewer(c.version, localVersion)
            val category = b?.bookCategory ?: parseCategoryName(c?.category)
            DocumentStatusItem(
                initials = initials,
                name = c?.name ?: b?.name ?: initials,
                type = b?.let { DocumentArchiver.documentTypeOf(it) } ?: c?.documentType ?: DocumentType.SWORD,
                cloudVersion = c?.version,
                localVersion = localVersion,
                cloudOnly = c != null && b == null,
                localOnly = c == null && b != null,
                updateAvailable = update,
                blocked = initials in blocked,
                sizeBytes = c?.size ?: 0L,
                category = category,
            )
        }
    }
```

Note: `store.listDocuments()` returns `List<DocumentSyncMeta>` (verify the return type when implementing; if it returns a different cloud-meta type, map it to `DocumentSyncMeta` before caching — the cache stores `DocumentSyncMeta` fields).

- [ ] **Step 2: Build**

Run: `./gradlew assembleStandardGithubDebug` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL. (No new unit test: `scan()` depends on the cloud store and Room; its logic is covered by the existing resolver/mapping tests, and end-to-end behaviour is verified manually on device.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt
git commit -m "Back DocumentSync.scan() with the cloud-listing cache

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV"
```

---

## Task 4: Restructure `sync_settings.xml` and strings

**Files:**
- Modify: `app/src/main/res/xml/sync_settings.xml` (lines 68-132 region)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: nothing.
- Produces: a settings screen where `sync_enable_documents` lives in `sync_category`, `sync_documents_wifi_only` and `document_sync_manage` live in `sync_general`, and there is no `document_sync_category` group and no `sync_documents_automatic` preference. Wiring of visibility/enable is Task 5.

- [ ] **Step 1: Move the master toggle into the categories group**

In `sync_settings.xml`, inside the `sync_category` PreferenceCategory (after `sync_enable_progress`), add:

```xml
<SwitchPreferenceCompat
    app:key="sync_enable_documents"
    app:defaultValue="false"
    app:title="@string/document_sync_title"
    app:summary="@string/document_sync_contents" />
```

- [ ] **Step 2: Move Wi-Fi-only + manage link into General sync, delete the Documents category**

Delete the entire `document_sync_category` PreferenceCategory (the old lines 110-132). Into the `sync_general` PreferenceCategory (after `cloud_sync_folder_path`), add:

```xml
<SwitchPreferenceCompat
    app:key="sync_documents_wifi_only"
    app:defaultValue="true"
    app:title="@string/document_sync_wifi_only_title"
    app:summary="@string/document_sync_wifi_only_summary" />
<Preference
    app:key="document_sync_manage"
    app:title="@string/document_sync_manage_title"
    app:summary="@string/document_sync_manage_summary" />
```

Do NOT re-add `sync_documents_automatic`. Remove the `app:dependency="sync_enable_documents"` attributes (visibility is wired dynamically in Task 5). Keep the existing `app:icon` on `sync_enable_documents` only if it was present; otherwise omit.

- [ ] **Step 2b: Run a lint/string check by building resources**

Run: `./gradlew :app:processStandardGithubDebugResources` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL (no missing-string errors). If it complains about a missing string, the referenced strings already exist (verified in spec) — re-check the key spelling.

- [ ] **Step 3: Remove the obsolete automatic strings**

In `strings.xml` delete these two lines:

```xml
<string name="document_sync_automatic_title">Automatic document sync</string>
<string name="document_sync_automatic_summary">Automatically upload installed documents and download documents from the cloud</string>
```

- [ ] **Step 4: Build**

Run: `./gradlew assembleStandardGithubDebug` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/sync_settings.xml app/src/main/res/values/strings.xml
git commit -m "Restructure sync settings: Documents toggle into categories, Wi-Fi-only + manage link into General sync

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV"
```

---

## Task 5: Enable-dialog flow + dynamic visibility in `SyncSettings`

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSummary.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSummaryTest.kt`
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt` (toggle listener, `onResume`, add visibility/enable wiring)
- Modify: `app/src/main/res/values/strings.xml` (dialog strings)

**Interfaces:**
- Consumes: `DocumentSync.scan()` → `List<DocumentStatusItem>` (fields `localOnly`, `cloudOnly`, `updateAvailable`, `sizeBytes`, `initials`); `DocumentSyncSettings.{enabled, wifiOnly, blockList}`; `DocumentSyncService.start(context, pushInitials, downloadInitials)`; `CloudSync.{signedIn, signIn, cloudAdapter}`.
- Produces:
  - `data class DocumentSyncSummary(val uploadInitials: List<String>, val downloadInitials: List<String>, val uploadBytes: Long, val downloadBytes: Long)` with `uploadCount`/`downloadCount` convenience vals.
  - `fun computeDocumentSyncSummary(items: List<DocumentStatusItem>, blocked: Set<String>): DocumentSyncSummary`.

- [ ] **Step 1: Write the summary computation test (failing)**

Create `DocumentSyncSummaryTest.kt`:

```kotlin
package net.bible.service.cloudsync.documents

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem
import org.crosswire.jsword.book.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncSummaryTest {
    private fun item(
        initials: String, localOnly: Boolean = false, cloudOnly: Boolean = false,
        update: Boolean = false, size: Long = 0,
    ) = DocumentStatusItem(
        initials = initials, name = initials, type = DocumentType.SWORD,
        cloudVersion = null, localVersion = null, cloudOnly = cloudOnly, localOnly = localOnly,
        updateAvailable = update, blocked = false, sizeBytes = size, category = BookCategory.BIBLE,
    )

    @Test fun splitsUploadsAndDownloadsWithSizes() {
        val items = listOf(
            item("UP", localOnly = true, size = 100),
            item("DL", cloudOnly = true, size = 200),
            item("UPD", update = true, size = 50),
            item("SYNCED", size = 999),
        )
        val s = computeDocumentSyncSummary(items, blocked = emptySet())
        assertEquals(listOf("UP"), s.uploadInitials)
        assertEquals(listOf("DL", "UPD"), s.downloadInitials)
        assertEquals(100L, s.uploadBytes)
        assertEquals(250L, s.downloadBytes)
        assertEquals(1, s.uploadCount)
        assertEquals(2, s.downloadCount)
    }

    @Test fun blockedItemsAreExcluded() {
        val items = listOf(
            item("DL", cloudOnly = true, size = 200),
            item("BLK", cloudOnly = true, size = 999),
        )
        val s = computeDocumentSyncSummary(items, blocked = setOf("BLK"))
        assertEquals(listOf("DL"), s.downloadInitials)
        assertEquals(200L, s.downloadBytes)
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncSummaryTest*"` (dangerouslyDisableSandbox: true)
Expected: compile error — `DocumentSyncSummary` / `computeDocumentSyncSummary` unresolved.

- [ ] **Step 3: Implement the summary**

Create `DocumentSyncSummary.kt` (2026 Sykerö header):

```kotlin
package net.bible.service.cloudsync.documents

import net.bible.service.cloudsync.documents.DocumentSync.DocumentStatusItem

/** What an initial document sync would transfer, used by the enable dialog. */
data class DocumentSyncSummary(
    val uploadInitials: List<String>,
    val downloadInitials: List<String>,
    val uploadBytes: Long,
    val downloadBytes: Long,
) {
    val uploadCount get() = uploadInitials.size
    val downloadCount get() = downloadInitials.size
    val isEmpty get() = uploadInitials.isEmpty() && downloadInitials.isEmpty()
}

/**
 * From a [DocumentSync.scan] result, compute uploads (local-only) and downloads
 * (cloud-only or update-available), excluding [blocked] documents.
 */
fun computeDocumentSyncSummary(items: List<DocumentStatusItem>, blocked: Set<String>): DocumentSyncSummary {
    val eligible = items.filterNot { it.initials in blocked }
    val uploads = eligible.filter { it.localOnly }
    val downloads = eligible.filter { it.cloudOnly || it.updateAvailable }
    return DocumentSyncSummary(
        uploadInitials = uploads.map { it.initials },
        downloadInitials = downloads.map { it.initials },
        uploadBytes = uploads.sumOf { it.sizeBytes },
        downloadBytes = downloads.sumOf { it.sizeBytes },
    )
}
```

- [ ] **Step 4: Run — expect pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncSummaryTest*"` (dangerouslyDisableSandbox: true)
Expected: PASS.

- [ ] **Step 5: Add dialog strings**

In `strings.xml` add:

```xml
<string name="document_sync_enable_dialog_title">Start syncing documents?</string>
<string name="document_sync_enable_dialog_nothing">No documents to transfer yet. Newly installed documents will sync automatically.</string>
```

(The body for the non-empty case reuses `cloud_doc_header_totals` = "Upload %1$d (%2$s) · Download %3$d (%4$s)" and appends `cloud_doc_wifi_waiting` when metered.)

- [ ] **Step 6: Replace the toggle listener with the dialog flow**

In `SyncSettings.kt`, replace the `sync_enable_documents` listener (the interim version from Task 1) with:

```kotlin
preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")!!.run {
    setOnPreferenceChangeListener { _, newValue ->
        val enable = newValue as Boolean
        if (enable) {
            lifecycleScope.launch {
                var signedIn = CloudSync.signedIn
                if (!signedIn) signedIn = CloudSync.signIn(activity as ActivityBase) == true
                if (signedIn) {
                    val items = withContext(Dispatchers.IO) { DocumentSync.scan() }
                    val summary = computeDocumentSyncSummary(items, DocumentSyncSettings.blockList.all())
                    showEnableDocumentsDialog(summary)
                }
            }
            false   // committed in the dialog's positive button
        } else {
            DocumentSyncSettings.enabled = false
            updateDocumentSyncVisibility()
            true
        }
    }
}
```

Add the helper methods to the fragment:

```kotlin
private fun showEnableDocumentsDialog(summary: DocumentSyncSummary) {
    val ctx = requireContext()
    val message = if (summary.isEmpty) {
        getString(R.string.document_sync_enable_dialog_nothing)
    } else {
        var m = getString(
            R.string.cloud_doc_header_totals,
            summary.uploadCount, Formatter.formatShortFileSize(ctx, summary.uploadBytes),
            summary.downloadCount, Formatter.formatShortFileSize(ctx, summary.downloadBytes),
        )
        if (DocumentSyncSettings.wifiOnly && CommonUtils.isMeteredNetwork) {
            m += "\n" + getString(R.string.cloud_doc_wifi_waiting)
        }
        m
    }
    AlertDialog.Builder(ctx)
        .setTitle(R.string.document_sync_enable_dialog_title)
        .setMessage(message)
        .setPositiveButton(R.string.okay) { _, _ ->
            DocumentSyncSettings.enabled = true
            DocumentSyncService.start(ctx, summary.uploadInitials, summary.downloadInitials)
            updateDocumentSyncVisibility()
            preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")?.isChecked = true
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

private fun updateDocumentSyncVisibility() {
    val adapterConfigured = CloudSync.cloudAdapter != null
    preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_documents_wifi_only")?.isVisible =
        DocumentSyncSettings.enabled
    preferenceScreen.findPreference<Preference>("document_sync_manage")?.isEnabled = adapterConfigured
}
```

Add imports: `androidx.appcompat.app.AlertDialog`, `android.text.format.Formatter`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`, `net.bible.service.cloudsync.documents.DocumentSync`, `net.bible.service.cloudsync.documents.DocumentSyncSummary`, `net.bible.service.cloudsync.documents.computeDocumentSyncSummary`, `net.bible.service.cloudsync.documents.DocumentSyncService`, `net.bible.service.cloudsync.documents.DocumentSyncSettings`, `net.bible.service.common.CommonUtils`, `androidx.preference.Preference`. (Some already present.)

Verify `CloudSync.cloudAdapter` is accessible from this package; if it is `internal`/private, use the existing accessor used elsewhere to detect a configured adapter (grep `cloudAdapter` / `signedIn`). If no public check exists, gate `document_sync_manage` on `CloudSync.signedIn` instead.

- [ ] **Step 7: Call visibility wiring from `onResume` and after preferences are created**

In `SyncSettings.kt`, at the end of `onCreatePreferences` (after the `document_sync_manage` click listener) and in `onResume()` (replacing the existing `sync_enable_documents` re-read at lines 257-261), call:

```kotlin
preferenceScreen.findPreference<SwitchPreferenceCompat>("sync_enable_documents")?.isChecked =
    DocumentSyncSettings.enabled
updateDocumentSyncVisibility()
```

- [ ] **Step 8: Build**

Run: `./gradlew assembleStandardGithubDebug` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSummary.kt \
        app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSummaryTest.kt \
        app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt \
        app/src/main/res/values/strings.xml
git commit -m "Enable document sync via a summary AlertDialog; dynamic Wi-Fi-only/manage visibility

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV"
```

---

## Task 6: `CloudDocumentsActivity` — remove setup mode, add access gate + "Sync now"

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
- Modify: `app/src/main/res/layout/activity_cloud_documents.xml` (remove the `header` intro TextView)
- Modify: `app/src/main/res/values/strings.xml` (remove setup strings, add "Sync now" + access toast)

**Interfaces:**
- Consumes: `DocumentSync.{scan, pullDocuments}`; `DocumentSyncSettings.enabled`; `CloudSync.{signedIn, signIn}`; cache via `DocumentSync.scan()` (Task 3).
- Produces: a Synced Documents activity with no setup mode; an overflow "Sync now" item visible only when `!DocumentSyncSettings.enabled`.

- [ ] **Step 1: Remove setup-mode code**

In `CloudDocumentsActivity.kt` delete: the `EXTRA_SETUP_MODE` companion constant, `setupMode`/`pendingSetup` fields, reading the extra in `onCreate`, `enterSetupMode()`, `exitSetupMode()`, `updateSetupHeader()`, `performSetupSync()`, the `if (setupMode) performSetupSync() else performBulkAction()` branch (call `performBulkAction()` directly), the `pendingSetup` check in `refresh()`, and any `binding.header` references. In `onSelectionChanged`, remove the `if (setupMode)` branch (keep the normal bulk-download path).

- [ ] **Step 2: Remove the header view from the layout**

In `activity_cloud_documents.xml` delete the `<TextView android:id="@+id/header" … />` block.

- [ ] **Step 3: Add the access gate in `onCreate`/`refresh`**

Replace the initial `refresh()` call in `onCreate` with a sign-in-first gate:

```kotlin
private fun openOrGate() = lifecycleScope.launch {
    var signedIn = CloudSync.signedIn
    if (!signedIn) signedIn = CloudSync.signIn(this@CloudDocumentsActivity) == true
    // refresh() reads from cache when signed-in scan is unavailable (Task 3).
    val items = withContext(Dispatchers.IO) { DocumentSync.scan() }
    if (!signedIn && items.isEmpty()) {
        Toast.makeText(this@CloudDocumentsActivity, R.string.document_sync_signin_required, Toast.LENGTH_LONG).show()
        finish()
        return@launch
    }
    allItems = items
    applyFilter()
}
```

Call `openOrGate()` instead of `refresh()` at the end of `onCreate`. Keep `refresh()` (now without the `pendingSetup` block) for pull-to-refresh and post-action refreshes.

- [ ] **Step 4: Add "Sync now" to the overflow menu**

In `onCreateOptionsMenu`, add a second item:

```kotlin
menu.add(Menu.NONE, MENU_SYNC_NOW, Menu.NONE, R.string.cloud_doc_sync_now)
    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
```

In `onPrepareOptionsMenu`, hide it while auto-sync is on or in selection mode:

```kotlin
menu.findItem(MENU_SYNC_NOW)?.isVisible = !DocumentSyncSettings.enabled && !adapter.isSelectionMode()
```

In `onOptionsItemSelected`, handle it:

```kotlin
MENU_SYNC_NOW -> { runSyncAction { DocumentSync.pullDocuments(automaticOnly = false) }; true }
```

Add `private const val MENU_SYNC_NOW = 2` to the companion. Note `pullDocuments(automaticOnly = false)` bypasses the Wi-Fi-only guard (Task 1 step 6) while honouring the block list and tombstones via the resolver. `runSyncAction` already re-scans on completion.

- [ ] **Step 5: Update strings**

In `strings.xml`: delete `cloud_doc_setup_intro` and `cloud_doc_setup_start`; add:

```xml
<string name="cloud_doc_sync_now">Sync now</string>
<string name="document_sync_signin_required">Sign in to your cloud account to manage synced documents.</string>
```

- [ ] **Step 6: Build**

Run: `./gradlew assembleStandardGithubDebug` (dangerouslyDisableSandbox: true)
Expected: BUILD SUCCESSFUL. Fix any dangling references to removed setup-mode symbols.

- [ ] **Step 7: Install and manually verify on device**

```bash
adb install -r app/build/outputs/apk/standardGithub/debug/app-standard-github-debug.apk
```
(dangerouslyDisableSandbox: true)

Manual checks: toggle Documents on → sign-in → dialog shows counts/sizes → confirm starts sync; back out of dialog leaves it off; Wi-Fi-only row appears only when on; Synced documents opens when adapter configured (signed in or from cache) and shows "Sync now" only when auto-sync is off; "Sync now" runs a transfer.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt \
        app/src/main/res/layout/activity_cloud_documents.xml \
        app/src/main/res/values/strings.xml
git commit -m "Synced Documents: drop setup mode, add sign-in/cache access gate and manual Sync now

Claude-Session: https://claude.ai/code/session_01Bgz5YSX45wALDRU1ZJZ2cV"
```

---

## Self-Review notes (for the implementer)

- **Spec coverage:** §1 settings → Task 4; §2 enable flow → Tasks 1 (interim) + 5 (dialog); §3 activity (setup removal, access gate, Sync now) → Task 6; §4 cache → Tasks 2–3; §5 automatic removal → Task 1; §6 strings/tests → spread across tasks.
- **Sign-in source of truth:** `CloudSync.signIn(activity)` returns `Boolean?` (existing usage in `SyncSettings` checks `== true`). Mirror that.
- **`store.listDocuments()` return type:** confirm it yields `DocumentSyncMeta` before caching (Task 3 step 1 note). If it returns a `CloudDocument`-style type, add a `.toMeta()`-style map first.
- **`CloudSync.cloudAdapter` visibility:** if not reachable from `SyncSettings`, gate `document_sync_manage` on `CloudSync.signedIn` (Task 5 step 6 note).
- **Room migration SQL:** must match Room's generated schema exactly; if a schema-mismatch is thrown at runtime, copy Room's expected `CREATE TABLE` from the exception (Task 2 step 5).
