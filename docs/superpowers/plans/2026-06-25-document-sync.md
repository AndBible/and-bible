# Document Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize installed Bible documents (all types, all install methods) across a user's devices via the existing `CloudAdapter` backend, by uploading whole documents (packaged as `.abmd.zip`) to a dedicated cloud folder and re-installing them on other devices.

**Architecture:** A new document-sync subsystem (`service/cloudsync/documents/`) that reuses the authenticated `CloudAdapter` from `CloudSync`. The cloud folder listing is the manifest (no synced database). Pure decision logic (version/tombstone/block resolution) is extracted into a unit-tested function; packaging reuses `BackupControl`'s per-type zipping; install reuses the existing `InstallZip`/`addManuallyInstalled*` paths. A management Activity serves both ongoing management and onboarding (setup mode).

**Tech Stack:** Kotlin, Android, kotlinx.serialization (already a dependency), JSword (`org.crosswire.common.util.Version`, `Books.installed()`), JUnit4 + Robolectric (existing test setup), View Binding, RecyclerView.

## Global Constraints

- **Language:** All repo artifacts (code, comments, commit messages, strings) in **English**. Chat may be Finnish.
- **Copyright header** on every new file (current year **2026**), per `ai-local/CLAUDE.md`:
  ```
  /*
   * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
   * ... (full GPL header as in existing new files; do NOT include "Martin Denham")
   */
  ```
- **Imports:** Java/Kotlin classes imported and referenced by simple name — never fully-qualified in code.
- **No hardcoded user-facing strings:** Android strings go to `app/src/main/res/values/strings.xml` (English only; translations handled separately).
- **Theme/e-ink:** UI status must not rely on color alone (icon + text); must work in dark, light, monochrome/e-ink; respect the "no animations" setting.
- **Gradle in sandbox:** every `./gradlew` command requires `dangerouslyDisableSandbox: true`.
- **Tests:** Kotlin-only change → run `./gradlew testStandardGoogleplayDebugUnitTest`. Do NOT run Vue tests.
- **Commits:** one logical change per commit; end commit message body with `Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz`. Do not push.
- **Branch:** work on the current `document-sync` branch. Never use worktree isolation (worktrees branch from the wrong base).

## File Structure

New files (all under `app/src/main/java/net/bible/service/cloudsync/documents/` unless noted):

- `DocumentSyncMeta.kt` — `@Serializable` per-document manifest model + JSON helpers.
- `DocumentSyncResolver.kt` — pure decision logic: `CloudDocument`, `LocalDocument`, `DocumentSyncActionType`, `DocumentSyncAction`, `resolveDocumentSyncActions(...)`.
- `DocumentBlockList.kt` — per-device block set (SharedPreferences-backed, injectable store).
- `DocumentSyncSettings.kt` — typed accessors for the document-sync preference keys + a metered-network check.
- `DocumentArchiver.kt` — package an installed `Book` into `.abmd.zip`; install an `.abmd.zip` headlessly.
- `DocumentStore.kt` — cloud blob/manifest operations against a `CloudAdapter` (list, read/write meta, upload, download, tombstone).
- `DocumentSync.kt` — orchestrator tying resolver + store + archiver together (`scan`, `pullDocuments`, `uploadDocument`, `removeFromCloud`).
- UI: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt` + `CloudDocumentsAdapter.kt`; layouts `activity_cloud_documents.xml`, `item_cloud_document.xml`.

Modified files:

- `BackupControl.kt` — extract reusable per-document zip function.
- `CloudSync.kt` — expose authenticated adapter + document sync folder helper; call `DocumentSync.pullDocuments()` in `synchronize()`.
- `BookInstallWatcher.kt` — trigger upload on `bookAdded`; handle uninstall on `bookRemoved`.
- `SyncSettings.kt`, `sync_settings.xml`, `strings.xml` — settings UI + strings.
- `AndroidManifest.xml` — register `CloudDocumentsActivity`.
- Document chooser menu (located in Task 15) — add a 3-dot entry.

---

## Phase 1 — Core data + decision logic (TDD, unit-tested)

### Task 1: DocumentSyncMeta model + JSON

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncMeta.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncMetaTest.kt`

**Interfaces:**
- Produces: `DocumentSyncMeta(initials, name, documentType: DocumentType, version, size, language, sourceDevice, timestamp, cipherKey: String?, deleted: Boolean)`; `enum class DocumentType { SWORD, MYBIBLE, MYSWORD, ESWORD, EPUB }`; `DocumentSyncMeta.toJson(): String`; `DocumentSyncMeta.fromJson(String): DocumentSyncMeta`; constant `DOCUMENT_META_FILENAME = "meta.json"`.

- [ ] **Step 1: Write the failing test**

```kotlin
// DocumentSyncMetaTest.kt (package net.bible.service.cloudsync.documents)
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DocumentSyncMetaTest {
    private val sample = DocumentSyncMeta(
        initials = "KJV",
        name = "King James Version",
        documentType = DocumentType.SWORD,
        version = "2.6",
        size = 16384000L,
        language = "en",
        sourceDevice = "device-1",
        timestamp = 1740000000000L,
        cipherKey = null,
        deleted = false,
    )

    @Test
    fun roundTripsThroughJson() {
        val restored = DocumentSyncMeta.fromJson(sample.toJson())
        assertEquals(sample, restored)
    }

    @Test
    fun unknownFieldsAreIgnoredForForwardCompatibility() {
        val json = """{"initials":"KJV","name":"King James Version","documentType":"SWORD",
            "version":"2.6","size":16384000,"language":"en","sourceDevice":"device-1",
            "timestamp":1740000000000,"cipherKey":null,"deleted":false,"futureField":"x"}"""
        val restored = DocumentSyncMeta.fromJson(json)
        assertEquals("KJV", restored.initials)
        assertFalse(restored.deleted)
    }

    @Test
    fun defaultsDeletedToFalseWhenMissing() {
        val json = """{"initials":"KJV","name":"n","documentType":"EPUB","version":"1.0",
            "size":1,"language":"en","sourceDevice":"d","timestamp":1}"""
        assertEquals(false, DocumentSyncMeta.fromJson(json).deleted)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncMetaTest*"` (with `dangerouslyDisableSandbox: true`)
Expected: FAIL — `DocumentSyncMeta` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// DocumentSyncMeta.kt — include the standard 2026 copyright header
package net.bible.service.cloudsync.documents

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DOCUMENT_META_FILENAME = "meta.json"

enum class DocumentType { SWORD, MYBIBLE, MYSWORD, ESWORD, EPUB }

