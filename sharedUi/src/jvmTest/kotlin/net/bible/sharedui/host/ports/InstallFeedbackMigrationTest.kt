package net.bible.sharedui.host.ports

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.control.backup.BackupControl
import net.bible.service.db.DatabaseContainer
import net.bible.service.format.SaxTextPolicy
import net.bible.service.installzip.*
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.epubInitials
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedlogic.platform.host.HostUserNotifier
import net.bible.sharedui.components.AppDialogHost
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedlogic.platform.AddonCatalog
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.assembly.StartupPhase
import net.bible.sharedui.desktop.assembly.DesktopAppStartup
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import okio.Path.Companion.toPath
import okio.source
import org.crosswire.common.util.CWProject
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class InstallFeedbackMigrationTest {
    @Test fun invalidArchiveReportsErrorAndNextEpubIsReadable() = runBlocking {
        assertNull(KoinPlatform.getKoinOrNull())
        val main = Executors.newSingleThreadExecutor { Thread(it, "install-feedback-main").apply { isDaemon = true } }.asCoroutineDispatcher()
        val root = Files.createTempDirectory("install-feedback")
        val previousHost = runCatching { DatabaseContainer.host }.getOrNull()
        fun field(name: String) = (runCatching { CWProject::class.java.getDeclaredField(name) }.getOrNull()
            ?: CWProject.Companion::class.java.getDeclaredField(name)).apply { isAccessible = true }
        fun owner(f: java.lang.reflect.Field): Any? = if (java.lang.reflect.Modifier.isStatic(f.modifiers)) null else CWProject.Companion
        val fields = listOf("homeProperty", "homeDirectory", "homeAltDirectory").map(::field)
        val values = fields.map { it.get(owner(it)) }
        val instanceField = field("instance")
        val instance = instanceField.get(owner(instanceField))
        val established = CWProject::class.java.getDeclaredField("writeHome").apply { isAccessible = true }.get(instance) != null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        Dispatchers.setMain(main)
        try {
            val paths = DesktopPaths({ key -> when (key) {
                "XDG_DATA_HOME" -> root.resolve("data").toString()
                "XDG_CONFIG_HOME" -> root.resolve("config").toString()
                "XDG_CACHE_HOME" -> root.resolve("cache").toString()
                else -> null
            } }, "Linux", root.resolve("home"))
            val window = object : DesktopWindowHandle {
                override val sizeDp = IntSize(1200, 800)
                override val density = 1f
                override fun toFront() = Unit
            }
            assertEquals(StartupPhase.Ready, DesktopAppStartup.ensureStarted(paths, window, "en", repositories = { PropertyMap() }))
            val koin = KoinPlatform.getKoin()
            val strings = koin.get<CoreStrings>()
            val infos = CopyOnWriteArrayList<String>()
            val phases = CopyOnWriteArrayList<InstallPhase>()
            val changes = AtomicInteger()
            val dialogs = AppDialogController()
            val notifier = HostUserNotifier(dialogs, onInfo = { message, _ -> infos += message })
            val feedback = HostInstallTerminalFeedback({ strings }, { notifier }, { changes.incrementAndGet() })
            var nextId = 0
            val activity = koin.get<net.bible.android.control.activity.ActivityCenter>()
            val zipAdapter = net.bible.android.control.activity.ZipInstallActivityAdapter({ activity }, {})
            val terminal = installTerminalHandler(feedback, zipAdapter) { strings }
            val controller = createIosInstallController(
                scope = scope,
                initialise = {},
                runnerFactory = {
                    InstallJobRunner(InstallInspector(::determineLocalFileType), DefaultInstallCommitter(
                        koin.get<BackupControl>(), strings, koin.get<AppEnvironment>(), { koin.get<AddonCatalog>().clearCaches() },
                    ))
                },
                newJobId = { (++nextId).toString() },
                onTerminal = { id, phase -> phases += phase; terminal(id, phase) },
            )
            val deps = InstallServiceController.JobDeps(
                acquireDir = root.resolve("acquired").toString().toPath(),
                openStream = { source ->
                    val file = java.io.File(java.net.URI(source.uri))
                    InstallStream(file.source(), file.length())
                },
                isStudyPadExport = { false }, studyPadStats = { error("unexpected StudyPad") },
                swordZipScan = ::scanSwordZip, epubUpgradeCheck = { false },
            )
            val bad = root.resolve("invalid.zip")
            Files.write(bad, byteArrayOf(0x50, 0x4b, 3, 4, 0))
            val epub = root.resolve("f-runtime.epub")
            val entries = listOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">f-runtime-fixed</dc:identifier><dc:title>F Runtime Fixture</dc:title><dc:creator>AndBible tests</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/><item id="one" href="ch1.xhtml" media-type="application/xhtml+xml"/><item id="two" href="ch2.xhtml" media-type="application/xhtml+xml"/></manifest><spine toc="ncx"><itemref idref="one"/><itemref idref="two"/></spine></package>""",
                "toc.ncx" to """<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head/><docTitle><text>F Runtime Fixture</text></docTitle><navMap><navPoint id="one" playOrder="1"><navLabel><text>One</text></navLabel><content src="ch1.xhtml"/></navPoint><navPoint id="two" playOrder="2"><navLabel><text>Two</text></navLabel><content src="ch2.xhtml"/></navPoint></navMap></ncx>""",
                "ch1.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One</title></head><body><p>F runtime chapter one proof.</p></body></html>""",
                "ch2.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Two</title></head><body><p>F runtime chapter two proof.</p></body></html>""",
            )
            ZipOutputStream(Files.newOutputStream(epub)).use { zip ->
                entries.forEach { (name, content) -> zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry() }
            }
            val starter = HostDocumentInstallStarter({ controller }, { deps }, scope)
            starter.startAndAwait(listOf(DocumentRef(bad.toUri().toString())), null)
            assertEquals(listOf<InstallPhase>(InstallPhase.Error(InstallError.INVALID_FILE, "invalid.zip")), phases)
            // The failed zip also reaches the activity list (#4007), as "<name>: <message>".
            assertEquals(
                listOf("File does not contain a valid SWORD module"),
                activity.items.value.filter { it.state == net.bible.android.control.activity.ActivityState.FAILED }
                    .map { it.message!!.substringAfter(": ") },
            )
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                        AbTheme(darkTheme = false) {
                            val pending by dialogs.pending.collectAsState()
                            AppDialogHost(pending, null, dialogs::respond, {}, {}, {})
                        }
                    }
                }
                onNodeWithText("File does not contain a valid SWORD module").assertExists()
                onNodeWithText("OK").performClick()
                onNodeWithText("File does not contain a valid SWORD module").assertDoesNotExist()
                runOnIdle { assertNull(dialogs.pending.value) }
            }
            assertTrue(controller.jobs.value.isEmpty())
            assertNull(Books.installed().getBook(epubInitials("f-runtime.epub")))
            starter.startAndAwait(listOf(DocumentRef(epub.toUri().toString())), null)
            assertEquals(InstallPhase.Done, phases.last())
            assertEquals(listOf(strings.installZipSuccessful), infos)
            assertEquals(2, changes.get())
            val book = assertNotNull(Books.installed().getBook(epubInitials("f-runtime.epub")))
            val text = book.getGlobalKeyList().joinToString("\n") { key ->
                SwordContentFacade.getCanonicalText(book, key, strings = strings, saxTextPolicy = koin.get<SaxTextPolicy>())
            }
            assertTrue(text.contains("F runtime chapter one proof."))
            assertTrue(text.contains("F runtime chapter two proof."))
        } finally {
            scope.cancel()
            DesktopAppStartup.resetForTest()
            previousHost?.let { DatabaseContainer.installHost(it) }
            fields.zip(values).forEach { (f, value) -> f.set(owner(f), value) }
            if (established) instanceField.set(owner(instanceField), instance) else {
                val reset = runCatching { CWProject::class.java.getDeclaredMethod("reset") }.getOrElse { CWProject.Companion::class.java.getDeclaredMethod("reset") }
                reset.isAccessible = true
                reset.invoke(if (java.lang.reflect.Modifier.isStatic(reset.modifiers)) null else CWProject.Companion)
            }
            root.toFile().deleteRecursively()
            Dispatchers.resetMain()
            main.close()
        }
    }
}
