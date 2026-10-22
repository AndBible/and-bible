package net.bible.android.control.backup

import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.cloudsync.CloudSync
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import okio.Buffer
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.Timeout
import okio.source
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.StoredZipWriter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Task 8 (W4): [BackupControl.extractAndRegisterModuleArchive] must never leave a module `.conf` next to partial data
 * when the extraction is interrupted at entry k (for every k from 1 to entryCount - 1), and an interrupted
 * reinstall must leave the already installed module readable.
 *
 * The archive is the real K4ZTextBook fixture with `mods.d` FIRST, as real module zips are ordered. The interruption is
 * an [IOException] from the archive [Source] after exactly the bytes of the first k entries (and, in a second variant,
 * halfway through entry k+1's payload). The source is clamped to the cut, so ZipReader's read-ahead cannot overshoot it.
 *
 * Consistency rule after the interruption and a fresh re-scan: either the conf is absent and the book is not in
 * `Books.installed()`, or conf plus every DataPath file are byte-equal to ONE complete generation.
 */
class ModuleArchiveInterruptionTest {
    private lateinit var dest: File
    private lateinit var work: File
    private lateinit var tempHome: File
    private lateinit var savedDownloadDir: File
    private lateinit var savedInstance: Any
    private var savedHomeProperty: String? = null

    @BeforeTest fun setUp() {
        val instanceField = CWProject::class.java.getDeclaredField("instance").apply { isAccessible = true }
        savedInstance = instanceField.get(null)
        savedHomeProperty = System.getProperty("jsword.home")
        tempHome = Files.createTempDirectory("archive-int-home").toFile()
        System.setProperty("jsword.home", tempHome.absolutePath)
        CWProject::class.java.getDeclaredMethod("reset").apply { isAccessible = true }.invoke(null)
        CWProject.instance().setFrontendName("jsword-test")
        savedDownloadDir = SwordBookPathJvm.getSwordDownloadDir()
        work = Files.createTempDirectory("archive-int-work").toFile()
        dest = File(work, "modules").apply { mkdirs() }
        runCatching { stopKoin() }
        startKoin {
            modules(module {
                single<AppDirectories> { TestDirs(dest.absolutePath.toPath()) }
            })
        }
        SwordBookPathJvm.setDownloadDir(dest)
    }

    @AfterTest fun tearDown() {
        stopKoin()
        SwordBookPathJvm.setDownloadDir(savedDownloadDir)
        CWProject::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, savedInstance)
        if (savedHomeProperty == null) System.clearProperty("jsword.home") else System.setProperty("jsword.home", savedHomeProperty)
        Books.installed().getBook(INITIALS)?.let { Books.installed().removeBook(it) }
        tempHome.deleteRecursively()
        work.deleteRecursively()
    }

    private class TestDirs(private val root: Path) : AppDirectories {
        override val internalFilesDir: Path = root / "internal"
        override val modulesDir: Path = root
        override val manualInstallDir: Path = root / "manual"
        override val manualInstallDir2: Path = root / "manual2"
        override val manualReadingPlanDir: Path = root / "plans"
    }

    private inline fun <reified T : Any> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, _ ->
            error("unexpected call of ${T::class.simpleName}.${m.name}")
        } as T

    /** Instantiates a final class without running its constructor: these collaborators are never touched by extraction. */
    private inline fun <reified T : Any> blank(): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), T::class.java) as T
    }

    private fun backupControl() = BackupControl(
        dialogs = AppDialogController(), strings = stub<CoreStrings>(), env = stub<AppEnvironment>(),
        settings = stub<AppSettings>(), windowControl = blank<WindowControl>(), cloudSync = blank<CloudSync>(),
        notifier = stub<UserNotifier>(), dirs = TestDirs(dest.absolutePath.toPath()),
    )

    private fun fixtureRoot(): File {
        val root = File("../jsword/src/commonTest/resources/k4-modules/space dir/ä")
        check(File(root, "mods.d/$INITIALS.conf").isFile) { "K4 fixture not found from ${File(".").absolutePath}" }
        return root
    }

    /** Generation 1 is the fixture as is; generation 2 differs in the conf and in every data file. */
    private fun generation(number: Int): List<Pair<String, ByteArray>> {
        val root = fixtureRoot()
        val conf = File(root, "mods.d/$INITIALS.conf").readBytes()
        val data = File(root, "modules/$INITIALS").listFiles()!!.sortedBy { it.name }
        return if (number == 1) {
            listOf("mods.d/$INITIALS.conf" to conf) + data.map { "modules/$INITIALS/${it.name}" to it.readBytes() }
        } else {
            listOf("mods.d/$INITIALS.conf" to (conf + "\nAbout=generation two\n".toByteArray())) +
                data.map { "modules/$INITIALS/${it.name}" to (it.readBytes() + byteArrayOf(0, 0, 0, 0)) }
        }
    }

    private fun zip(entries: List<Pair<String, ByteArray>>, name: String): File {
        val buffer = Buffer()
        StoredZipWriter.write(buffer, entries)
        return File(work, name).apply { writeBytes(buffer.readByteArray()) }
    }

    /** Bytes of the local records of the first [count] entries (30-byte header, name, stored body, no descriptor). */
    private fun boundary(entries: List<Pair<String, ByteArray>>, count: Int): Long =
        entries.take(count).sumOf { (name, body) -> 30L + name.encodeToByteArray().size + body.size }

    /** Delivers exactly [limit] bytes of [inner], then fails every read with an [IOException]. */
    private class CutSource(private val inner: Source, private val limit: Long) : Source {
        var tripped = false
        var closed = false
        private var delivered = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (delivered >= limit) { tripped = true; throw IOException("injected interruption after $limit bytes") }
            val n = inner.read(sink, minOf(byteCount, limit - delivered))
            if (n > 0) delivered += n
            return n
        }
        override fun timeout(): Timeout = inner.timeout()
        override fun close() { closed = true; inner.close() }
    }

    private fun rescan() {
        Books.installed().getBook(INITIALS)?.let { Books.installed().removeBook(it) }
        Books.installed().registerDriver(SwordBookDriver.instance())
    }

    private fun matches(entries: List<Pair<String, ByteArray>>) = entries.all { (name, bytes) ->
        File(dest, name).let { it.isFile && it.readBytes().contentEquals(bytes) }
    }

    /** Applies the consistency rule and returns "absent", "gen1" or "gen2". */
    private fun assertConsistent(label: String, generations: List<List<Pair<String, ByteArray>>>): String {
        rescan()
        if (!File(dest, "mods.d/$INITIALS.conf").exists()) {
            assertNull(Books.installed().getBook(INITIALS), "$label: no conf, yet the book is installed")
            return "absent"
        }
        val index = generations.indexOfFirst { matches(it) }
        if (index < 0) fail("$label: conf present next to partial or mixed data " +
            generations.first().drop(1).map { (n, b) -> n to (File(dest, n).takeIf { it.isFile }?.length() to b.size) })
        assertNotNull(Books.installed().getBook(INITIALS), "$label: complete module is not discovered")
        return "gen${index + 1}"
    }

    /** Runs one interrupted extraction; returns the cut source so the caller can check the failure really came from it. */
    private fun interrupted(archive: File, limit: Long): CutSource {
        var cut: CutSource? = null
        val result = runCatching {
            runBlocking { backupControl().extractAndRegisterModuleArchive({ CutSource(archive.source(), limit).also { cut = it } }) }
        }
        assertTrue(result.isFailure, "extraction must fail when the archive is cut at $limit bytes")
        val source = checkNotNull(cut)
        assertTrue(source.tripped, "the failure must be the injected one (cut at $limit): ${result.exceptionOrNull()}")
        assertTrue(source.closed, "the archive source must be closed after the failure")
        return source
    }

    private fun cuts(entries: List<Pair<String, ByteArray>>, k: Int): List<Pair<String, Long>> {
        val (name, body) = entries[k]
        return listOf(
            "after $k entries" to boundary(entries, k),
            "inside payload of entry ${k + 1}" to boundary(entries, k) + 30 + name.encodeToByteArray().size + body.size / 2,
        )
    }

    @Test fun interruptedExtractNeverLeavesConfWithPartialData() {
        val entries = generation(1)
        assertEquals("mods.d/$INITIALS.conf", entries.first().first, "mods.d first, as real module zips are ordered")
        val archive = zip(entries, "gen1.zip")
        for (k in 1 until entries.size) for ((how, limit) in cuts(entries, k)) {
            dest.deleteRecursively(); dest.mkdirs()
            interrupted(archive, limit)
            val state = assertConsistent("k=$k $how", listOf(entries))
            println("MODULE-INTERRUPTION k=$k of ${entries.size} ($how) -> $state")
        }
    }

    @Test fun interruptedOverwriteKeepsAReadableModule() {
        val old = generation(1)
        val new = generation(2)
        val oldZip = zip(old, "gen1.zip")
        val newZip = zip(new, "gen2.zip")
        for (k in 1 until new.size) for ((how, limit) in cuts(new, k)) {
            dest.deleteRecursively(); dest.mkdirs()
            runBlocking { backupControl().extractAndRegisterModuleArchive({ oldZip.source() }) }
            assertEquals("gen1", assertConsistent("k=$k $how setup", listOf(old, new)))
            interrupted(newZip, limit)
            val state = assertConsistent("k=$k $how", listOf(old, new))
            println("MODULE-OVERWRITE-INTERRUPTION k=$k of ${new.size} ($how) -> $state")
            // generation 2 never completes, so only generation 1 may survive, and it must still read
            assertEquals("gen1", state, "k=$k $how: the installed module was lost or mixed")
            val book = assertNotNull(Books.installed().getBook(INITIALS), "k=$k $how: installed module vanished")
            assertEquals("In the beginning God created the heaven and the earth.",
                book.getRawText(book.getKey("Gen.1.1")).trim(), "k=$k $how: Gen 1:1 unreadable")
        }
    }

    private companion object { const val INITIALS = "K4ZTextBook" }
}