@Serializable
data class DocumentSyncMeta(
    val initials: String,
    val name: String,
    val documentType: DocumentType,
    val version: String,
    val size: Long,
    val language: String,
    val sourceDevice: String,
    val timestamp: Long,
    val cipherKey: String? = null,
    val deleted: Boolean = false,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun fromJson(text: String): DocumentSyncMeta = json.decodeFromString(serializer(), text)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncMetaTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncMeta.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncMetaTest.kt
git commit -m "Add DocumentSyncMeta model for document sync manifest

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 2: Sync action resolver (decision table)

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncResolver.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncResolverTest.kt`

**Interfaces:**
- Consumes: `DocumentSyncMeta` (Task 1) is NOT used here directly; the resolver works on plain `CloudDocument`/`LocalDocument` to stay UI/cloud-agnostic.
- Produces:
  - `data class CloudDocument(initials, name, documentType: DocumentType, version, size: Long, timestamp: Long, deleted: Boolean)`
  - `data class LocalDocument(initials, version)`
  - `enum class DocumentSyncActionType { DOWNLOAD, UPGRADE, UNINSTALL, SKIP_BLOCKED, NONE }`
  - `data class DocumentSyncAction(val initials: String, val type: DocumentSyncActionType)`
  - `fun resolveDocumentSyncActions(cloudDocs: List<CloudDocument>, localDocs: Map<String, LocalDocument>, syncTimestamps: Map<String, Long>, blocked: Set<String>, isNewer: (cloudVersion: String, localVersion: String) -> Boolean): List<DocumentSyncAction>`

Decision table (one action per cloud document, in input order):

| Cloud doc | Condition | Action |
|---|---|---|
| `deleted == true` | local installed AND `syncTimestamps[initials] != null` AND `cloud.timestamp > syncTimestamps[initials]` | `UNINSTALL` |
| `deleted == true` | otherwise | `NONE` |
| present | `initials in blocked` | `SKIP_BLOCKED` |
| present | local not installed | `DOWNLOAD` |
| present | `isNewer(cloud.version, local.version)` | `UPGRADE` |
| present | otherwise (local same/newer) | `NONE` |

- [ ] **Step 1: Write the failing test**

```kotlin
// DocumentSyncResolverTest.kt (package net.bible.service.cloudsync.documents)
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentSyncResolverTest {
    // simple numeric-dotted comparator for tests: cloud newer if lexently-compared parts greater
    private val isNewer: (String, String) -> Boolean = { c, l ->
        fun parts(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
        val cp = parts(c); val lp = parts(l)
        val n = maxOf(cp.size, lp.size)
        var result = false
        for (i in 0 until n) {
            val a = cp.getOrElse(i) { 0 }; val b = lp.getOrElse(i) { 0 }
            if (a != b) { result = a > b; break }
        }
        result
    }

    private fun cloud(initials: String, version: String = "1.0", deleted: Boolean = false, ts: Long = 100) =
        CloudDocument(initials, initials, DocumentType.SWORD, version, 1, ts, deleted)
    private fun local(initials: String, version: String = "1.0") = initials to LocalDocument(initials, version)

    @Test fun downloadsWhenNotInstalled() {
        val actions = resolveDocumentSyncActions(listOf(cloud("KJV")), emptyMap(), emptyMap(), emptySet(), isNewer)
        assertEquals(listOf(DocumentSyncAction("KJV", DocumentSyncActionType.DOWNLOAD)), actions)
    }

    @Test fun upgradesWhenCloudNewer() {
        val actions = resolveDocumentSyncActions(listOf(cloud("KJV", "2.0")), mapOf(local("KJV", "1.0")), emptyMap(), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.UPGRADE, actions.single().type)
    }

    @Test fun noneWhenLocalSameOrNewer() {
        val same = resolveDocumentSyncActions(listOf(cloud("KJV", "2.0")), mapOf(local("KJV", "2.0")), emptyMap(), emptySet(), isNewer)
        val newer = resolveDocumentSyncActions(listOf(cloud("KJV", "1.0")), mapOf(local("KJV", "2.0")), emptyMap(), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, same.single().type)
        assertEquals(DocumentSyncActionType.NONE, newer.single().type)
    }

    @Test fun skipsBlocked() {
        val actions = resolveDocumentSyncActions(listOf(cloud("KJV")), emptyMap(), emptyMap(), setOf("KJV"), isNewer)
        assertEquals(DocumentSyncActionType.SKIP_BLOCKED, actions.single().type)
    }

    @Test fun tombstoneUninstallsWhenNewerThanLocalSync() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            mapOf(local("KJV")), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.UNINSTALL, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenNoSyncRecord() {
        // user installed locally, never synced this doc → do not auto-delete
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            mapOf(local("KJV")), emptyMap(), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenOlderThanLocalSync() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 50)),
            mapOf(local("KJV")), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }

    @Test fun tombstoneIgnoredWhenNotInstalled() {
        val actions = resolveDocumentSyncActions(
            listOf(cloud("KJV", deleted = true, ts = 200)),
            emptyMap(), mapOf("KJV" to 100L), emptySet(), isNewer)
        assertEquals(DocumentSyncActionType.NONE, actions.single().type)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncResolverTest*"`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Write minimal implementation**

```kotlin
// DocumentSyncResolver.kt — include the standard 2026 copyright header
package net.bible.service.cloudsync.documents

data class CloudDocument(
    val initials: String,
    val name: String,
    val documentType: DocumentType,
    val version: String,
    val size: Long,
    val timestamp: Long,
    val deleted: Boolean,
)

data class LocalDocument(
    val initials: String,
    val version: String,
)

enum class DocumentSyncActionType { DOWNLOAD, UPGRADE, UNINSTALL, SKIP_BLOCKED, NONE }

data class DocumentSyncAction(val initials: String, val type: DocumentSyncActionType)

fun resolveDocumentSyncActions(
    cloudDocs: List<CloudDocument>,
    localDocs: Map<String, LocalDocument>,
    syncTimestamps: Map<String, Long>,
    blocked: Set<String>,
    isNewer: (cloudVersion: String, localVersion: String) -> Boolean,
): List<DocumentSyncAction> = cloudDocs.map { cloud ->
    val local = localDocs[cloud.initials]
    val type = when {
        cloud.deleted -> {
            val syncedAt = syncTimestamps[cloud.initials]
            if (local != null && syncedAt != null && cloud.timestamp > syncedAt)
                DocumentSyncActionType.UNINSTALL
            else DocumentSyncActionType.NONE
        }
        cloud.initials in blocked -> DocumentSyncActionType.SKIP_BLOCKED
        local == null -> DocumentSyncActionType.DOWNLOAD
        isNewer(cloud.version, local.version) -> DocumentSyncActionType.UPGRADE
        else -> DocumentSyncActionType.NONE
    }
    DocumentSyncAction(cloud.initials, type)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentSyncResolverTest*"`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncResolver.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentSyncResolverTest.kt
git commit -m "Add document sync action resolver with decision table

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 3: Per-device block list + sync settings + metered-network check

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentBlockList.kt`
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt`
- Test: `app/src/test/java/net/bible/service/cloudsync/documents/DocumentBlockListTest.kt`

**Interfaces:**
- Produces:
  - `interface StringSetStore { fun get(): Set<String>; fun set(value: Set<String>) }`
  - `class DocumentBlockList(private val store: StringSetStore)` with `fun isBlocked(initials: String): Boolean`, `fun block(initials: String)`, `fun unblock(initials: String)`, `fun all(): Set<String>`.
  - `object DocumentSyncSettings`: `var enabled: Boolean`, `var automatic: Boolean`, `var wifiOnly: Boolean` (backed by `CommonUtils.settings` keys `sync_enable_documents`, `sync_documents_automatic` default true, `sync_documents_wifi_only` default true); `val blockList: DocumentBlockList`; `fun syncTimestamp(initials: String): Long?` / `fun setSyncTimestamp(initials: String, ts: Long)` (keys `doc_sync_ts_<initials>`); `val isAutoTransferAllowed: Boolean` (`!wifiOnly || !CommonUtils.isMeteredNetwork`).

`CommonUtils.isMeteredNetwork` is added in this task (see Step 3b).

- [ ] **Step 1: Write the failing test**

```kotlin
// DocumentBlockListTest.kt (package net.bible.service.cloudsync.documents)
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentBlockListTest {
    private class FakeStore : StringSetStore {
        var value: Set<String> = emptySet()
        override fun get() = value
        override fun set(value: Set<String>) { this.value = value }
    }

    @Test fun blocksAndUnblocks() {
        val store = FakeStore()
        val list = DocumentBlockList(store)
        assertFalse(list.isBlocked("KJV"))
        list.block("KJV")
        assertTrue(list.isBlocked("KJV"))
        assertEquals(setOf("KJV"), store.value)
        list.unblock("KJV")
        assertFalse(list.isBlocked("KJV"))
        assertEquals(emptySet<String>(), store.value)
    }

    @Test fun blockIsIdempotent() {
        val list = DocumentBlockList(FakeStore())
        list.block("KJV")
        list.block("KJV")
        assertEquals(setOf("KJV"), list.all())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentBlockListTest*"`
Expected: FAIL — unresolved references.

- [ ] **Step 3a: Write `DocumentBlockList.kt`**

```kotlin
// DocumentBlockList.kt — standard 2026 copyright header
package net.bible.service.cloudsync.documents

interface StringSetStore {
    fun get(): Set<String>
    fun set(value: Set<String>)
}

class DocumentBlockList(private val store: StringSetStore) {
    fun isBlocked(initials: String): Boolean = initials in store.get()
    fun block(initials: String) { store.set(store.get() + initials) }
    fun unblock(initials: String) { store.set(store.get() - initials) }
    fun all(): Set<String> = store.get()
}
```

- [ ] **Step 3b: Add `isMeteredNetwork` to `CommonUtils`**

In `app/src/main/java/net/bible/service/common/CommonUtils.kt`, near `isCloudSyncAvailable` (line ~1679), add:

```kotlin
val isMeteredNetwork: Boolean get() {
    val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return cm.isActiveNetworkMetered
}
```

Ensure imports `android.content.Context` and `android.net.ConnectivityManager` are present (add if missing).

- [ ] **Step 3c: Write `DocumentSyncSettings.kt`**

```kotlin
// DocumentSyncSettings.kt — standard 2026 copyright header
package net.bible.service.cloudsync.documents

import net.bible.service.common.CommonUtils

object DocumentSyncSettings {
    private const val ENABLED = "sync_enable_documents"
    private const val AUTOMATIC = "sync_documents_automatic"
    private const val WIFI_ONLY = "sync_documents_wifi_only"
    private const val BLOCKED = "sync_documents_blocked"
    private const val TS_PREFIX = "doc_sync_ts_"

    var enabled: Boolean
        get() = CommonUtils.settings.getBoolean(ENABLED, false)
        set(value) = CommonUtils.settings.setBoolean(ENABLED, value)

    var automatic: Boolean
        get() = CommonUtils.settings.getBoolean(AUTOMATIC, true)
        set(value) = CommonUtils.settings.setBoolean(AUTOMATIC, value)

    var wifiOnly: Boolean
        get() = CommonUtils.settings.getBoolean(WIFI_ONLY, true)
        set(value) = CommonUtils.settings.setBoolean(WIFI_ONLY, value)

    val blockList: DocumentBlockList = DocumentBlockList(object : StringSetStore {
        override fun get(): Set<String> =
            CommonUtils.settings.getString(BLOCKED, "")
                ?.split("\n")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        override fun set(value: Set<String>) =
            CommonUtils.settings.setString(BLOCKED, value.joinToString("\n"))
    })

    fun syncTimestamp(initials: String): Long? =
        CommonUtils.settings.getLong("$TS_PREFIX$initials", -1L).takeIf { it >= 0 }

    fun setSyncTimestamp(initials: String, ts: Long) =
        CommonUtils.settings.setLong("$TS_PREFIX$initials", ts)

    val isAutoTransferAllowed: Boolean
        get() = !wifiOnly || !CommonUtils.isMeteredNetwork
}
```

Note: verify `CommonUtils.settings` has `getLong`/`setLong`/`getString`/`setString` (it does — used widely). If `getLong` default-arg signature differs, adapt to the actual signature.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testStandardGoogleplayDebugUnitTest --tests "*DocumentBlockListTest*"`
Expected: PASS (2 tests). Then full compile check: `./gradlew compileStandardGoogleplayDebugKotlin`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentBlockList.kt app/src/main/java/net/bible/service/cloudsync/documents/DocumentSyncSettings.kt app/src/test/java/net/bible/service/cloudsync/documents/DocumentBlockListTest.kt app/src/main/java/net/bible/service/common/CommonUtils.kt
git commit -m "Add document sync settings, per-device block list, metered-network check

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

## Phase 2 — Archiving + headless install (concrete, build-verified)

### Task 4 (Step 0 cleanup): Extract reusable per-document zip function in BackupControl

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/backup/BackupControl.kt:345-419` (`createModulesZip`)

**Interfaces:**
- Produces: `suspend fun BackupControl.createSingleModuleZip(book: Book, zipFile: File)` — packages exactly one book into a `.abmd.zip` (same on-disk layout + manifest as `createModulesZip`). `createModulesZip(books, zipFile)` is refactored to package multiple books but the per-book file-collection logic is shared.

- [ ] **Step 1: Refactor** — extract the per-book body of the `for (b in books)` loop (lines 385-415) into a private helper that writes one book's files into an open `ZipOutputStream`, e.g. `private fun addBookToZip(outFile: ZipOutputStream, b: Book)`. Keep `createModulesZip` calling it in the loop. Add a new public function:

```kotlin
suspend fun createSingleModuleZip(book: Book, zipFile: File) = withContext(Dispatchers.IO) {
    val manifest = AndBibleBackupManifest(backupType = BackupType.MODULE_BACKUP)
    FileOutputStream(zipFile).use { out ->
        ZipOutputStream(out).use { outFile ->
            manifest.saveToZip(outFile)
            addBookToZip(outFile, book)
        }
    }
}
```

The `addBookToZip` helper contains exactly the existing per-book branch logic (MyBible/MySword/eSword via `addModuleFile`, EPUB via `addModuleDir`, else SWORD config+data). `addFile`/`addModuleFile`/`addModuleDir`/`relativeFileName` become private members usable by both (lift them out of `createModulesZip`'s local scope to file/class scope as needed).

- [ ] **Step 2: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL. (Pure refactor; no behavior change.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/android/control/backup/BackupControl.kt
git commit -m "Extract reusable per-document zip helper in BackupControl

Step-0 refactor to share single-module packaging with document sync.

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 5: DocumentArchiver — package + headless install

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentArchiver.kt`
- Reference (read, do not duplicate): `InstallZip.kt` `ZipHandler` (line 101), `installZipFile` (167); `addMyBibleBook`, `addMySwordBook`, `addESwordBook`, `addManuallyInstalledEpubBooks`, `addManuallyInstalledMyBibleBooks`, `addManuallyInstalledMySwordBooks`, `addManuallyInstalledESwordBooks` (imported in `InstallZip.kt:55-62`).

**Interfaces:**
- Consumes: `BackupControl.createSingleModuleZip` (Task 4); `DocumentType` (Task 1).
- Produces:
  - `fun documentTypeOf(book: Book): DocumentType` — maps via the `isManuallyInstalled*` extension props (`isManuallyInstalledMyBibleBook` → MYBIBLE, `isManuallyInstalledMySwordBook` → MYSWORD, `isManuallyInstalledESwordBook` → ESWORD, `isManuallyInstalledEpub` → EPUB, else SWORD).
  - `fun documentVersion(book: Book): String` — `book.bookMetaData.getProperty("Version") ?: "0.0"`.
  - `suspend fun packageDocument(book: Book): File` — returns a temp `.abmd.zip`.
  - `suspend fun installArchive(archive: File): Boolean` — installs an `.abmd.zip` headlessly (no Activity UI) and returns success.

- [ ] **Step 1: Implement packaging + type/version helpers**

```kotlin
// DocumentArchiver.kt — standard 2026 copyright header
package net.bible.service.cloudsync.documents

import net.bible.android.control.backup.BackupControl
import net.bible.service.common.CommonUtils
import net.bible.service.sword.epub.isManuallyInstalledEpub
import net.bible.service.sword.esword.isManuallyInstalledESwordBook
import net.bible.service.sword.mybible.isManuallyInstalledMyBibleBook
import net.bible.service.sword.mysword.isManuallyInstalledMySwordBook
import org.crosswire.jsword.book.Book
import java.io.File

object DocumentArchiver {
    fun documentTypeOf(book: Book): DocumentType = when {
        book.isManuallyInstalledMyBibleBook -> DocumentType.MYBIBLE
        book.isManuallyInstalledMySwordBook -> DocumentType.MYSWORD
        book.isManuallyInstalledESwordBook -> DocumentType.ESWORD
        book.isManuallyInstalledEpub -> DocumentType.EPUB
        else -> DocumentType.SWORD
    }

    fun documentVersion(book: Book): String =
        book.bookMetaData.getProperty("Version") ?: "0.0"

    suspend fun packageDocument(book: Book): File {
        val zipFile = File(CommonUtils.tmpDir, "doc-${book.initials}.abmd.zip")
        if (zipFile.exists()) zipFile.delete()
        BackupControl.createSingleModuleZip(book, zipFile)
        return zipFile
    }

    // installArchive implemented in Step 2
}
```

- [ ] **Step 2: Implement headless install**

Read `InstallZip.kt:101-310` (`ZipHandler`) to confirm the constructor signature and whether it can run without the Activity. `ZipHandler` operates on an `InputStream`/file and posts progress via `ABEventBus`; it does not require Activity UI for the happy path. Implement `installArchive` by invoking the same code path the Activity uses. If `ZipHandler` requires a `ContentResolver`/`Uri`, wrap the local file with `Uri.fromFile(archive)` and `app.contentResolver`. The `.abmd.zip` contains a manifest (`AndBibleBackupManifest`, `BackupType.MODULE_BACKUP`) plus module files in `modulesDir`-relative paths, so installation extracts files into `SharedConstants.modulesDir` and then runs the discovery/registration functions:

```kotlin
suspend fun installArchive(archive: File): Boolean = withContext(Dispatchers.IO) {
    // Reuse the established install path. Extract into modulesDir, then register:
    //   - SWORD: extract mods.d/*.conf + modules/** then SwordBookDriver.registerNewBook / Books refresh
    //   - sqlite types: copy into mybible/mysword/esword dirs then addManuallyInstalled*Books()
    //   - epub: extract into epub/<dir> then addManuallyInstalledEpubBooks()
    // Implementation mirrors InstallZip.installZipFile + the add* discovery calls.
    // Returns true if Books.installed() gained the document.
    TODO_IMPLEMENT_BY_MIRRORING_INSTALLZIP
}
```

> Implementer note: This step is the one place that genuinely reuses non-trivial existing logic. Before writing it, read `InstallZip.installZipFile()` (167-229) and `installFromFile()`/`installEpub()` to extract a headless installer. Prefer adding a small `suspend fun installModuleArchive(file: File): Boolean` to `BackupControl` (next to `restoreModulesViaIntent`) that contains the non-UI extraction+registration, and have `DocumentArchiver.installArchive` delegate to it. This keeps install logic beside the existing packaging logic. Do NOT duplicate extraction code.

- [ ] **Step 3: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual smoke (deferred to integration)** — actual install round-trip is validated in Phase 5 manual testing on a device, since it requires real installed modules + JSword registry. Note this in the commit.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentArchiver.kt app/src/main/java/net/bible/android/control/backup/BackupControl.kt
git commit -m "Add DocumentArchiver: package and headless-install document archives

Round-trip install reuses existing InstallZip extraction/registration.
Validated manually on device in a later phase (requires JSword registry).

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

## Phase 3 — Cloud layer + orchestrator

### Task 6: Expose authenticated adapter + document folder from CloudSync

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt`

**Interfaces:**
- Produces on `CloudSync`:
  - `internal val cloudAdapter: CloudAdapter?` — returns `_adapter` (null if not signed in).
  - `const val DOCUMENTS_SYNC_FOLDER_SUFFIX = "DOCUMENTS"`.
  - `suspend fun documentsSyncFolderId(): String?` — finds-or-creates the folder named `"${app.applicationInfo.packageName}-sync-DOCUMENTS"` at cloud root via `adapter.listFiles(name=...)` / `adapter.createNewFolder(name)`; returns its id, or null if not signed in.

- [ ] **Step 1: Implement** — add to `object CloudSync`:

```kotlin
internal val cloudAdapter: CloudAdapter? get() = _adapter

const val DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "DOCUMENTS"

suspend fun documentsSyncFolderId(): String? {
    val adapter = _adapter ?: return null
    val name = "${app.applicationInfo.packageName}-sync-$DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX"
    val existing = adapter.listFiles(name = name).firstOrNull()
    return existing?.id ?: adapter.createNewFolder(name).id
}
```

- [ ] **Step 2: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/CloudSync.kt
git commit -m "Expose authenticated adapter and documents sync folder from CloudSync

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 7: DocumentStore — cloud blob/manifest operations

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentStore.kt`

**Interfaces:**
- Consumes: `CloudAdapter` (interface), `CloudFile`, `DocumentSyncMeta`, `DocumentType`, `CloudSync.documentsSyncFolderId()`.
- Produces: `class DocumentStore(private val adapter: CloudAdapter, private val rootFolderId: String)` with:
  - `suspend fun listDocuments(): List<DocumentSyncMeta>` — list per-document subfolders, download each `meta.json`, parse. Skips folders without a valid meta.
  - `suspend fun uploadDocument(meta: DocumentSyncMeta, archive: File)` — ensure `{initials}/` folder; upload `{version}.abmd.zip` (tmp-name then meta commit); delete older `*.abmd.zip`; write/overwrite `meta.json` last.
  - `suspend fun downloadArchive(initials: String, version: String): File` — download `{initials}/{version}.abmd.zip` to a temp file.
  - `suspend fun writeTombstone(meta: DocumentSyncMeta)` — overwrite `meta.json` with `deleted=true`, new `timestamp`; delete the `*.abmd.zip`.

- [ ] **Step 1: Implement** (concrete; uses `adapter.getFolders`, `listFiles`, `download`, `upload`, `createNewFolder`, `delete`, and `CommonUtils.tmpFile`):

```kotlin
// DocumentStore.kt — standard 2026 copyright header
package net.bible.service.cloudsync.documents

import android.util.Log
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudFile
import net.bible.service.common.CommonUtils
import java.io.File

private const val TAG = "DocumentStore"

class DocumentStore(
    private val adapter: CloudAdapter,
    private val rootFolderId: String,
) {
    private suspend fun folderFor(initials: String): CloudFile? =
        adapter.getFolders(rootFolderId).firstOrNull { it.name == initials }

    private suspend fun ensureFolder(initials: String): String =
        folderFor(initials)?.id ?: adapter.createNewFolder(initials, rootFolderId).id

    private suspend fun readMeta(folderId: String): DocumentSyncMeta? {
        val metaFile = adapter.listFiles(parentsIds = listOf(folderId), name = DOCUMENT_META_FILENAME)
            .firstOrNull() ?: return null
        val tmp = CommonUtils.tmpFile
        return try {
            tmp.outputStream().use { adapter.download(metaFile.id, it) }
            DocumentSyncMeta.fromJson(tmp.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading meta for folder $folderId", e); null
        } finally { tmp.delete() }
    }

    suspend fun listDocuments(): List<DocumentSyncMeta> =
        adapter.getFolders(rootFolderId).mapNotNull { readMeta(it.id) }

    private suspend fun writeMeta(folderId: String, meta: DocumentSyncMeta) {
        // delete existing meta.json then upload fresh (acts as the atomic commit point)
        adapter.listFiles(parentsIds = listOf(folderId), name = DOCUMENT_META_FILENAME)
            .forEach { adapter.delete(it.id) }
        val tmp = CommonUtils.tmpFile
        try {
            tmp.writeText(meta.toJson())
            adapter.upload(DOCUMENT_META_FILENAME, tmp, folderId)
        } finally { tmp.delete() }
    }

    suspend fun uploadDocument(meta: DocumentSyncMeta, archive: File) {
        val folderId = ensureFolder(meta.initials)
        val archiveName = "${meta.version}.abmd.zip"
        // upload archive first; only then commit meta.json pointing at it
        adapter.upload(archiveName, archive, folderId)
        // remove older archives (keep only newest version)
        adapter.listFiles(parentsIds = listOf(folderId))
            .filter { it.name.endsWith(".abmd.zip") && it.name != archiveName }
            .forEach { adapter.delete(it.id) }
        writeMeta(folderId, meta)
    }

    suspend fun downloadArchive(initials: String, version: String): File {
        val folderId = folderFor(initials)?.id ?: error("No cloud folder for $initials")
        val file = adapter.listFiles(parentsIds = listOf(folderId), name = "$version.abmd.zip").first()
        val tmp = CommonUtils.tmpFile
        tmp.outputStream().use { adapter.download(file.id, it) }
        return tmp
    }

    suspend fun writeTombstone(meta: DocumentSyncMeta) {
        val folderId = ensureFolder(meta.initials)
        adapter.listFiles(parentsIds = listOf(folderId))
            .filter { it.name.endsWith(".abmd.zip") }
            .forEach { adapter.delete(it.id) }
        writeMeta(folderId, meta.copy(deleted = true))
    }
}
```

Note: timestamps must come from the caller (`Date.now()` equivalents). Use `System.currentTimeMillis()` in the orchestrator (Task 8), not here, to keep this class side-effect-light.

- [ ] **Step 2: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentStore.kt
git commit -m "Add DocumentStore for cloud document blob and manifest operations

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 8: DocumentSync orchestrator

**Files:**
- Create: `app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt`

**Interfaces:**
- Consumes: `CloudSync.cloudAdapter`, `CloudSync.documentsSyncFolderId()`, `DocumentStore`, `DocumentArchiver`, `DocumentSyncSettings`, `resolveDocumentSyncActions`, `DocumentSyncMeta`/`CloudDocument`/`LocalDocument`, `Books.installed()`.
- Produces `object DocumentSync`:
  - `data class DocumentStatusItem(val initials: String, val name: String, val type: DocumentType, val cloudVersion: String?, val localVersion: String?, val cloudOnly: Boolean, val localOnly: Boolean, val updateAvailable: Boolean, val blocked: Boolean, val sizeBytes: Long)`
  - `suspend fun scan(): List<DocumentStatusItem>` — merge `Books.installed()` with `store.listDocuments()`.
  - `suspend fun uploadDocument(book: Book)` — guarded by `enabled && automatic && !blocked && isAutoTransferAllowed` when auto-triggered; the management view calls `pushDocument(book)` for manual (no auto guards).
  - `suspend fun pushDocument(book: Book)` — unconditional upload (manual).
  - `suspend fun pullDocuments(automaticOnly: Boolean)` — run resolver, execute DOWNLOAD/UPGRADE/UNINSTALL; when `automaticOnly` and not allowed by network, return early.
  - `suspend fun downloadAndInstall(initials: String)` — manual download+install of one cloud doc.
  - `suspend fun removeFromCloud(book: Book?, initials: String, name: String, type: DocumentType)` — write tombstone.
  - `fun versionIsNewer(cloudVersion: String, localVersion: String): Boolean` — wraps `org.crosswire.common.util.Version`.

- [ ] **Step 1: Implement** (concrete):

```kotlin
// DocumentSync.kt — standard 2026 copyright header
package net.bible.service.cloudsync.documents

import android.util.Log
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books

object DocumentSync {
    private const val TAG = "DocumentSync"

    fun versionIsNewer(cloudVersion: String, localVersion: String): Boolean =
        try { Version(cloudVersion) > Version(localVersion) } catch (e: Exception) { false }

    private suspend fun store(): DocumentStore? {
        val adapter = CloudSync.cloudAdapter ?: return null
        val folderId = CloudSync.documentsSyncFolderId() ?: return null
        return DocumentStore(adapter, folderId)
    }

    private fun installedSyncableBooks(): List<Book> =
        Books.installed().books.filter { !it.isPseudoBook }

    data class DocumentStatusItem(
        val initials: String,
        val name: String,
        val type: DocumentType,
        val cloudVersion: String?,
        val localVersion: String?,
        val cloudOnly: Boolean,
        val localOnly: Boolean,
        val updateAvailable: Boolean,
        val blocked: Boolean,
        val sizeBytes: Long,
    )

    suspend fun scan(): List<DocumentStatusItem> {
        val store = store() ?: return emptyList()
        val cloud = store.listDocuments().filter { !it.deleted }.associateBy { it.initials }
        val local = installedSyncableBooks().associateBy { it.initials }
        val blocked = DocumentSyncSettings.blockList.all()
        val allInitials = (cloud.keys + local.keys).toSortedSet()
        return allInitials.map { initials ->
            val c = cloud[initials]; val b = local[initials]
            val localVersion = b?.let { DocumentArchiver.documentVersion(it) }
            val update = c != null && localVersion != null && versionIsNewer(c.version, localVersion)
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
            )
        }
    }

    suspend fun pushDocument(book: Book) {
        val store = store() ?: return
        val archive = DocumentArchiver.packageDocument(book)
        try {
            val bmd = book.bookMetaData
            val meta = DocumentSyncMeta(
                initials = book.initials,
                name = book.name,
                documentType = DocumentArchiver.documentTypeOf(book),
                version = DocumentArchiver.documentVersion(book),
                size = archive.length(),
                language = book.language.code,
                sourceDevice = CommonUtils.deviceIdentifier,
                timestamp = System.currentTimeMillis(),
                cipherKey = bmd.getProperty("CipherKey"),
            )
            // Skip if cloud already same/newer
            val existing = store.listDocuments().firstOrNull { it.initials == book.initials && !it.deleted }
            if (existing != null && !versionIsNewer(meta.version, existing.version)) {
                Log.i(TAG, "Cloud has same/newer ${book.initials}; skipping upload"); return
            }
            store.uploadDocument(meta, archive)
            DocumentSyncSettings.setSyncTimestamp(book.initials, meta.timestamp)
        } finally { archive.delete() }
    }

    suspend fun uploadDocument(book: Book) {
        if (!DocumentSyncSettings.enabled || !DocumentSyncSettings.automatic) return
        if (DocumentSyncSettings.blockList.isBlocked(book.initials)) return
        if (!DocumentSyncSettings.isAutoTransferAllowed) return
        pushDocument(book)
    }

    suspend fun downloadAndInstall(initials: String) {
        val store = store() ?: return
        val meta = store.listDocuments().firstOrNull { it.initials == initials && !it.deleted } ?: return
        val archive = store.downloadArchive(initials, meta.version)
        try {
            if (DocumentArchiver.installArchive(archive)) {
                DocumentSyncSettings.setSyncTimestamp(initials, meta.timestamp)
            }
        } finally { archive.delete() }
    }

    suspend fun pullDocuments(automaticOnly: Boolean) {
        if (!DocumentSyncSettings.enabled) return
        if (automaticOnly && (!DocumentSyncSettings.automatic || !DocumentSyncSettings.isAutoTransferAllowed)) return
        val store = store() ?: return
        val cloudMetas = store.listDocuments()
        val local = installedSyncableBooks().associateBy { it.initials }
        val cloudDocs = cloudMetas.map {
            CloudDocument(it.initials, it.name, it.documentType, it.version, it.size, it.timestamp, it.deleted)
        }
        val localDocs = local.mapValues { (i, b) -> LocalDocument(i, DocumentArchiver.documentVersion(b)) }
        val syncTimestamps = local.keys.mapNotNull { i -> DocumentSyncSettings.syncTimestamp(i)?.let { i to it } }.toMap()
        val actions = resolveDocumentSyncActions(
            cloudDocs, localDocs, syncTimestamps,
            DocumentSyncSettings.blockList.all(), ::versionIsNewer)
        for (action in actions) when (action.type) {
            DocumentSyncActionType.DOWNLOAD, DocumentSyncActionType.UPGRADE -> downloadAndInstall(action.initials)
            DocumentSyncActionType.UNINSTALL -> uninstallLocal(action.initials, local)
            DocumentSyncActionType.SKIP_BLOCKED, DocumentSyncActionType.NONE -> {}
        }
    }

    private fun uninstallLocal(initials: String, local: Map<String, Book>) {
        val book = local[initials] ?: return
        try {
            book.driver.delete(book)
            DocumentSyncSettings.setSyncTimestamp(initials, System.currentTimeMillis())
        } catch (e: Exception) { Log.e(TAG, "Failed uninstalling $initials", e) }
    }

    suspend fun removeFromCloud(initials: String, name: String, type: DocumentType) {
        val store = store() ?: return
        val existing = store.listDocuments().firstOrNull { it.initials == initials }
        val meta = (existing ?: DocumentSyncMeta(
            initials = initials, name = name, documentType = type, version = "0.0",
            size = 0, language = "", sourceDevice = CommonUtils.deviceIdentifier,
            timestamp = 0,
        )).copy(timestamp = System.currentTimeMillis())
        store.writeTombstone(meta)
    }
}
```

Note: confirm `book.driver.delete(book)` is the correct uninstall entry (per exploration: `SwordBookDriver.delete`, `SqliteSwordDriver.delete`, `EpubSwordDriver.delete` all implement `BookDriver.delete(Book)` and remove from `Books.installed()`). Confirm `CipherKey` is the correct conf property name for the cipher key; if the codebase stores it elsewhere (e.g. `SwordDocumentInfo.cipherKey` in RepoDatabase), read that instead and include it in meta.

- [ ] **Step 2: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/documents/DocumentSync.kt
git commit -m "Add DocumentSync orchestrator (scan, pull, push, remove)

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

## Phase 4 — Integration: triggers, sync cycle, settings UI

### Task 9: Settings UI (sync_settings.xml + SyncSettings.kt + strings.xml)

**Files:**
- Modify: `app/src/main/res/xml/sync_settings.xml`
- Modify: `app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `DocumentSyncSettings` (Task 3), `CloudDocumentsActivity` (Task 12 — for the link; if implementing settings before UI, gate the link behind a TODO and wire in Task 15).

- [ ] **Step 1: Add strings** to `strings.xml` (English):

```xml
<string name="document_sync_title">Documents</string>
<string name="document_sync_contents">Installed Bibles, commentaries and other documents</string>
<string name="document_sync_automatic_title">Automatic document sync</string>
<string name="document_sync_automatic_summary">Automatically upload installed documents and download documents from the cloud</string>
<string name="document_sync_wifi_only_title">Sync documents on Wi-Fi only</string>
<string name="document_sync_wifi_only_summary">Automatic document transfers wait for an unmetered (Wi-Fi) connection</string>
<string name="document_sync_manage_title">Manage cloud documents</string>
<string name="document_sync_manage_summary">View and manage documents stored in the cloud</string>
```

- [ ] **Step 2: Add a "Documents" category** to `sync_settings.xml` (after the existing `sync_category` block, before `</PreferenceScreen>`):

```xml
<PreferenceCategory
    android:key="document_sync_category"
    android:title="@string/document_sync_title">
    <SwitchPreferenceCompat android:key="sync_enable_documents"
        android:title="@string/document_sync_title"
        android:summary="@string/document_sync_contents"
        android:defaultValue="false"
        android:icon="@drawable/ic_baseline_description_gray_24" />
    <SwitchPreferenceCompat android:key="sync_documents_automatic"
        android:title="@string/document_sync_automatic_title"
        android:summary="@string/document_sync_automatic_summary"
        android:defaultValue="true"
        android:dependency="sync_enable_documents" />
    <SwitchPreferenceCompat android:key="sync_documents_wifi_only"
        android:title="@string/document_sync_wifi_only_title"
        android:summary="@string/document_sync_wifi_only_summary"
        android:defaultValue="true"
        android:dependency="sync_enable_documents" />
    <Preference android:key="document_sync_manage"
        android:title="@string/document_sync_manage_title"
        android:summary="@string/document_sync_manage_summary"
        android:dependency="sync_enable_documents" />
</PreferenceCategory>
```

- [ ] **Step 3: Wire `sync_enable_documents`** in `SyncSettings.kt` `onCreatePreferences` (after the existing `sync_enable_*` setup). The master toggle must: sign in if needed, set `DocumentSyncSettings.enabled = true`, then launch the management view in setup mode (Task 14). For now (before Task 14), set enabled + start a pull:

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
                    // Task 14 replaces this with: launch CloudDocumentsActivity in setup mode
                    startActivity(Intent(requireContext(), CloudDocumentsActivity::class.java)
                        .putExtra(CloudDocumentsActivity.EXTRA_SETUP_MODE, true))
                }
                activity?.recreate()
            }
            false
        } else { DocumentSyncSettings.enabled = false; true }
    }
}
preferenceScreen.findPreference<Preference>("document_sync_manage")!!.setOnPreferenceClickListener {
    startActivity(Intent(requireContext(), CloudDocumentsActivity::class.java))
    true
}
```

Add imports: `net.bible.service.cloudsync.documents.DocumentSyncSettings`, `net.bible.android.view.activity.cloud.CloudDocumentsActivity`, `android.content.Intent`.

> If executing Task 9 before Task 12/14, temporarily comment out the `CloudDocumentsActivity` references and leave a `// TODO(Task 15): wire management view` so the module compiles; Task 15 restores them. Prefer ordering: do Tasks 12–14 first, then 9's wiring — but the XML/strings parts of Task 9 can land independently.

