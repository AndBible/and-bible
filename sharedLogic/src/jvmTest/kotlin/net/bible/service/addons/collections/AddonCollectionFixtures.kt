package net.bible.service.addons.collections

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.PARAGRAH_BREAK_LABEL_NAME
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createTriggers
import net.bible.service.db.buildAppDatabase
import net.bible.service.db.exec
import net.bible.service.db.newTestDir
import net.bible.service.db.queryRows
import net.bible.service.db.writeStudyPadExportDb
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedlogic.platform.CollectionPackRef
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.lang.reflect.Proxy

/** Fixed source ids of the author's collection ([writeAuthorCollection]). */
object Src {
    val comfort = IdType("11111111-0000-4000-8000-000000000001")
    val study = IdType("11111111-0000-4000-8000-000000000002")
    val b1 = IdType("22222222-0000-4000-8000-000000000001")
    val b2 = IdType("22222222-0000-4000-8000-000000000002")
    val g1 = IdType("22222222-0000-4000-8000-000000000003")
    val entry = IdType("33333333-0000-4000-8000-000000000001")
}

fun hex(id: IdType) = "X'${id.toHex()}'"

fun testEnv(dir: String): AppEnvironment = Proxy.newProxyInstance(AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java)) { _, method, _ ->
    when (method.name) {
        "getIsDebugMode" -> false
        "getTmpDir", "getCacheDir", "getDbBackupPath" -> dir
        "getDeviceIdentifier" -> "device"
        "getVersionNumber" -> 0L
        else -> error("Unexpected AppEnvironment method: ${method.name}")
    }
} as AppEnvironment

fun openBookmarkDb(path: String): BookmarkDatabase = buildAppDatabase<BookmarkDatabase>(path, *bookmarkMigrations)

/** A local bookmark database with the sync triggers installed (as a device with sync set up has them). */
suspend fun newLocalDb(dir: String, name: String = "local.sqlite3", deviceId: String = "device"): BookmarkDatabase {
    val path = "$dir/$name"
    val db = openBookmarkDb(path)
    createTriggers(accessor(db, path, deviceId))
    return db
}

fun accessor(db: BookmarkDatabase, path: String, deviceId: String = "device") = SyncableDatabaseAccessor(
    localDb = db,
    dbFactory = { openBookmarkDb(it) },
    _resetLocalDb = { error("not in tests") },
    localDbPath = path,
    category = SyncableDatabaseDefinition.BOOKMARKS,
    deviceId = deviceId,
)

suspend fun BookmarkDatabase.sql(vararg statements: String) = useWriterConnection { c -> statements.forEach { c.exec(it) } }

suspend fun BookmarkDatabase.rows(sql: String): List<List<String?>> = useReaderConnection { c ->
    c.queryRows(sql) { st -> (0 until st.getColumnCount()).map { if (st.isNull(it)) null else st.getText(it) } }
}

suspend fun BookmarkDatabase.count(sql: String): Long = rows(sql).single().single()!!.toLong()

suspend fun BookmarkDatabase.hexIds(table: String, column: String = "id"): Set<String> =
    rows("SELECT hex($column) FROM $table").map { it[0]!!.lowercase() }.toSet()

/**
 * Seeds the author's own labels into [db]: label "Comfort" with bookmarks b1 (note) and b2, StudyPad "Study" with b2
 * (order 1), a generic bookmark g1 and a text entry (order 2). Returns nothing; ids are [Src].
 */
suspend fun seedAuthor(db: BookmarkDatabase) = db.sql(
    "INSERT INTO Label (id, name, color) VALUES (${hex(Src.comfort)}, 'Comfort', 5)",
    "INSERT INTO Label (id, name, color, type) VALUES (${hex(Src.study)}, 'Study', 6, 'EXAMPLE')",
    "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt, primaryLabelId) VALUES (${hex(Src.b1)}, 100, 101, 100, 101, 'KJV', 1, ${hex(Src.comfort)})",
    "INSERT INTO BibleBookmark (id, kjvOrdinalStart, kjvOrdinalEnd, ordinalStart, ordinalEnd, v11n, createdAt) VALUES (${hex(Src.b2)}, 200, 200, 200, 200, 'KJV', 2)",
    "INSERT INTO BibleBookmarkNotes (bookmarkId, notes) VALUES (${hex(Src.b1)}, 'Fear not')",
    "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(Src.b1)}, ${hex(Src.comfort)})",
    "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(Src.b2)}, ${hex(Src.comfort)})",
    "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId, orderNumber) VALUES (${hex(Src.b2)}, ${hex(Src.study)}, 1)",
    "INSERT INTO GenericBookmark (id, `key`, createdAt, bookInitials) VALUES (${hex(Src.g1)}, 'chapter 1', 3, 'DOC')",
    "INSERT INTO GenericBookmarkToLabel (bookmarkId, labelId, orderNumber) VALUES (${hex(Src.g1)}, ${hex(Src.study)}, 3)",
    "INSERT INTO StudyPadTextEntry (id, labelId, orderNumber, indentLevel) VALUES (${hex(Src.entry)}, ${hex(Src.study)}, 2, 0)",
    "INSERT INTO StudyPadTextEntryText (studyPadTextEntryId, text) VALUES (${hex(Src.entry)}, 'Intro text')",
)

/**
 * Writes the author's two labels into a collection file at [target] with the real StudyPad export, plus a link from
 * b2 to the paragraph-break special label (as a StudyPad with paragraph breaks has).
 */
suspend fun writeAuthorCollection(dir: String, target: Path, author: BookmarkDatabase? = null) {
    val source = author ?: openBookmarkDb("$dir/author-${System.nanoTime()}.sqlite3").also { seedAuthor(it) }
    try {
        FileSystem.SYSTEM.write(target) { }
        writeStudyPadExportDb(
            target,
            listOf(BookmarkEntities.Label(id = Src.comfort, name = "Comfort"), BookmarkEntities.Label(id = Src.study, name = "Study")),
            source = source,
            openExportDb = { openBookmarkDb(it) },
        )
    } finally {
        if (author == null) source.close()
    }
    val file = openBookmarkDb(target.toString())
    try {
        file.sql(
            "INSERT INTO Label (id, name) VALUES (X'AAAAAAAAAAAA4AAA8AAAAAAAAAAAAAAA', '$PARAGRAH_BREAK_LABEL_NAME')",
            "INSERT INTO BibleBookmarkToLabel (bookmarkId, labelId) VALUES (${hex(Src.b2)}, X'AAAAAAAAAAAA4AAA8AAAAAAAAAAAAAAA')",
        )
    } finally {
        file.close()
    }
}

fun ref(module: String, file: Path, version: String = "1.0") = CollectionPackRef(module, "$module name", version, file)

fun sha256(file: Path): String = FileSystem.SYSTEM.read(file) { readByteString() }.sha256().hex()


fun newDir(): String = newTestDir()

fun pathOf(dir: String, name: String): Path = "$dir/$name".toPath()

val paragraphBreakHex get() = PARAGRAPH_BREAK_LABEL_ID.toHex()
