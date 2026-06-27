# Document Sync — Incremental Cloud Listing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a no-op document sync near-instant by listing the cloud incrementally (DB-sync's `createdTimeAtLeast` watermark model), and consolidate all per-device document-sync state into a renamed `DocumentSyncDatabase`.

**Architecture:** Rename `CacheDatabase` → `DocumentSyncDatabase` (new file, not backed up, not synced, wiped on sign-out) and give it a `DocumentSyncPreferences` settings singleton, a `CloudListingState` watermark singleton, a `CloudDocumentSyncTimestamp` keyed table, plus the existing `CachedCloudDocument` cache. `DocumentStore` gains a batched `listChangedDocuments(watermark)`; `DocumentSync.refreshCache()` merges the changed metas into the cache via a pure `mergeCloudListing` and advances the watermark. `scan()` / `runSync()` route through `refreshCache()`.

**Tech Stack:** Kotlin, Room (with `Converters`, `IdType` PK singletons), Robolectric + JUnit4 for tests, coroutines.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-06-27-document-sync-incremental-listing-design.md`.
- New-file copyright header (current year 2026, "Sykerö Software / Tuomas Airaksinen", **no** "Martin Denham"); edited files: bump year to 2026 and use "Sykerö Software / Tuomas Airaksinen".
- All repo text (code, comments, commit messages) in **English**.
- Kotlin/Room: import classes, use simple names (no fully-qualified names in code).
- `DocumentSyncDatabase` must stay **out of** `ALL_DB_FILENAMES` (not backed up) and **out of** `SyncableDatabaseDefinition` (not synced).
- Run Kotlin unit tests with: `./gradlew testStandardGoogleplayDebugUnitTest -x npmInstall -x npmUpgrade -x jsBuild` (add `--tests "<pattern>"` to scope), `dangerouslyDisableSandbox: true`.
- No data migration: existing `SettingsDatabase` doc-sync keys are abandoned (settings reset to defaults once).

---

### Task 1: Rename `CacheDatabase` → `DocumentSyncDatabase` (mechanical, behavior-preserving)

Pure rename + file-name change. No new tables, no behavior change yet. The DB file changes name, so the old cache is dropped (rebuildable) and the orphaned old file is deleted on first run.

**Files:**
- Modify: `app/src/main/java/net/bible/android/database/Databases.kt:140-160`
- Modify: `app/src/main/java/net/bible/service/db/DatabaseContainer.kt:29,252-259` (+ `init` block at `:84-88` area)
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt` (7 references)
- Modify: `app/src/main/java/net/bible/android/database/CachedCloudDocument.kt:30-33` (doc comment)

**Interfaces:**
- Produces: `DocumentSyncDatabase` class with `fun cloudDocumentCacheDao(): CloudDocumentCacheDao`; `DatabaseContainer.instance.documentSyncDb: DocumentSyncDatabase`; `const val DOCUMENT_SYNC_DATABASE_VERSION = 1`.

- [ ] **Step 1: Rename the database class in `Databases.kt`**

Replace lines 140-160 (the `CACHE_DATABASE_VERSION` const + `CacheDatabase` class + its doc comment) with:

```kotlin
const val DOCUMENT_SYNC_DATABASE_VERSION = 1

/**
 * Home for all per-device document-sync state — settings, the cloud-listing cache, the listing
 * watermark, and per-document sync timestamps. Entirely device-local: never backed up (absent from
 * ALL_DB_FILENAMES), never synced (absent from SyncableDatabaseDefinition), and wiped on cloud
 * sign-out. Document-sync setup is re-established per device (the cloud account itself is
 * device-local), so there is nothing here to back up or sync.
 *
 * Kept separate from [TemporaryDatabase]: that one is single-purpose search scratch with its own
 * (multi-file) lifecycle, and conflating the two schemas in one class would force every file
 * instantiated from it to carry the union of both schemas plus unused DAOs.
 */
@Database(
    entities = [
        CachedCloudDocument::class,
    ],
    version = DOCUMENT_SYNC_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class DocumentSyncDatabase: RoomDatabase() {
    abstract fun cloudDocumentCacheDao(): CloudDocumentCacheDao
}
```

- [ ] **Step 2: Rename the getter + file in `DatabaseContainer.kt`**

Change the import at line 29 from `import net.bible.android.database.CacheDatabase` to `import net.bible.android.database.DocumentSyncDatabase`.

Replace lines 252-259 with:

```kotlin
    val documentSyncDb: DocumentSyncDatabase =
        Room.databaseBuilder(
            application, DocumentSyncDatabase::class.java, "document-sync.sqlite3"
        )
            .allowMainThreadQueries()
            .openHelperFactory(dbFactory)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
```

- [ ] **Step 3: Delete the orphaned old cache file on first run**

In `DatabaseContainer`'s `init` block (currently `init { backupDatabaseIfNeeded() }` near line 85), add a line so it reads:

```kotlin
    init {
        backupDatabaseIfNeeded()
        // The cloud-document cache DB was renamed to document-sync.sqlite3; drop the orphaned file.
        if (!application.isRunningTests) application.deleteDatabase("cloud-documents-cache.sqlite3")
    }
```

- [ ] **Step 4: Update the 7 references in `DocumentSync.kt`**

Replace every occurrence of `cloudDocumentsCacheDb` with `documentSyncDb` (lines 89, 109, 122, 131, 256, 315, 354). Each reads `DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()` → `DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()`.

