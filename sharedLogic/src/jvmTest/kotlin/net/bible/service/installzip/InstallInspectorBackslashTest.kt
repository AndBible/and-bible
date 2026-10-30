package net.bible.service.installzip

import kotlinx.coroutines.runBlocking
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.page.window.WindowControl
import net.bible.service.cloudsync.CloudSync
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.source
import org.crosswire.common.util.CWProject
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * D6 Task 4: a module zip written by a Windows tool may carry `\` in its entry names (`mods.d\kjv.conf`). The scan
 * ([scanSwordZip], which feeds [InstallInspector]) must classify it as a SWORD zip and
 * [BackupControl.extractAndRegisterModuleArchive] must write the files at the normalised paths; a `..\` traversal
 * must be refused by both.
 */
class InstallInspectorBackslashTest {
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
        tempHome = Files.createTempDirectory("backslash-home").toFile()
        System.setProperty("jsword.home", tempHome.absolutePath)
        CWProject::class.java.getDeclaredMethod("reset").apply { isAccessible = true }.invoke(null)
        CWProject.instance().setFrontendName("jsword-test")
        savedDownloadDir = SwordBookPathJvm.getSwordDownloadDir()
        work = Files.createTempDirectory("backslash-work").toFile()
        dest = File(work, "modules").apply { mkdirs() }
        runCatching { stopKoin() }
        startKoin { modules(module { single<AppDirectories> { TestDirs(dest.absolutePath.toPath()) } }) }
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

    /** The real fixture module, with every entry name written using `\` separators. */
    private fun fixtureEntries(): List<Pair<String, ByteArray>> {
        val root = fixtureRoot()
        val data = File(root, "modules/$INITIALS").listFiles()!!.sortedBy { it.name }
        return listOf("mods.d\\$INITIALS.conf" to File(root, "mods.d/$INITIALS.conf").readBytes()) +
            data.map { "modules\\$INITIALS\\${it.name}" to it.readBytes() }
    }

    private fun zip(entries: List<Pair<String, ByteArray>>): File {
        val file = File(work, "m.zip")
        ZipOutputStream(file.outputStream()).use { z ->
            for ((name, body) in entries) { z.putNextEntry(ZipEntry(name)); z.write(body); z.closeEntry() }
        }
        return file
    }

    @Test fun backslashEntryNamesInstall() {
        val archive = zip(fixtureEntries())
        val scan = scanSwordZip(archive.absolutePath.toPath())
        assertFalse(scan.invalid, "a zip with backslash entry names must classify as a SWORD zip")
        assertFalse(scan.isEpub)
        assertEquals(fixtureEntries().size, scan.totalEntries)

        runBlocking { backupControl().extractAndRegisterModuleArchive({ archive.source() }, scan.totalEntries) }

        val root = fixtureRoot()
        assertContentEquals(File(root, "mods.d/$INITIALS.conf").readBytes(), File(dest, "mods.d/$INITIALS.conf").readBytes())
        for (f in File(root, "modules/$INITIALS").listFiles()!!) {
            val installed = File(dest, "modules/$INITIALS/${f.name}")
            assertTrue(installed.isFile, "missing ${installed.path}")
            assertContentEquals(f.readBytes(), installed.readBytes())
        }
        assertFalse(dest.walkTopDown().any { '\\' in it.name }, "no file name may contain a literal backslash")
    }

    @Test fun backslashTraversalRefused() {
        val archive = zip(fixtureEntries() + ("..\\..\\evil" to "x".toByteArray()))
        assertTrue(scanSwordZip(archive.absolutePath.toPath()).invalid, "scan must refuse ..\\ traversal")
        assertFailsWith<IOException> {
            runBlocking { backupControl().extractAndRegisterModuleArchive({ archive.source() }) }
        }
        assertFalse(File(work, "evil").exists())
        assertFalse(File(work.parentFile, "evil").exists())
    }

    private companion object {
        const val INITIALS = "K4ZTextBook"
    }
}
