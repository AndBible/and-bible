package net.bible.android.control.backup

import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentArchiver
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedcore.platform.desktop.DesktopDateTimeFormats
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
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookDriver
import java.util.zip.ZipFile
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
        // Interruption snapshots must retain real SQLite journal/durability behavior.
        net.bible.service.db.TestSqliteDurability.keepDurableDirectory(work.absolutePath)
        dest = File(work, "modules").apply { mkdirs() }
        runCatching { stopKoin() }
        startKoin {
            modules(module {
                single<AppDirectories> { TestDirs(dest.absolutePath.toPath()) }
                single<DateTimeFormats> { DesktopDateTimeFormats() }
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

    private fun backupControl(env: AppEnvironment = stub<AppEnvironment>()) = BackupControl(
        dialogs = AppDialogController(), strings = stub<CoreStrings>(), env = env,
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

    private fun strayFiles(root: File) = root.walkTopDown().filter { it.isFile && (it.name.endsWith(".part") ||
        it.name.endsWith(".tmp") || it.name.endsWith(".old")) }.map { it.relativeTo(root).path }.toList()

    /** File-level state a kill would leave: "absent", "gen1"/"gen2", or "VIOLATION" (conf next to partial or mixed data). */
    private fun snapshot(generations: List<List<Pair<String, ByteArray>>>): String {
        if (!File(dest, "mods.d/$INITIALS.conf").exists()) return "absent"
        val index = generations.indexOfFirst { matches(it) }
        return if (index < 0) "VIOLATION" else "gen${index + 1}"
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
        assertEquals(emptyList(), strayFiles(dest), "$label: staged .part/.tmp/.old files left behind")
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

    /**
     * The publish phase (data renames, then conf) is not atomic as a whole. The state at every step boundary is what a
     * kill would leave: it must be absent or one complete generation, never the old conf next to mixed data; and a
     * handled failure at any step must leave absent or a complete generation and no staged leftovers.
     */
    @Test fun interruptedPublishNeverLeavesConfNextToMixedData() {
        val old = generation(1)
        val new = generation(2)
        val oldZip = zip(old, "gen1.zip")
        val newZip = zip(new, "gen2.zip")
        fun fresh(overwrite: Boolean) {
            dest.deleteRecursively(); dest.mkdirs()
            if (overwrite) runBlocking { backupControl().extractAndRegisterModuleArchive({ oldZip.source() }) }
        }
        for (overwrite in listOf(false, true)) {
            fresh(overwrite)
            val control = backupControl()
            val snapshots = mutableListOf<String>()
            control.onPublishStep = { snapshots += snapshot(listOf(old, new)) }
            runBlocking { control.extractAndRegisterModuleArchive({ newZip.source() }) }
            assertEquals(new.size, snapshots.size, "one publish step per data file plus the conf")
            assertTrue(snapshots.none { it == "VIOLATION" }, "overwrite=$overwrite: kill-state snapshots $snapshots")
            assertEquals("gen2", assertConsistent("overwrite=$overwrite full publish", listOf(old, new)))
            for (j in 0 until new.size) {
                fresh(overwrite)
                val failing = backupControl()
                failing.onPublishStep = { step -> if (step == j) throw IOException("injected publish failure at $j") }
                val result = runCatching { runBlocking { failing.extractAndRegisterModuleArchive({ newZip.source() }) } }
                assertTrue(result.isFailure, "overwrite=$overwrite j=$j: the publish failure must surface")
                val state = assertConsistent("overwrite=$overwrite j=$j", listOf(old, new))
                println("MODULE-PUBLISH-INTERRUPTION overwrite=$overwrite j=$j -> $state")
                if (overwrite && j == 0) assertEquals("gen1", state, "nothing replaced yet: the old module must be restored")
            }
        }
    }

    /** A process kill skips finally: replay the exact disk snapshots through the next startup, not an exception path. */
    @Test fun startupRecoversEveryKilledArchiveStepWithoutOrphanFiles() {
        val old = generation(1)
        for (changedPath in listOf(false, true)) {
            val new = generation(2).map { (name, bytes) ->
                if (!changedPath) name to bytes
                else if (name.startsWith("mods.d/")) name to bytes.decodeToString()
                    .replace("modules/$INITIALS", "modules/relocated").encodeToByteArray()
                else name.replace("modules/$INITIALS", "modules/relocated") to bytes
            }
            val archive = zip(new, "crash.zip")
            for (overwrite in listOf(false, true)) {
                dest.deleteRecursively(); dest.mkdirs()
                if (overwrite) installGen1()
                val snapshots = mutableListOf<File>()
                fun capture() {
                    val copy = File(work, "snapshot-${snapshots.size}")
                    dest.copyRecursively(copy, overwrite = true)
                    snapshots += copy
                }
                val control = backupControl()
                control.onPublishStep = { capture() }
                runBlocking {
                    control.extractAndRegisterModuleArchive({ archive.source() }, new.size) { capture() }
                }
                capture() // completed install must survive recovery too
                for ((step, copy) in snapshots.withIndex()) {
                    dest.deleteRecursively()
                    copy.copyRecursively(dest, overwrite = true)
                    net.bible.sharedlogic.platform.host.HostSwordEnvironment.createModuleFolders(dest.absolutePath.toPath())
                    val label = "changedPath=$changedPath overwrite=$overwrite crash snapshot=$step"
                    val state = assertConsistent(label, listOf(old, new))
                    if (state == "absent") {
                        assertEquals(emptyList(), File(dest, "modules").walkTopDown().filter { it.isFile }.toList(),
                            "$label: absent module must not leave any data files")
                        assertEquals(emptyList(), File(dest, "mods.d").walkTopDown().filter { it.isFile }.toList(),
                            "$label: absent module must not leave any conf files")
                    }
                    copy.deleteRecursively()
                }
            }
        }
    }

    @Test fun interruptedRelocationCleansOldDataWhenStoreRootIsAnAlias() {
        dest.deleteRecursively()
        val realStore = File(work, "real-store").apply { mkdirs() }
        Files.createSymbolicLink(dest.toPath(), realStore.toPath())
        installGen1()
        val relocated = generation(2).map { (name, bytes) ->
            if (name.startsWith("mods.d/")) name to bytes.decodeToString()
                .replace("modules/$INITIALS", "modules/relocated").encodeToByteArray()
            else name.replace("modules/$INITIALS", "modules/relocated") to bytes
        }
        val archive = zip(relocated, "alias-relocation.zip")
        val control = backupControl()
        control.onPublishStep = { if (it == 1) throw IOException("injected publish interruption") }
        assertTrue(runCatching { runBlocking { control.extractAndRegisterModuleArchive({ archive.source() }) } }.isFailure)
        assertEquals(emptyList(), realStore.walkTopDown().filter { it.isFile }.toList(),
            "root alias must not prevent removing the old generation after publication starts")
    }

    @Test fun archiveCannotForgeRecoveryJournal() {
        val entries = generation(1) + (".sword-install-forged/publishing" to
            "[\"mods.d/keep.conf\",\"modules/keep/data\"]".encodeToByteArray())
        val archive = zip(entries, "forged.zip")
        val result = runCatching { runBlocking { backupControl().extractAndRegisterModuleArchive({ archive.source() }) } }
        assertTrue(result.isFailure, "ZIP entries must not claim the private transaction namespace")
        assertTrue(!File(dest, ".sword-install-forged").exists())
    }

    @Test fun replacingSharedDataWithoutAllOwnersIsRejectedBeforePublication() {
        installGen1()
        val sharedConf = File(dest, "mods.d/shared.conf")
        sharedConf.writeText(generation(1).first().second.decodeToString().replace("[$INITIALS]", "[SharedOwner]"))
        rescan()
        val shared = assertNotNull(Books.installed().getBook("SharedOwner"))
        try {
            val replacement = zip(generation(2), "shared-replacement.zip")
            val result = runCatching { runBlocking { backupControl().extractAndRegisterModuleArchive({ replacement.source() }) } }
            assertTrue(result.isFailure, "must reject replacing data still owned by a module outside the archive")
            assertTrue(matches(generation(1)), "rejection must preserve every byte of the installed generation")
            assertTrue(sharedConf.isFile, "unrelated owner's conf must remain")
            assertEquals("In the beginning God created the heaven and the earth.",
                shared.getRawText(shared.getKey("Gen.1.1")).trim())
        } finally {
            Books.installed().getBook("SharedOwner")?.let { Books.installed().removeBook(it) }
        }
    }

    @Test fun identicalSharedDataCanBeReinstalledWithoutTouchingItsOwner() {
        installGen1()
        val sharedConf = File(dest, "mods.d/shared.conf")
        sharedConf.writeText(generation(1).first().second.decodeToString().replace("[$INITIALS]", "[SharedOwner]"))
        rescan()
        assertNotNull(Books.installed().getBook("SharedOwner"))
        try {
            val archive = zip(generation(1), "identical-shared.zip")
            runBlocking { backupControl().extractAndRegisterModuleArchive({ archive.source() }) }
            assertTrue(matches(generation(1)))
            assertTrue(sharedConf.isFile)
            assertNotNull(Books.installed().getBook("SharedOwner"))
        } finally {
            Books.installed().getBook("SharedOwner")?.let { Books.installed().removeBook(it) }
        }
    }

    @Test fun replacementIncludingAllSharedDataOwnersCanPublish() {
        installGen1()
        val sharedConf = File(dest, "mods.d/shared.conf")
        sharedConf.writeText(generation(1).first().second.decodeToString().replace("[$INITIALS]", "[SharedOwner]"))
        rescan()
        assertNotNull(Books.installed().getBook("SharedOwner"))
        try {
            val new = generation(2)
            val entries = new + ("mods.d/shared.conf" to
                new.first().second.decodeToString().replace("[$INITIALS]", "[SharedOwner]").encodeToByteArray())
            val archive = zip(entries, "all-shared-owners.zip")
            runBlocking { backupControl().extractAndRegisterModuleArchive({ archive.source() }) }
            assertTrue(matches(new))
            assertNotNull(Books.installed().getBook("SharedOwner"))
        } finally {
            Books.installed().getBook("SharedOwner")?.let { Books.installed().removeBook(it) }
        }
    }

    private fun installGen1() = runBlocking { backupControl().extractAndRegisterModuleArchive({ zip(generation(1), "gen1.zip").source() }) }

    /** A staging directory left by a crashed EPUB install is swept; unrelated directories are not touched. */
    @Test fun leftoverEpubStagingDirectoriesAreSweptAtTheNextInstall() {
        val leftover = File(dest, ".epub-install-crashed/epub/x").apply { mkdirs() }
        File(leftover, "f.txt").writeText("stale")
        val unrelated = File(dest, "keep-me").apply { mkdirs() }
        installGen1()
        assertTrue(!File(dest, ".epub-install-crashed").exists(), "stale staging dir must be swept")
        assertTrue(unrelated.isDirectory, "only .epub-install-* dirs may be swept")
    }

    /** A bare directory entry `epub/foo/` must not create a live (empty) EPUB folder during extraction. */
    @Test fun epubDirectoryEntryDoesNotCreateALiveFolder() {
        val entries = generation(1) + ("epub/foo/" to ByteArray(0))
        runBlocking { backupControl().extractAndRegisterModuleArchive({ zip(entries, "dir.zip").source() }) }
        assertTrue(!File(dest, "epub/foo").exists(), "live epub/foo must not exist")
        assertNotNull(Books.installed().getBook(INITIALS))
    }

    /** One EPUB failing to register must not skip SWORD conf registration or leave staging/old trees behind. */
    @Test fun failingEpubDoesNotSkipSwordRegistration() {
        val entries = generation(1) + listOf("epub/bad.epub/optimize.lock" to byteArrayOf(1), "epub/bad.epub/a.txt" to byteArrayOf(2))
        val result = runCatching { runBlocking { backupControl().extractAndRegisterModuleArchive({ zip(entries, "bad.zip").source() }) } }
        assertTrue(result.isFailure, "the EPUB failure must surface")
        assertNotNull(Books.installed().getBook(INITIALS), "SWORD module must still be registered")
        assertTrue(!File(dest, "epub/bad.epub").exists())
        assertEquals(emptyList(), dest.listFiles()!!.filter { it.name.startsWith(".epub-") }.map { it.name })
    }

    /** EPUB data parts are not SWORD data: a later conf failure must still restore the old conf. */
    @Test fun epubPartsDoNotMarkSwordDataReplaced() {
        installGen1()
        val oldConf = File(dest, "mods.d/$INITIALS.conf").readBytes()
        val newConf = generation(2).first()
        val entries = listOf(newConf, "epub/p.epub/a.txt" to byteArrayOf(2))
        // Fail at publish step 0 and at step 1: whichever step is the conf's, a failure must restore the old conf.
        for (failAt in 0..1) {
            installGen1()
            val control = backupControl()
            control.onPublishStep = { if (it == failAt) throw IOException("injected publish failure at $it") }
            val result = runCatching { runBlocking { control.extractAndRegisterModuleArchive({ zip(entries, "mixed.zip").source() }) } }
            if (result.exceptionOrNull()?.message?.contains("injected") == true) {
                assertTrue(File(dest, "mods.d/$INITIALS.conf").readBytes().contentEquals(oldConf), "failAt=$failAt: old conf must be restored")
            }
        }
    }

    @Test fun cloudArchiveRoundTripPreservesEachGenerationAndUnrelatedModules() = runBlocking {
        val env = Proxy.newProxyInstance(AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java)) { _, method, _ ->
            when (method.name) {
                "getTmpDir" -> work.absolutePath
                "getVersionNumber" -> 1L
                else -> error("unexpected environment call: ${method.name}")
            }
        } as AppEnvironment
        val control = backupControl(env)
        val unrelated = File(dest, "modules/unrelated/keep.bin").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(9, 8, 7)) }
        for (generationNumber in 1..3) {
            val entries = generation(1).map { (name, bytes) ->
                if (name.startsWith("mods.d/")) name to (bytes + "\nVersion=$generationNumber.0\n".toByteArray())
                else name to (bytes + ByteArray(generationNumber) { generationNumber.toByte() })
            }
            control.extractAndRegisterModuleArchive({ zip(entries, "input.zip").source() })
            val metadata = SwordBookMetaData(
                File(dest, "mods.d/$INITIALS.conf").absolutePath.toPath(), directoryKUri(dest.absolutePath.toPath()),
            )
            val book = SwordBook(metadata, NullBackend())
            val expectedPath = work.absolutePath.toPath() / "doc-$INITIALS.abmd.zip"
            File(expectedPath.toString()).writeText("stale archive must not be reused")
            val archive = DocumentArchiver.packageDocument(book, env, control)
            assertEquals(expectedPath, archive)
            val contents = ZipFile(archive.toString()).use { z ->
                z.entries().asSequence().filter { it.name != "AndBibleBackupManifest.json" }
                    .associate { entry -> entry.name to z.getInputStream(entry).use { it.readBytes().toList() } }
            }
            assertEquals(entries.associate { it.first to it.second.toList() }, contents, "generation $generationNumber")
            File(dest, "mods.d/$INITIALS.conf").delete()
            File(dest, "modules/$INITIALS").deleteRecursively()
            Books.installed().getBook(INITIALS)?.let { Books.installed().removeBook(it) }
            assertTrue(DocumentArchiver.installArchive(archive, INITIALS, control), "generation $generationNumber")
            assertTrue(matches(entries), "round trip must preserve every file of generation $generationNumber")
            assertEquals(listOf<Byte>(9, 8, 7), unrelated.readBytes().toList(), "unrelated module remains byte-identical")
        }
    }

    private companion object { const val INITIALS = "K4ZTextBook" }
}