- [ ] **Step 4: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/sync_settings.xml app/src/main/res/values/strings.xml app/src/main/java/net/bible/android/view/activity/settings/SyncSettings.kt
git commit -m "Add document sync settings UI

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 10: Upload/uninstall triggers in BookInstallWatcher

**Files:**
- Modify: `app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt`

**Interfaces:**
- Consumes: `DocumentSync.uploadDocument(book)`, `DocumentSyncSettings`.

- [ ] **Step 1: Trigger upload on `bookAdded`** — in the `bookAdded` callback (after `addBookToDb(book)`), launch a background coroutine to upload if auto-sync conditions hold. Use a module-level `CoroutineScope(Dispatchers.IO)`:

```kotlin
// add near top of object
private val syncScope = CoroutineScope(Dispatchers.IO)

// inside bookAdded(), after addBookToDb(book):
if (DocumentSyncSettings.enabled && DocumentSyncSettings.automatic) {
    syncScope.launch {
        try { DocumentSync.uploadDocument(book) }
        catch (e: Exception) { Log.e(TAG, "Document sync upload failed for ${book.initials}", e) }
    }
}
```

Add imports: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `net.bible.service.cloudsync.documents.DocumentSync`, `net.bible.service.cloudsync.documents.DocumentSyncSettings`.