- [ ] **Step 5: Update the doc comment in `CachedCloudDocument.kt`**

Replace lines 30-33's comment text "Lives in CacheDatabase — never backed up, never synced. Pure derived data." with "Lives in DocumentSyncDatabase — never backed up, never synced. Pure derived data." Also bump the copyright year to 2026 if not already.

- [ ] **Step 6: Compile + run existing document-sync tests**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.control.backup.*" --tests "net.bible.service.cloudsync.documents.*" --tests "net.bible.android.view.activity.cloud.*" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL; existing CloudDocument* / AssembleStatusItems / DocumentSync* tests pass (they reference the public API, unaffected by the rename).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/database/Databases.kt app/src/main/java/net/bible/service/db/DatabaseContainer.kt app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt app/src/main/java/net/bible/android/database/CachedCloudDocument.kt
git commit -m "Rename CacheDatabase to DocumentSyncDatabase"
```

---

### Task 2: Add the settings / watermark / timestamp entities + DAOs

Add the three new tables to `DocumentSyncDatabase`. No accessor rewrite yet (Task 3) — this task just lands the schema and proves get/set round-trips.

**Files:**
- Create: `app/src/main/java/net/bible/android/database/DocumentSyncEntities.kt`
- Modify: `app/src/main/java/net/bible/android/database/Databases.kt` (DocumentSyncDatabase `@Database` entities + DAO accessors)
- Test: `app/src/test/java/net/bible/android/database/DocumentSyncEntitiesTest.kt`

**Interfaces:**
- Produces:
  - `DocumentSyncPreferences(id: IdType = SINGLETON_ID, enabled, wifiOnly, autoDownload, autoUpload, autoDelete, syncNowDownload, syncNowUpload, syncNowDelete, showRemovedDocuments: Boolean, blockList: Set<String>)`
  - `CloudListingState(id: IdType = SINGLETON_ID, watermark: Long)`
  - `CloudDocumentSyncTimestamp(initials: String, timestamp: Long)`
  - DAOs: `DocumentSyncPreferencesDao{get():DocumentSyncPreferences?; set(p); clear()}`, `CloudListingStateDao{get():CloudListingState?; set(s); clear()}`, `CloudDocumentSyncTimestampDao{get(initials):Long?; set(row); clear()}`
  - `DocumentSyncDatabase.documentSyncPreferencesDao()`, `.cloudListingStateDao()`, `.cloudDocumentSyncTimestampDao()`

- [ ] **Step 1: Create the entities + DAOs file**

Create `app/src/main/java/net/bible/android/database/DocumentSyncEntities.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Singleton row of the user's document-sync preferences. Device-local (lives in
 * [DocumentSyncDatabase], never backed up or synced). Fixed primary key so there is exactly one row.
 */
@Entity
data class DocumentSyncPreferences(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val wifiOnly: Boolean = true,
    @ColumnInfo(defaultValue = "1") val autoDownload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val autoUpload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val autoDelete: Boolean = true,
    @ColumnInfo(defaultValue = "1") val syncNowDownload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val syncNowUpload: Boolean = true,
    @ColumnInfo(defaultValue = "1") val syncNowDelete: Boolean = true,
    @ColumnInfo(defaultValue = "0") val showRemovedDocuments: Boolean = false,
    val blockList: Set<String> = emptySet(),
) {
    companion object {
        val SINGLETON_ID = IdType.fromString("d0c00000-0000-0000-0000-000000000001")
    }
}

/** Singleton row holding the incremental-listing watermark (max observed cloud meta createdTime). */
@Entity
data class CloudListingState(
    @PrimaryKey val id: IdType = SINGLETON_ID,
    @ColumnInfo(defaultValue = "0") val watermark: Long = 0L,
) {
    companion object {
        val SINGLETON_ID = IdType.fromString("d0c00000-0000-0000-0000-000000000002")
    }
}

/** This device's last-sync timestamp for one document, keyed by initials. */
@Entity
data class CloudDocumentSyncTimestamp(
    @PrimaryKey val initials: String,
    val timestamp: Long,
)

@Dao
interface DocumentSyncPreferencesDao {
    @Query("SELECT * FROM DocumentSyncPreferences LIMIT 1")
    fun get(): DocumentSyncPreferences?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(prefs: DocumentSyncPreferences)

    @Query("DELETE FROM DocumentSyncPreferences")
    fun clear()
}

@Dao
interface CloudListingStateDao {
    @Query("SELECT * FROM CloudListingState LIMIT 1")
    fun get(): CloudListingState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(state: CloudListingState)

    @Query("DELETE FROM CloudListingState")
    fun clear()
}

