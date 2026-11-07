package net.bible.demo

import kotlinx.coroutines.runBlocking
import net.bible.android.control.backup.DATABASE_BACKUP_NAME
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.TextContentType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.parseArgbHex
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.service.db.useWriterConnectionMarked
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Turns a [DemoContent] YAML into an AndBible database backup zip: the content is written into real, freshly created
 * Room databases through their DAOs (bundled SQLite driver, as the app uses), then `BackupControl.zipDatabaseFiles()`
 * makes the zip, so the file is exactly what the apps write and restore. Every validation failure is an
 * [IllegalArgumentException] naming the offending item.
 *
 * Command line: `--content <yaml> --out <zip>` (see the `generateDemoBackup` task of `:sharedLogic`).
 */
object DemoBackupGenerator {
    /** The pseudo document the app stores a StudyPad window under (`FakeBookFactory.journalDocument`). */
    private const val STUDYPAD_DOCUMENT = "MyStudyPads"

    suspend fun generate(content: DemoContent, outZip: Path) {
        validate(content)
        val dir = newTestDir(durable = true)
        val previousHost = try { DatabaseContainer.host } catch (_: IllegalStateException) { null }
        val ownsKoin = KoinPlatform.getKoinOrNull() == null
        if (ownsKoin) startKoin { modules(module { single<net.bible.sharedcore.platform.DateTimeFormats> { FixedDateTimeFormats } }) }
        var container: DatabaseContainer? = null
        try {
            DatabaseContainer.installHost(FakeDatabaseHost(dir))
            container = DatabaseContainer.create()
            // Room opens lazily: touch every database so each file exists, as in a used app.
            for (name in ALL_DB_FILENAMES) container.dbByFilename.getValue(name).useWriterConnectionMarked { }
            write(content, container)
            container.sync()
            closeContainer(container)
            container = null

            val zip = checkNotNull(DemoBackupRig(dir).backup.zipDatabaseFiles()) { "zipDatabaseFiles produced no zip" }
            Files.createDirectories(outZip.toAbsolutePath().parent)
            check(DATABASE_BACKUP_NAME == zip.name)
            Files.copy(Path.of(zip.toString()), outZip, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            container?.let { closeContainer(it) }
            DatabaseContainer.ready = false
            DatabaseContainer.detachInstanceForTest()
            previousHost?.let { DatabaseContainer.installHost(it) }
            if (ownsKoin) stopKoin()
        }
    }

    private suspend fun closeContainer(container: DatabaseContainer) {
        DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) }
        DatabaseContainer.ready = false
    }

    private fun versification(name: String, where: String): Versification =
        Versifications.instance().getVersification(name) ?: throw IllegalArgumentException("$where: unknown versification '$name'")

    private fun displayStyle(value: String?, where: String): BookmarkDisplayStyle? = value?.let {
        runCatching { BookmarkDisplayStyle.valueOf(it) }.getOrNull()
            ?: throw IllegalArgumentException("$where: unknown display style '$it' (${BookmarkDisplayStyle.values().joinToString()})")
    }

    private fun verseRange(b: DemoBookmark) = try {
        VerseRangeFactory.fromString(versification(b.v11n, "bookmark '${b.id}'"), b.ref)
    } catch (e: IllegalArgumentException) { throw e } catch (e: Exception) {
        throw IllegalArgumentException("bookmark '${b.id}': cannot read ref '${b.ref}': ${e.message}", e)
    }

    /** All reference checks, run before anything is written. */
    private fun validate(c: DemoContent) {
        fun dup(kind: String, ids: List<String>) {
            ids.groupBy { it }.filter { it.value.size > 1 }.keys.firstOrNull()?.let { throw IllegalArgumentException("duplicate $kind id '$it'") }
        }
        dup("label", c.labels.map { it.id }); dup("bookmark", c.bookmarks.map { it.id })
        dup("workspace", c.workspaces.map { it.name })
        val labelIds = c.labels.map { it.id }.toSet()
        for (l in c.labels) {
            runCatching { parseArgbHex(l.color) }.onFailure { throw IllegalArgumentException("label '${l.id}': bad color '${l.color}' (use #RRGGBB or #AARRGGBB)") }
            displayStyle(l.displayStyle, "label '${l.id}'"); displayStyle(l.wholeVerseStyle, "label '${l.id}'")
        }
        for (b in c.bookmarks) {
            for (l in b.labels) require(l in labelIds) { "bookmark '${b.id}': unknown label id '$l'" }
            b.primaryLabel?.let { require(it in b.labels) { "bookmark '${b.id}': primaryLabel '$it' is not in its labels" } }
            verseRange(b)
        }
        val bookmarksById = c.bookmarks.associateBy { it.id }
        for (sp in c.studypads) {
            require(sp.label in labelIds) { "studypad: unknown label id '${sp.label}'" }
            for ((i, e) in sp.entries.withIndex()) {
                require((e.bookmark == null) != (e.text == null)) { "studypad '${sp.label}' entry #${i + 1}: set exactly one of bookmark and text" }
                e.bookmark?.let {
                    val b = requireNotNull(bookmarksById[it]) { "studypad '${sp.label}' entry #${i + 1}: unknown bookmark id '$it'" }
                    require(sp.label in b.labels) { "studypad '${sp.label}' entry #${i + 1}: bookmark '$it' does not carry label '${sp.label}'" }
                }
            }
        }
        val categories = setOf("BIBLE", "COMMENTARY", "DICTIONARY", "GENERAL_BOOK", "MAPS", "MY_NOTE", "STUDYPAD")
        for (w in c.workspaces) {
            for ((i, win) in w.windows.withIndex()) {
                val where = "workspace '${w.name}' window #${i + 1}"
                require(win.category in categories) { "$where: unknown category '${win.category}' (${categories.joinToString()})" }
                when (win.category) {
                    "STUDYPAD" -> require(win.label in labelIds) { "$where: unknown or missing label id '${win.label}'" }
                    "MY_NOTE" -> {}
                    else -> require(win.document != null) { "$where: 'document' is required for ${win.category}" }
                }
                require(win.state.uppercase() in setOf("VISIBLE", "SPLIT", "MINIMISED")) { "$where: unknown state '${win.state}' (VISIBLE, MINIMISED)" }
                if (win.category in setOf("BIBLE", "COMMENTARY", "MY_NOTE") && win.key != null) verseOf(win.key, where)
            }
            require(w.windows.count { it.maximized } <= 1) { "workspace '${w.name}': more than one maximized window" }
            for (k in w.textDisplaySettings.keys) setterFor(k, "workspace '${w.name}' textDisplaySettings")
        }
    }

    private val defaultV11n get() = Versifications.instance().getVersification("KJV")!!

    private fun verseOf(osis: String, where: String): WorkspaceEntities.Verse {
        val v = try { VerseRangeFactory.fromString(defaultV11n, osis).start } catch (e: Exception) {
            throw IllegalArgumentException("$where: cannot read verse '$osis': ${e.message}", e)
        }
        return WorkspaceEntities.Verse(defaultV11n.name, v.book.ordinal, v.chapter, v.verse)
    }

    private fun setterFor(field: String, where: String): java.lang.reflect.Method {
        val name = "set" + field.replaceFirstChar { it.uppercase() }
        return WorkspaceEntities.TextDisplaySettings::class.java.methods
            .firstOrNull { it.name == name && it.parameterCount == 1 && it.parameterTypes[0] in setOf(java.lang.Integer::class.java, java.lang.Boolean::class.java, String::class.java) }
            ?: throw IllegalArgumentException("$where: unknown setting '$field' (supported: Int, Boolean and String fields of TextDisplaySettings)")
    }

    private fun applySettings(target: WorkspaceEntities.TextDisplaySettings, values: Map<String, String>, where: String) {
        for ((field, raw) in values) {
            val setter = setterFor(field, where)
            val value: Any = try {
                when (setter.parameterTypes[0]) {
                    java.lang.Integer::class.java -> raw.toInt()
                    java.lang.Boolean::class.java -> raw.toBooleanStrict()
                    else -> raw
                }
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$where: bad value '$raw' for '$field'", e)
            }
            setter.invoke(target, value)
        }
    }

    private suspend fun write(c: DemoContent, container: DatabaseContainer) {
        val dao = container.bookmarkDb.bookmarkDao()
        val labels = c.labels.associate { l ->
            l.id to BookmarkEntities.Label(
                name = l.name,
                color = parseArgbHex(l.color),
                displayStyle = displayStyle(l.displayStyle, "label '${l.id}'") ?: BookmarkDisplayStyle.HIGHLIGHT,
                displayStyleWholeVerse = displayStyle(l.wholeVerseStyle, "label '${l.id}'"),
                favourite = l.favourite,
            )
        }
        for (l in labels.values) dao.insert(l)

        // The order inside a StudyPad: bookmark and text entries share one sequence.
        val studyPadEntryOrder = HashMap<Pair<String, String>, Int>() // (label id, bookmark id) -> orderNumber
        val textOrders = ArrayList<Triple<String, String, Int>>()
        for (sp in c.studypads) {
            sp.entries.forEachIndexed { i, e ->
                if (e.bookmark != null) studyPadEntryOrder[sp.label to e.bookmark] = i else textOrders += Triple(sp.label, e.text!!, i)
            }
        }

        for (b in c.bookmarks) {
            val range = verseRange(b)
            val entity = BookmarkEntities.BibleBookmarkWithNotes(range, null, true, null).apply {
                primaryLabelId = b.primaryLabel?.let { labels.getValue(it).id }
            }.bookmarkEntity
            dao.insert(entity)
            b.note?.let { dao.insert(BookmarkEntities.BibleBookmarkNotes(entity.id, it, TextContentType.HTML)) }
            for (l in b.labels) {
                dao.insert(BookmarkEntities.BibleBookmarkToLabel(
                    bookmarkId = entity.id, labelId = labels.getValue(l).id,
                    orderNumber = studyPadEntryOrder[l to b.id] ?: -1,
                ))
            }
        }
        for ((labelKey, text, orderNumber) in textOrders) {
            val entry = BookmarkEntities.StudyPadTextEntry(labelId = labels.getValue(labelKey).id, orderNumber = orderNumber, contentType = TextContentType.HTML)
            dao.insert(entry)
            dao.insert(BookmarkEntities.StudyPadTextEntryText(entry.id, text))
        }

        val wdao = container.workspaceDb.workspaceDao()
        val workspaceIds = ArrayList<IdType>()
        c.workspaces.forEachIndexed { wi, w ->
            val windowIds = w.windows.map { IdType() }
            val maximizedIndex = w.windows.indexOfFirst { it.maximized }
            val settings = WorkspaceEntities.TextDisplaySettings().also { applySettings(it, w.textDisplaySettings, "workspace '${w.name}' textDisplaySettings") }
            val ws = WorkspaceEntities.Workspace(
                name = w.name, orderNumber = wi, textDisplaySettings = settings,
                maximizedWindowId = windowIds.getOrNull(maximizedIndex),
            )
            wdao.insertWorkspace(ws)
            workspaceIds += ws.id
            w.windows.forEachIndexed { i, win ->
                val state = if (win.state.uppercase() == "MINIMISED") "MINIMISED" else "VISIBLE"
                wdao.insertWindow(WorkspaceEntities.Window(
                    workspaceId = ws.id, isSynchronized = win.synchronized, isPinMode = win.pinned,
                    windowLayout = WorkspaceEntities.WindowLayout(state, win.weight),
                    id = windowIds[i], orderNumber = i,
                ))
                wdao.insertPageManager(pageManager(windowIds[i], win, labels))
            }
        }
        container.settingsStore.apply {
            workspaceIds.firstOrNull()?.let { setString("current_workspace_id", it.toString()) }
            flush()
        }
    }

    private fun pageManager(windowId: IdType, win: DemoWindow, labels: Map<String, BookmarkEntities.Label>): WorkspaceEntities.PageManager {
        val where = "window of category ${win.category}"
        val verse = if (win.category in setOf("BIBLE", "COMMENTARY", "MY_NOTE") && win.key != null) verseOf(win.key, where)
        else WorkspaceEntities.Verse(defaultV11n.name, BibleBook.GEN.ordinal, 1, 1)
        val bibleDocument = when (win.category) {
            "BIBLE" -> win.document
            else -> win.bibleDocument
        }
        return WorkspaceEntities.PageManager(
            windowId = windowId,
            biblePage = WorkspaceEntities.BiblePage(bibleDocument, verse),
            commentaryPage = WorkspaceEntities.CommentaryPage(if (win.category == "COMMENTARY") win.document else null, null, null),
            dictionaryPage = WorkspaceEntities.Page(if (win.category == "DICTIONARY") win.document else null, if (win.category == "DICTIONARY") win.key else null, null),
            generalBookPage = when (win.category) {
                "GENERAL_BOOK" -> WorkspaceEntities.Page(win.document, win.key, null)
                "STUDYPAD" -> WorkspaceEntities.Page(STUDYPAD_DOCUMENT, "journal:${labels.getValue(win.label!!).id}", null)
                else -> WorkspaceEntities.Page(null, null, null)
            },
            mapPage = WorkspaceEntities.Page(if (win.category == "MAPS") win.document else null, if (win.category == "MAPS") win.key else null, null),
            currentCategoryName = when (win.category) {
                "STUDYPAD" -> "GENERAL_BOOK"
                "MY_NOTE" -> "MYNOTE"
                else -> win.category
            },
            textDisplaySettings = null,
            jsState = null,
        )
    }
}

fun main(args: Array<String>) {
    fun arg(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val content = arg("--content")
    val out = arg("--out")
    if (content == null || out == null) {
        System.err.println("usage: --content <content.yaml> --out <backup.abdb.zip>")
        exitProcess(2)
    }
    try {
        runBlocking { DemoBackupGenerator.generate(DemoContent.parse(Files.readString(Path.of(content))), Path.of(out)) }
        println("Wrote $out")
    } catch (e: Exception) {
        System.err.println("Demo backup generation failed: ${e.message}")
        exitProcess(1)
    }
    exitProcess(0)
}