- [ ] **Step 2: Uninstall handling on `bookRemoved`** — per the design, the default is **local-only** (do nothing to the cloud). Tombstone propagation is an explicit action from the management view (Task 13), NOT from `bookRemoved`. So add only a clarifying comment in `bookRemoved`:

```kotlin
// Document sync: local uninstall does NOT propagate to the cloud by default.
// "Remove from sync" (tombstone) is an explicit action in CloudDocumentsActivity.
```

- [ ] **Step 3: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/control/versification/BookInstallWatcher.kt
git commit -m "Trigger document sync upload when a document is installed

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 11: Wire pullDocuments into the sync cycle

**Files:**
- Modify: `app/src/main/java/net/bible/service/cloudsync/CloudSync.kt` (`synchronize()`)

**Interfaces:**
- Consumes: `DocumentSync.pullDocuments(automaticOnly=true)`.

- [ ] **Step 1: Call document pull** at the end of `synchronize()`'s `syncMutex.withLock { ... }` block (after the `DatabaseContainer.databaseAccessorFactories.asyncMap { ... }` loop, before the final "Synchronization complete" log):

```kotlin
try {
    DocumentSync.pullDocuments(automaticOnly = true)
} catch (e: Exception) {
    Log.e(TAG, "Document sync pull failed", e)
}
```