@Dao
interface CloudDocumentSyncTimestampDao {
    @Query("SELECT timestamp FROM CloudDocumentSyncTimestamp WHERE initials = :initials")
    fun get(initials: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(row: CloudDocumentSyncTimestamp)

    @Query("DELETE FROM CloudDocumentSyncTimestamp")
    fun clear()
}
```

- [ ] **Step 2: Register the entities + DAO accessors in `DocumentSyncDatabase`**

In `app/src/main/java/net/bible/android/database/Databases.kt`, change the `DocumentSyncDatabase` `@Database` entities list and abstract functions to:

```kotlin
@Database(
    entities = [
        CachedCloudDocument::class,
        DocumentSyncPreferences::class,
        CloudListingState::class,
        CloudDocumentSyncTimestamp::class,
    ],
    version = DOCUMENT_SYNC_DATABASE_VERSION
)
@TypeConverters(Converters::class)
abstract class DocumentSyncDatabase: RoomDatabase() {
    abstract fun cloudDocumentCacheDao(): CloudDocumentCacheDao
    abstract fun documentSyncPreferencesDao(): DocumentSyncPreferencesDao
    abstract fun cloudListingStateDao(): CloudListingStateDao
    abstract fun cloudDocumentSyncTimestampDao(): CloudDocumentSyncTimestampDao
}
```

- [ ] **Step 3: Write the round-trip test**

Create `app/src/test/java/net/bible/android/database/DocumentSyncEntitiesTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.android.database

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncEntitiesTest {
    private val db get() = DatabaseContainer.instance.documentSyncDb

    @Test
    fun preferencesDefaultWhenEmptyAndRoundTrip() {
        db.documentSyncPreferencesDao().clear()
        assertNull("no row before any write", db.documentSyncPreferencesDao().get())

        db.documentSyncPreferencesDao().set(
            DocumentSyncPreferences(enabled = true, wifiOnly = false, blockList = setOf("KJV", "ESV"))
        )
        val read = db.documentSyncPreferencesDao().get()!!
        assertTrue(read.enabled)
        assertEquals(false, read.wifiOnly)
        assertEquals(setOf("KJV", "ESV"), read.blockList)
        // set() REPLACEs the single fixed-PK row (no second row accumulates).
        db.documentSyncPreferencesDao().set(read.copy(enabled = false))
        assertEquals(false, db.documentSyncPreferencesDao().get()!!.enabled)
    }

    @Test
    fun listingStateRoundTrip() {
        db.cloudListingStateDao().clear()
        assertNull(db.cloudListingStateDao().get())
        db.cloudListingStateDao().set(CloudListingState(watermark = 1740000000000L))
        assertEquals(1740000000000L, db.cloudListingStateDao().get()!!.watermark)
    }

    @Test
    fun timestampPerInitialsRoundTrip() {
        db.cloudDocumentSyncTimestampDao().clear()
        assertNull(db.cloudDocumentSyncTimestampDao().get("KJV"))
        db.cloudDocumentSyncTimestampDao().set(CloudDocumentSyncTimestamp("KJV", 42L))
        db.cloudDocumentSyncTimestampDao().set(CloudDocumentSyncTimestamp("ESV", 7L))
        assertEquals(42L, db.cloudDocumentSyncTimestampDao().get("KJV"))
        assertEquals(7L, db.cloudDocumentSyncTimestampDao().get("ESV"))
        db.cloudDocumentSyncTimestampDao().clear()
        assertNull(db.cloudDocumentSyncTimestampDao().get("KJV"))
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.android.database.DocumentSyncEntitiesTest" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/database/DocumentSyncEntitiesTest.kt app/src/main/java/net/bible/android/database/DocumentSyncEntities.kt app/src/main/java/net/bible/android/database/Databases.kt app/src/test/java/net/bible/android/database/DocumentSyncEntitiesTest.kt
git commit -m "Add document-sync settings, watermark and timestamp tables"
```

---

### Task 3: Re-back `DocumentSyncSettings` onto the new entities

Rewrite the `DocumentSyncSettings` accessor object to read/write the new singletons + timestamp table instead of `CommonUtils.settings`. Public API is preserved (callers unchanged) and a `watermark` property is added.

**Files:**
- Modify (rewrite body): `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSettingsTest.kt`

**Interfaces:**
- Consumes: the DAOs from Task 2; `DocumentBlockList` + `StringSetStore` (existing, in `DocumentBlockList.kt`).
- Produces (unchanged public API): `DocumentSyncSettings.{enabled, wifiOnly, autoDownload, autoUpload, autoDelete, syncNowDownload, syncNowUpload, syncNowDelete, showRemovedDocuments}: Boolean`; `blockList: DocumentBlockList`; `syncTimestamp(initials): Long?`; `setSyncTimestamp(initials, ts)`; `isAutoTransferAllowed: Boolean`. New: `var watermark: Long`.

- [ ] **Step 1: Rewrite `DocumentSyncSettings.kt`**

Replace the body of `object DocumentSyncSettings` (keep the license header + package) with:

```kotlin
package net.bible.service.cloudsync.documents

import net.bible.android.database.CloudDocumentSyncTimestamp
import net.bible.android.database.CloudListingState
import net.bible.android.database.DocumentSyncPreferences
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer

object DocumentSyncSettings {
    private val prefsDao get() = DatabaseContainer.instance.documentSyncDb.documentSyncPreferencesDao()
    private val listingDao get() = DatabaseContainer.instance.documentSyncDb.cloudListingStateDao()
    private val tsDao get() = DatabaseContainer.instance.documentSyncDb.cloudDocumentSyncTimestampDao()

    private fun prefs(): DocumentSyncPreferences = prefsDao.get() ?: DocumentSyncPreferences()
    private fun update(transform: DocumentSyncPreferences.() -> DocumentSyncPreferences) =
        prefsDao.set(prefs().transform())

    var enabled: Boolean
        get() = prefs().enabled
        set(value) = update { copy(enabled = value) }

    var wifiOnly: Boolean
        get() = prefs().wifiOnly
        set(value) = update { copy(wifiOnly = value) }

    var showRemovedDocuments: Boolean
        get() = prefs().showRemovedDocuments
        set(value) = update { copy(showRemovedDocuments = value) }

    var autoDownload: Boolean
        get() = prefs().autoDownload
        set(value) = update { copy(autoDownload = value) }

    var autoUpload: Boolean
        get() = prefs().autoUpload
        set(value) = update { copy(autoUpload = value) }

    var autoDelete: Boolean
        get() = prefs().autoDelete
        set(value) = update { copy(autoDelete = value) }

    var syncNowDownload: Boolean
        get() = prefs().syncNowDownload
        set(value) = update { copy(syncNowDownload = value) }

    var syncNowUpload: Boolean
        get() = prefs().syncNowUpload
        set(value) = update { copy(syncNowUpload = value) }

    var syncNowDelete: Boolean
        get() = prefs().syncNowDelete
        set(value) = update { copy(syncNowDelete = value) }

    val blockList: DocumentBlockList = DocumentBlockList(object : StringSetStore {
        override fun get(): Set<String> = prefs().blockList
        override fun set(value: Set<String>) = update { copy(blockList = value) }
    })

    /** Incremental-listing watermark: max observed cloud meta createdTime. 0 ⇒ cold start. */
    var watermark: Long
        get() = (listingDao.get() ?: CloudListingState()).watermark
        set(value) = listingDao.set(CloudListingState(watermark = value))

    fun syncTimestamp(initials: String): Long? = tsDao.get(initials)

    fun setSyncTimestamp(initials: String, ts: Long) =
        tsDao.set(CloudDocumentSyncTimestamp(initials, ts))

    val isAutoTransferAllowed: Boolean
        get() = !wifiOnly || !CommonUtils.isMeteredNetwork
}
```

- [ ] **Step 2: Write the test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSettingsTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.cloudsync.documents

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncSettingsTest {
    @Before
    fun reset() {
        DatabaseContainer.instance.documentSyncDb.apply {
            documentSyncPreferencesDao().clear()
            cloudListingStateDao().clear()
            cloudDocumentSyncTimestampDao().clear()
        }
    }

    @Test
    fun defaults() {
        assertFalse(DocumentSyncSettings.enabled)
        assertTrue(DocumentSyncSettings.wifiOnly)
        assertTrue(DocumentSyncSettings.autoDownload)
        assertFalse(DocumentSyncSettings.showRemovedDocuments)
        assertEquals(0L, DocumentSyncSettings.watermark)
        assertTrue(DocumentSyncSettings.blockList.all().isEmpty())
        assertNull(DocumentSyncSettings.syncTimestamp("KJV"))
    }

    @Test
    fun booleansRoundTripIndependently() {
        DocumentSyncSettings.enabled = true
        DocumentSyncSettings.autoUpload = false
        assertTrue(DocumentSyncSettings.enabled)
        assertFalse(DocumentSyncSettings.autoUpload)
        // Other prefs keep their defaults (independent columns of one row).
        assertTrue(DocumentSyncSettings.wifiOnly)
    }

    @Test
    fun watermarkAndTimestampsAndBlockListRoundTrip() {
        DocumentSyncSettings.watermark = 12345L
        assertEquals(12345L, DocumentSyncSettings.watermark)

        DocumentSyncSettings.setSyncTimestamp("KJV", 99L)
        assertEquals(99L, DocumentSyncSettings.syncTimestamp("KJV"))

        DocumentSyncSettings.blockList.block("ESV")
        assertTrue(DocumentSyncSettings.blockList.all().contains("ESV"))
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentSyncSettingsTest" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: PASS (3 tests).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSettingsTest.kt
git commit -m "Back DocumentSyncSettings onto DocumentSyncDatabase singletons"
```

---

### Task 4: Wipe the whole `DocumentSyncDatabase` on sign-out

`onSignOut()` must clear all four tables (settings + listing state + timestamps + cache), so a later sign-in (possibly a different account) starts from a clean slate.

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt:113-123` (`onSignOut`)
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSignOutTest.kt`

**Interfaces:**
- Consumes: the four DAOs (`cloudDocumentCacheDao`, `documentSyncPreferencesDao`, `cloudListingStateDao`, `cloudDocumentSyncTimestampDao`) each with `clear()`.

- [ ] **Step 1: Rewrite `onSignOut()`**

Replace `DocumentSync.onSignOut()` (currently lines 113-123, including its doc comment) with:

```kotlin
    /**
     * Tears document sync down on sign-out by wiping the entire [net.bible.android.database.DocumentSyncDatabase]:
     * settings, the cloud-listing cache, the listing watermark, and per-document sync timestamps.
     * All of it is device-local state tied to the account just disconnected — a later sign-in may
     * target a different cloud account and must start clean (sync off, empty block list, cold-start
     * listing). Mirrors the DB-sync sign-out, which clears its own per-database sync status.
     */
    suspend fun onSignOut() {
        DatabaseContainer.instance.documentSyncDb.apply {
            cloudDocumentCacheDao().clear()
            documentSyncPreferencesDao().clear()
            cloudListingStateDao().clear()
            cloudDocumentSyncTimestampDao().clear()
        }
    }
```

- [ ] **Step 2: Write the test**

Create `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSignOutTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.cloudsync.documents

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.CachedCloudDocument
import net.bible.service.db.DatabaseContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncSignOutTest {
    @Test
    fun signOutWipesEverything() = runBlocking {
        DocumentSyncSettings.enabled = true
        DocumentSyncSettings.watermark = 5000L
        DocumentSyncSettings.setSyncTimestamp("KJV", 1L)
        DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().replaceAll(
            listOf(CachedCloudDocument("KJV", "KJV", "SWORD", "1.0", 10, "en", "BIBLE", "dev", 1L, null, false))
        )

        DocumentSync.onSignOut()

        assertFalse(DocumentSyncSettings.enabled)
        assertEquals(0L, DocumentSyncSettings.watermark)
        assertNull(DocumentSyncSettings.syncTimestamp("KJV"))
        assertTrue(DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().all().isEmpty())
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentSyncSignOutTest" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncSignOutTest.kt
git commit -m "Wipe entire DocumentSyncDatabase on cloud sign-out"
```

---

### Task 5: Pure `mergeCloudListing` + tests (TDD)

The heart of the incremental refresh: given the old cache, the changed metas, the current folder set, and createdTime info, produce the new cache and the advanced watermark — with no Android/network dependencies.

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentListingMerge.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentListingMergeTest.kt`

**Interfaces:**
- Consumes: `DocumentSyncMeta` (existing).
- Produces:
  - `data class MergeResult(val cache: List<DocumentSyncMeta>, val watermark: Long)`
  - `fun mergeCloudListing(oldCache: List<DocumentSyncMeta>, changed: List<DocumentSyncMeta>, currentInitials: Set<String>, oldWatermark: Long, matchedCreatedTimes: List<Long>, failedCreatedTimes: List<Long>): MergeResult`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/net/bible/service/cloudsync/documents/DocumentListingMergeTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentListingMergeTest {
    private fun meta(initials: String, version: String = "1.0", deleted: Boolean = false) =
        DocumentSyncMeta(
            initials = initials, name = initials, documentType = DocumentType.SWORD,
            version = version, size = 1, language = "en", sourceDevice = "dev",
            timestamp = 1L, deleted = deleted,
        )

    @Test
    fun upsertsChangedAndKeepsUnchanged() {
        val old = listOf(meta("KJV", "1.0"), meta("ESV", "1.0"))
        val result = mergeCloudListing(
            oldCache = old,
            changed = listOf(meta("ESV", "2.0")),          // ESV changed; KJV unchanged
            currentInitials = setOf("KJV", "ESV"),
            oldWatermark = 100L,
            matchedCreatedTimes = listOf(200L),
            failedCreatedTimes = emptyList(),
        )
        val byInitials = result.cache.associateBy { it.initials }
        assertEquals("1.0", byInitials["KJV"]!!.version)   // kept
        assertEquals("2.0", byInitials["ESV"]!!.version)   // upserted
        assertEquals(2, result.cache.size)
        assertEquals(200L, result.watermark)
    }

    @Test
    fun addsNewDocument() {
        val result = mergeCloudListing(
            oldCache = listOf(meta("KJV")),
            changed = listOf(meta("NIV")),
            currentInitials = setOf("KJV", "NIV"),
            oldWatermark = 0L,
            matchedCreatedTimes = listOf(50L),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(setOf("KJV", "NIV"), result.cache.map { it.initials }.toSet())
    }

    @Test
    fun purgesVanishedFolder() {
        val result = mergeCloudListing(
            oldCache = listOf(meta("KJV"), meta("ESV")),
            changed = emptyList(),
            currentInitials = setOf("KJV"),                // ESV folder gone
            oldWatermark = 100L,
            matchedCreatedTimes = emptyList(),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(listOf("KJV"), result.cache.map { it.initials })
        assertEquals(100L, result.watermark)               // nothing matched ⇒ unchanged
    }

    @Test
    fun coldStartIngestsAll() {
        val result = mergeCloudListing(
            oldCache = emptyList(),
            changed = listOf(meta("KJV"), meta("ESV")),
            currentInitials = setOf("KJV", "ESV"),
            oldWatermark = 0L,
            matchedCreatedTimes = listOf(10L, 30L),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(setOf("KJV", "ESV"), result.cache.map { it.initials }.toSet())
        assertEquals(30L, result.watermark)
    }

    @Test
    fun tombstoneInChangedIsKept() {
        val result = mergeCloudListing(
            oldCache = listOf(meta("KJV", "1.0")),
            changed = listOf(meta("KJV", "1.0", deleted = true)),
            currentInitials = setOf("KJV"),                 // tombstone keeps the folder
            oldWatermark = 0L,
            matchedCreatedTimes = listOf(70L),
            failedCreatedTimes = emptyList(),
        )
        assertEquals(true, result.cache.single().deleted)
        assertEquals(70L, result.watermark)
    }

    @Test
    fun watermarkStopsBelowEarliestFailure() {
        val result = mergeCloudListing(
            oldCache = emptyList(),
            changed = listOf(meta("KJV")),                  // KJV parsed ok (createdTime 100)
            currentInitials = setOf("KJV", "ESV"),
            oldWatermark = 50L,
            matchedCreatedTimes = listOf(100L, 300L),       // ESV (300) failed
            failedCreatedTimes = listOf(300L),
        )
        assertEquals(listOf("KJV"), result.cache.map { it.initials })
        assertEquals(299L, result.watermark)               // min(failed) - 1
    }

    @Test
    fun watermarkNeverGoesBackwardOnFailure() {
        val result = mergeCloudListing(
            oldCache = emptyList(),
            changed = emptyList(),
            currentInitials = setOf("KJV"),
            oldWatermark = 1000L,
            matchedCreatedTimes = listOf(400L),             // a failure inside the margin window
            failedCreatedTimes = listOf(400L),
        )
        assertEquals(1000L, result.watermark)               // max(oldWatermark, 399) = 1000
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentListingMergeTest" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: FAIL — `mergeCloudListing` / `MergeResult` unresolved.

- [ ] **Step 3: Implement `mergeCloudListing`**

Create `app/src/main/java/net/bible/service/cloudsync/documents/DocumentListingMerge.kt`:

```kotlin
/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.cloudsync.documents

/** Outcome of merging an incremental cloud listing into the cache. */
data class MergeResult(val cache: List<DocumentSyncMeta>, val watermark: Long)

/**
 * Merges an incremental cloud listing into the existing cache (pure; no Android/network).
 *
 * - Drops cache entries whose folder is no longer present ([currentInitials] = all folders now).
 * - Upserts every successfully-read [changed] meta (keyed by initials), keeping unchanged entries.
 * - Advances the watermark to the max matched createdTime, but **never past the earliest failure**:
 *   when something failed, the watermark stops at `min(failedCreatedTimes) - 1` so the failed meta
 *   (and anything newer) is re-fetched next cycle, while not regressing below [oldWatermark].
 */
fun mergeCloudListing(
    oldCache: List<DocumentSyncMeta>,
    changed: List<DocumentSyncMeta>,
    currentInitials: Set<String>,
    oldWatermark: Long,
    matchedCreatedTimes: List<Long>,
    failedCreatedTimes: List<Long>,
): MergeResult {
    val byInitials = oldCache.associateBy { it.initials }.toMutableMap()
    byInitials.keys.retainAll(currentInitials)          // purge folders that vanished
    for (m in changed) byInitials[m.initials] = m       // upsert changed/new

    val minFailed = failedCreatedTimes.minOrNull()
    val advanced = if (minFailed != null) {
        minFailed - 1                                   // stop just below the earliest failure
    } else {
        matchedCreatedTimes.maxOrNull() ?: oldWatermark
    }
    val newWatermark = maxOf(oldWatermark, advanced)    // never regress

    return MergeResult(byInitials.values.toList(), newWatermark)
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.DocumentListingMergeTest" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentListingMerge.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentListingMergeTest.kt
git commit -m "Add pure mergeCloudListing for incremental cache refresh"
```

---

### Task 6: `DocumentStore.listChangedDocuments` + single-document `readMeta`

Add the batched incremental listing to `DocumentStore`, and a single-folder `readMeta(initials)` so per-document operations stop doing full listings. (Network code — verified by compile + the wiring in Task 7; manual on-device testing covers the live adapter behaviour, as for DB sync.)

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentStore.kt`

**Interfaces:**
- Consumes: `CloudAdapter.listFiles(parentsIds, name, createdTimeAtLeast)` returning `List<CloudFile>` (each `CloudFile.createdTime: Long`, `.parentId`, `.id`); `CloudAdapter.getFolders(rootFolderId)`; `DocumentSyncMeta.fromJson`; `DOCUMENT_META_FILENAME`.
- Produces:
  - `data class ChangedListing(changedMetas: List<DocumentSyncMeta>, currentInitials: Set<String>, matchedCreatedTimes: List<Long>, failedCreatedTimes: List<Long>)`
  - `suspend fun listChangedDocuments(watermark: Long): ChangedListing`
  - `suspend fun readMeta(initials: String): DocumentSyncMeta?`
  - `listDocuments()` now delegates to `listChangedDocuments(0).changedMetas`.

- [ ] **Step 1: Add the const, `ChangedListing`, `downloadMeta`, `listChangedDocuments`, and `readMeta(initials)`**

In `DocumentStore.kt`: add near the existing `LIST_CONCURRENCY` const:

```kotlin
/**
 * Trailing overlap (ms) re-queried below the watermark each incremental listing — absorbs
 * NextCloud's one-second createdTime resolution and minor clock skew. Re-reading a meta already in
 * the cache is idempotent; this is a safety overlap, not a skew guarantee (the reset action is).
 */
private const val LISTING_MARGIN_MS = 5_000L
```

Add this data class above the `DocumentStore` class (top-level, after the consts):

```kotlin
/** Result of an incremental cloud listing — only the metas whose meta.json changed since a watermark. */
data class ChangedListing(
    val changedMetas: List<DocumentSyncMeta>,
    val currentInitials: Set<String>,
    val matchedCreatedTimes: List<Long>,
    val failedCreatedTimes: List<Long>,
)
```

Inside `DocumentStore`, **replace the existing private `readMeta(folderId)` (lines 48-58) entirely** with a `downloadMeta` helper plus a public single-document `readMeta(initials)`. (The old `readMeta(folderId)` had only one caller, `listDocuments()`, which now delegates to `listChangedDocuments` — so it is dead. Note: do NOT keep both a `readMeta(folderId)` and a `readMeta(initials)` — both take `String`, so they would be a duplicate-signature compile error.)

```kotlin
    private suspend fun downloadMeta(metaFile: CloudFile): DocumentSyncMeta? {
        val tmp = CommonUtils.tmpFile
        return try {
            tmp.outputStream().use { adapter.download(metaFile.id, it) }
            DocumentSyncMeta.fromJson(tmp.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading meta ${metaFile.id}", e); null
        } finally { tmp.delete() }
    }

    /** Reads the current meta.json for one document by initials (single folder), or null if absent. */
    suspend fun readMeta(initials: String): DocumentSyncMeta? {
        val folderId = folderFor(initials)?.id ?: return null
        val metaFile = adapter.listFiles(parentsIds = listOf(folderId), name = DOCUMENT_META_FILENAME)
            .firstOrNull() ?: return null
        return downloadMeta(metaFile)
    }
```

Add the batched incremental listing:

```kotlin
    /**
     * Lists only the documents whose meta.json changed since [watermark] (server-side
     * createdTimeAtLeast filter, minus a small overlap margin), in one batched call across all
     * folders. Also returns the current folder set (for new-document discovery + purge detection)
     * and the matched/failed createdTimes so the merge can advance the watermark safely.
     * Pass watermark = 0 for a full (cold-start) listing.
     */
    suspend fun listChangedDocuments(watermark: Long): ChangedListing {
        val folders = adapter.getFolders(rootFolderId)
        val since = maxOf(0L, watermark - LISTING_MARGIN_MS)
        val metaFiles = adapter.listFiles(
            parentsIds = folders.map { it.id },
            name = DOCUMENT_META_FILENAME,
            createdTimeAtLeast = since,
        )
        val changed = mutableListOf<DocumentSyncMeta>()
        val matched = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        for (f in metaFiles) {
            matched.add(f.createdTime)
            val meta = downloadMeta(f)
            if (meta != null) changed.add(meta) else failed.add(f.createdTime)
        }
        return ChangedListing(changed, folders.map { it.name }.toSet(), matched, failed)
    }
```

- [ ] **Step 2: Make `listDocuments()` delegate to the full listing**

Replace the existing `listDocuments()` (lines 60-64) with:

```kotlin
    /** Full listing of all live + tombstoned cloud metas (cold-start path: watermark 0). */
    suspend fun listDocuments(): List<DocumentSyncMeta> = listChangedDocuments(0).changedMetas
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentStore.kt
git commit -m "Add batched listChangedDocuments and single-document readMeta to DocumentStore"
```

---

### Task 7: Wire `refreshCache()` incremental + route `scan`/`runSync` + per-document `readMeta`

Make `refreshCache()` the single network-listing operation (incremental merge + watermark), route `scan()`/`runSync()` through it, switch the per-document callers to `store.readMeta(initials)`, and add `resetListingCache()` for the reset action (Task 8).

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt` (`refreshCache`, `scan`, `runSync`, `pushDocument`, `downloadAndInstall`, `removeFromCloud`; add `resetListingCache`)

**Interfaces:**
- Consumes: `store.listChangedDocuments(watermark)`, `store.readMeta(initials)`, `mergeCloudListing(...)`, `MergeResult`, `DocumentSyncSettings.watermark`, `DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()` with `all()` / `replaceAll(...)` / `clear()`, `CloudListingState` dao `clear()` (via `cloudListingStateDao()`).
- Produces: `suspend fun resetListingCache()` (clears cache + watermark so the next scan cold-starts).

- [ ] **Step 1: Rewrite `refreshCache()`**

Replace `refreshCache()` (currently lines 102-111) with:

```kotlin
    /**
     * Incrementally refreshes the cloud-listing cache: fetches only metas changed since the stored
     * watermark, merges them into the cache (upsert + purge), and advances the watermark. With
     * nothing changed this is two round-trips and no downloads. Watermark 0 (cold start) lists all.
     */
    suspend fun refreshCache() {
        val store = store() ?: return
        val cacheDao = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao()
        val watermark = DocumentSyncSettings.watermark
        val listing = store.listChangedDocuments(watermark)
        val merged = mergeCloudListing(
            oldCache = cacheDao.all().map { it.toMeta() },
            changed = listing.changedMetas,
            currentInitials = listing.currentInitials,
            oldWatermark = watermark,
            matchedCreatedTimes = listing.matchedCreatedTimes,
            failedCreatedTimes = listing.failedCreatedTimes,
        )
        cacheDao.replaceAll(merged.cache.map { it.toCacheEntity() })
        DocumentSyncSettings.watermark = merged.watermark
    }

    /**
     * Clears the listing cache and watermark so the next [scan]/[refreshCache] cold-starts a full
     * authoritative listing. User preferences (block list, toggles) are intentionally kept — this is
     * "re-scan the cloud", not a sign-out. Recovery path for a rare clock-skew silent miss.
     */
    suspend fun resetListingCache() {
        DatabaseContainer.instance.documentSyncDb.apply {
            cloudDocumentCacheDao().clear()
            cloudListingStateDao().clear()
        }
    }
```

- [ ] **Step 2: Rewrite `scan()` to route through `refreshCache()`**

Replace `scan()` (currently lines 88-100) with:

```kotlin
    suspend fun scan(includeDeleted: Boolean = false): List<DocumentStatusItem> {
        if (store() != null) refreshCache()   // signed in: incremental network refresh into the cache
        return scanCached(includeDeleted)     // offline / after refresh: build from the cache
    }
```

- [ ] **Step 3: Route `runSync()` through `refreshCache()`**

In `runSync()`, replace these lines (currently 252-257):

```kotlin
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        // Keep the cache fresh on every run (not only when something transfers), so with automatic
        // sync on the management view can trust the cache without hitting the network.
        DatabaseContainer.instance.cloudDocumentsCacheDb.cloudDocumentCacheDao()
            .replaceAll(cloudMetas.map { it.toCacheEntity() })
```

with:

```kotlin
        store() ?: return
        // Incrementally refresh the cache (fast no-op when nothing changed), then resolve actions
        // from the freshly-merged full cloud picture held in the cache.
        refreshCache()
        val cloudMetas = DatabaseContainer.instance.documentSyncDb.cloudDocumentCacheDao().all().map { it.toMeta() }
```

(`toMeta` is already imported at `DocumentSync.kt:26` — `import net.bible.android.database.toMeta`.)

- [ ] **Step 4: Switch per-document callers to `store.readMeta(initials)`**

In `pushDocument` replace line 165:

```kotlin
        val existing = store.listDocuments().firstOrNull { it.initials == book.initials && !it.deleted }
```
with:
```kotlin
        val existing = store.readMeta(book.initials)?.takeIf { !it.deleted }
```

In `downloadAndInstall` replace line 200:

```kotlin
        val meta = store.listDocuments().firstOrNull { it.initials == initials && !it.deleted } ?: return
```
with:
```kotlin
        val meta = store.readMeta(initials)?.takeIf { !it.deleted } ?: return
```

In `removeFromCloud` replace line 317:

```kotlin
        val existing = store.listDocuments().firstOrNull { it.initials == initials }
```
with:
```kotlin
        val existing = store.readMeta(initials)
```

- [ ] **Step 5: Compile + run the document-sync + cloud test suites**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.*" --tests "net.bible.android.view.activity.cloud.*" --tests "net.bible.android.database.DocumentSyncEntitiesTest" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL; all pass (resolver/ops/summary/merge/settings tests unaffected).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt
git commit -m "Make refreshCache incremental and route scan/runSync through it"
```

---

### Task 8: "Re-scan / reset" overflow menu action

Add a 3-dot menu action to `CloudDocumentsActivity` that clears the listing cache + watermark and re-scans from scratch.

**Files:**
- Modify: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt:218-260,594-595`
- Modify: `app/src/main/res/values/strings.xml` (near `:1929`)

**Interfaces:**
- Consumes: `DocumentSync.resetListingCache()` (Task 7); the existing `runSyncAction { ... }` helper (`:582`) which runs a suspend block then re-scans via `DocumentSync.scan(...)`; `CloudSync.signedIn`; `adapter.isSelectionMode()`.

- [ ] **Step 1: Add the string**

In `app/src/main/res/values/strings.xml`, after the `cloud_doc_sync_now` entry (line 1929), add:

```xml
    <string name="cloud_doc_rescan">Re-scan from cloud</string>
```

- [ ] **Step 2: Add the menu id constant**

In `CloudDocumentsActivity.kt` companion object, after `private const val MENU_SHOW_REMOVED = 3` (line 595), add:

```kotlin
        private const val MENU_RESCAN = 4
```

- [ ] **Step 3: Add the menu item in `onCreateOptionsMenu`**

After the `MENU_SHOW_REMOVED` item block in `onCreateOptionsMenu` (line 222-225), add:

```kotlin
        menu.add(Menu.NONE, MENU_RESCAN, Menu.NONE, R.string.cloud_doc_rescan)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
```

- [ ] **Step 4: Add visibility in `onPrepareOptionsMenu`**

After the `MENU_SHOW_REMOVED` block in `onPrepareOptionsMenu` (line 231-234), add:

```kotlin
        menu.findItem(MENU_RESCAN)?.isVisible = CloudSync.signedIn && !adapter.isSelectionMode()
```

- [ ] **Step 5: Handle the click in `onOptionsItemSelected`**

In the `when (item.itemId)` of `onOptionsItemSelected`, add a branch before the `android.R.id.home` branch:

```kotlin
        MENU_RESCAN -> {
            runSyncAction { DocumentSync.resetListingCache() }   // clears cache+watermark, then re-scans
            true
        }
```

- [ ] **Step 6: Compile**

Run: `./gradlew compileStandardGoogleplayDebugKotlin -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt app/src/main/res/values/strings.xml
git commit -m "Add re-scan/reset action to synced-documents menu"
```

---

## Final verification

- [ ] Run the full document-sync-related suites together:

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "net.bible.service.cloudsync.documents.*" --tests "net.bible.android.view.activity.cloud.*" --tests "net.bible.android.database.DocumentSyncEntitiesTest" --tests "net.bible.android.control.backup.*" -x npmInstall -x npmUpgrade -x jsBuild` (`dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL, all pass.

- [ ] Manual / on-device (not unit-testable — live cloud adapter): with tens of synced documents on Google Drive and on NextCloud, a no-op "Sync now" / auto-cycle issues only `getFolders` + one empty `listFiles` (no meta downloads); a changed/added/removed document on another device is picked up next sync; "Re-scan from cloud" repopulates after a manual cache wipe; sign-out wipes settings + cache (re-enable required after sign-in).