Add import: `net.bible.service.cloudsync.documents.DocumentSync`.

Note: `pullDocuments` itself guards on `enabled`/`automatic`/network and returns early when not applicable, and `synchronize()` already returns early if `!signedIn`. Uploads are event-driven (Task 10); the periodic cycle handles downloads/upgrades/tombstone-uninstalls.

- [ ] **Step 2: Build-verify**

Run: `./gradlew compileStandardGoogleplayDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/bible/service/cloudsync/CloudSync.kt
git commit -m "Run document sync pull as part of the sync cycle

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

## Phase 5 — Management view UI + onboarding (build + manual verification)

### Task 12: CloudDocumentsActivity skeleton + list rendering

**Files:**
- Create: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt`
- Create: `app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsAdapter.kt`
- Create: `app/src/main/res/layout/activity_cloud_documents.xml`
- Create: `app/src/main/res/layout/item_cloud_document.xml`
- Modify: `app/src/main/AndroidManifest.xml` (register activity)
- Modify: `app/src/main/res/values/strings.xml` (status + filter strings)

**Interfaces:**
- Consumes: `DocumentSync.scan()` → `List<DocumentStatusItem>`.
- Produces: `class CloudDocumentsActivity : ActivityBase()` with `companion object { const val EXTRA_SETUP_MODE = "setupMode" }`.

- [ ] **Step 1: Add strings** (status + filters + actions) to `strings.xml`:

```xml
<string name="cloud_doc_status_synced">Synced</string>
<string name="cloud_doc_status_local_only">On this device only</string>
<string name="cloud_doc_status_cloud_only">In cloud only</string>
<string name="cloud_doc_status_update">Update available</string>
<string name="cloud_doc_status_blocked">Blocked on this device</string>
<string name="cloud_doc_filter_all">All</string>
<string name="cloud_doc_filter_installed">Installed</string>
<string name="cloud_doc_filter_cloud">In cloud</string>
<string name="cloud_doc_filter_updates">Updates</string>
<string name="cloud_doc_filter_blocked">Blocked</string>
<string name="cloud_doc_action_download">Download</string>
<string name="cloud_doc_action_push">Push to cloud</string>
<string name="cloud_doc_action_remove_cloud">Remove from cloud</string>
<string name="cloud_doc_action_block">Don\'t sync to this device</string>
<string name="cloud_doc_action_unblock">Allow on this device</string>
<string name="cloud_doc_remove_confirm">Remove this document from the cloud? In automatic mode it will also be removed from your other devices.</string>
<string name="cloud_doc_setup_intro">Document sync enabled — choose what to sync.</string>
<string name="cloud_doc_setup_start">Start syncing</string>
<string name="cloud_doc_header_totals">Upload %1$d (%2$s) · Download %3$d (%4$s)</string>
<string name="cloud_doc_wifi_waiting">Automatic downloads will start on Wi-Fi.</string>
```

- [ ] **Step 2: Layouts** — `activity_cloud_documents.xml`: a vertical `LinearLayout` with a header `TextView` (`@+id/header`), a horizontal filter `ChipGroup` or `RadioGroup` (`@+id/filters`), a `RecyclerView` (`@+id/recycler`, weight=1), and a bottom action bar `LinearLayout` (`@+id/bottomBar`, initially `gone`) containing a `Button` (`@+id/primaryAction`). `item_cloud_document.xml`: a row with a type icon `ImageView` (`@+id/typeIcon`), title `TextView` (`@+id/title`), subtitle `TextView` (`@+id/subtitle`, shows version + size + status text), a status icon `ImageView` (`@+id/statusIcon`), a `CheckBox` (`@+id/checkbox`, `gone` outside selection mode), and an overflow `ImageButton` (`@+id/overflow`). Use existing AndBible row styles/colors; status conveyed by icon + text (e-ink safe).

- [ ] **Step 3: Adapter** — `CloudDocumentsAdapter(private val onAction: (DocumentStatusItem, CloudDocAction) -> Unit)` extending `RecyclerView.Adapter`, with `submit(items: List<DocumentStatusItem>)`. Define `enum class CloudDocAction { DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, UNBLOCK, TOGGLE_SELECT }`. Bind status text via a `statusText(item)` helper mapping the booleans to the strings above; bind a status icon (use existing FontAwesome/vector drawables: e.g. download arrow for cloud-only, check for synced, refresh for update, block for blocked). Selection-mode visuals added in Task 13.

- [ ] **Step 4: Activity** — inflate via View Binding, `super.buildActivityComponent().inject(this)` as other activities do, load `DocumentSync.scan()` in `lifecycleScope.launch` (with an Hourglass), apply the selected filter, and submit to the adapter. Read `EXTRA_SETUP_MODE` (used in Task 14). Overflow click shows a popup menu of valid actions for that item's status and calls back into the activity to perform them (download/push/remove/block) via `DocumentSync`, then re-scans.

- [ ] **Step 5: Manifest** — register inside `<application>`:

```xml
<activity android:name="net.bible.android.view.activity.cloud.CloudDocumentsActivity"
    android:label="@string/document_sync_manage_title"
    android:theme="@style/AppThemeNoActionBar" />
```

(Match the theme attribute other ActivityBase activities use — check a neighbouring `<activity>` entry and copy its `android:theme`/`android:configChanges`.)

- [ ] **Step 6: Build-verify**

Run: `./gradlew assembleStandardGithubDebug` (with `dangerouslyDisableSandbox: true`)
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/ app/src/main/res/layout/activity_cloud_documents.xml app/src/main/res/layout/item_cloud_document.xml app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "Add CloudDocumentsActivity with document list and filters

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 13: Selection mode + bulk actions + per-item actions

**Files:**
- Modify: `CloudDocumentsActivity.kt`, `CloudDocumentsAdapter.kt`

**Interfaces:**
- Consumes: `DocumentSync.downloadAndInstall`, `pushDocument`, `removeFromCloud`, `DocumentSyncSettings.blockList`.

- [ ] **Step 1: Per-item actions** — implement the overflow popup actions:
  - Cloud-only / update → **Download**: `DocumentSync.downloadAndInstall(initials)` (manual; bypasses wifi-only).
  - Local-only → **Push to cloud**: find the `Book` via `Books.installed().getBook(initials)`, `DocumentSync.pushDocument(book)`.
  - Synced/cloud-present → **Remove from cloud**: confirm with `Dialogs.simpleQuestion(..., R.string.cloud_doc_remove_confirm)`, then `DocumentSync.removeFromCloud(initials, name, type)`.
  - **Block/Unblock**: `DocumentSyncSettings.blockList.block(initials)` / `.unblock(initials)`.
  - After any action, re-scan and refresh.

- [ ] **Step 2: Selection mode** — a toolbar/menu toggle that shows checkboxes (`@+id/checkbox` visible), shows the bottom bar, and tracks a `MutableSet<String>` of selected initials. The bottom bar primary button performs a bulk action over the selection appropriate to context (in setup mode it's "Start syncing" — Task 14; in normal mode default to bulk Download for cloud-available selected items). Run bulk work in `lifecycleScope.launch` with an Hourglass and progress.

- [ ] **Step 3: Build-verify**

Run: `./gradlew assembleStandardGithubDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/
git commit -m "Add selection mode and per-item/bulk actions to CloudDocumentsActivity

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 14: Setup mode (onboarding)

**Files:**
- Modify: `CloudDocumentsActivity.kt`

**Interfaces:**
- Consumes: `EXTRA_SETUP_MODE`, `DocumentSync` push/pull, `DocumentSyncSettings`.

- [ ] **Step 1: Setup-mode UI** — when `EXTRA_SETUP_MODE` is true: show the intro header (`cloud_doc_setup_intro`), enter selection mode with **all items pre-selected**, populate the header totals (`cloud_doc_header_totals` — counts + sizes of would-upload local-only and would-download cloud-only/update items; compute MB strings), show the bottom bar with primary button text `cloud_doc_setup_start`, and show the Wi-Fi note if `DocumentSyncSettings.wifiOnly && CommonUtils.isMeteredNetwork`.

- [ ] **Step 2: Start action** — on "Start syncing": for each selected item run the appropriate operation (local-only → `pushDocument`; cloud-only/update → `downloadAndInstall`). For **deselected** items: cloud-only deselected → `DocumentSyncSettings.blockList.block(initials)`; local-only deselected → do not push (and optionally remember as not-pushed; minimal version: simply skip). Run in `lifecycleScope.launch` with progress; on completion, drop setup mode (hide intro + Start CTA) so the screen becomes the normal management view, and `finish()` back to settings if launched from there.

- [ ] **Step 3: Manual-mode enable** — when the master toggle is enabled with `DocumentSyncSettings.automatic == false`, the settings code (Task 9) should open the activity WITHOUT setup mode (plain management view). Verify Task 9's enable handler passes `EXTRA_SETUP_MODE = DocumentSyncSettings.automatic`.

- [ ] **Step 4: Build-verify**

Run: `./gradlew assembleStandardGithubDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/bible/android/view/activity/cloud/CloudDocumentsActivity.kt
git commit -m "Add setup mode (onboarding) to CloudDocumentsActivity

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

### Task 15: Entry points — settings link finalization + document chooser 3-dot menu

**Files:**
- Modify: `SyncSettings.kt` (finalize the `CloudDocumentsActivity` references from Task 9)
- Locate and modify the document chooser / "Download documents" screen menu.

**Interfaces:**
- Consumes: `CloudDocumentsActivity`, `DocumentSyncSettings.enabled`.

- [ ] **Step 1: Finalize settings wiring** — ensure Task 9's `sync_enable_documents` handler opens `CloudDocumentsActivity` with `EXTRA_SETUP_MODE = DocumentSyncSettings.automatic`, and the `document_sync_manage` Preference opens it without setup mode. Remove any temporary TODO/comment shims.

- [ ] **Step 2: Find the document chooser menu** — locate the document selection / download Activity and its options menu. Run:

```bash
grep -rln "DownloadActivity\|class .*Document.*Activity\|onCreateOptionsMenu" app/src/main/java/net/bible/android/view/activity/download app/src/main/java/net/bible/android/view/activity/page/screen 2>/dev/null
ls app/src/main/res/menu/ | grep -i "download\|document\|chooser"
```

Identify the 3-dot overflow menu XML used by the document chooser/download screen.

- [ ] **Step 3: Add a menu item** to that menu XML:

```xml
<item android:id="@+id/manage_cloud_documents"
    android:title="@string/document_sync_manage_title"
    android:showAsAction="never" />
```

In the hosting Activity's `onOptionsItemSelected`, handle `R.id.manage_cloud_documents` → `startActivity(Intent(this, CloudDocumentsActivity::class.java))`. Gate visibility in `onPrepareOptionsMenu`: `menu.findItem(R.id.manage_cloud_documents)?.isVisible = DocumentSyncSettings.enabled`.

- [ ] **Step 4: Build-verify**

Run: `./gradlew assembleStandardGithubDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add management view entry points (settings + document chooser menu)

Claude-Session: https://claude.ai/code/session_014fC6frBNBJnUY48kUgFPHz"
```

---

## Final verification (after all tasks)

- [ ] Run full unit tests: `./gradlew testStandardGoogleplayDebugUnitTest` — expect all green (new: `DocumentSyncMetaTest`, `DocumentSyncResolverTest`, `DocumentBlockListTest`).
- [ ] Full debug build: `./gradlew assembleStandardGithubDebug` — BUILD SUCCESSFUL.
- [ ] **Manual device test plan** (the I/O round-trip that unit tests can't cover):
  1. Device A: enable document sync (auto). Confirm setup screen lists installed docs with totals; "Start syncing" uploads them.
  2. Device B (same cloud account): enable document sync. Confirm setup screen lists cloud docs; "Start syncing" downloads + installs them; verify each type (SWORD, MyBible, MySword, eSword, EPUB) opens correctly.
  3. Update a doc on A → confirm B auto-upgrades on next sync.
  4. Block a doc on B → confirm it is not auto-downloaded; unblock → it downloads.
  5. "Remove from cloud" on A → confirm B uninstalls on next sync (auto); confirm a locally-installed-but-never-synced doc is NOT deleted by an old tombstone.
  6. WiFi-only: on mobile data, confirm auto transfers wait; manual download/push from the management view still works.
  7. Verify the management view in dark, light, and monochrome/e-ink themes; verify "no animations" is respected.

---

## Self-Review notes (author)

- **Spec coverage:** all doc types (Task 5 type mapping + BackupControl branches); all install methods (whole-file packaging); versions/newest-wins (Tasks 2, 8 `versionIsNewer`); per-device block (Tasks 3, 13); explicit onboarding (Task 14); management view + filters (Tasks 12–13); auto/manual modes (Tasks 8, 9, 14); WiFi-only (Tasks 3, 8); tombstone deletion default-off + optional (Tasks 7, 8, 10, 13); entry points (Task 15). Covered.
- **Deviation from spec:** network policy uses `ConnectivityManager.isActiveNetworkMetered` instead of WorkManager constraints (the project has no WorkManager). `sync_documents_auto_on_wifi` is intentionally omitted (the periodic sync cycle + metered check covers it), per the spec's "under consideration".
- **Unknowns to confirm during execution (flagged inline):** exact headless install reuse in `InstallZip` (Task 5 Step 2); cipher-key property source (`CipherKey` vs `SwordDocumentInfo.cipherKey`) (Task 8); `book.driver.delete(book)` uninstall entry (Task 8); document chooser menu location (Task 15 Step 2); neighbouring `<activity>` theme attributes (Task 12 Step 5); `CommonUtils.settings` long/string accessor signatures (Task 3).
