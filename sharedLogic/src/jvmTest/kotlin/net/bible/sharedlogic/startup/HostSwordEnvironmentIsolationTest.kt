package net.bible.sharedlogic.startup

import net.bible.sharedlogic.platform.host.HostSwordEnvironment
import okio.Path.Companion.toOkioPath
import org.crosswire.common.util.CWProject
import org.crosswire.jsword.book.sword.SwordBookPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostSwordEnvironmentIsolationTest {
    @Test fun ownedModuleFilesStayInPlaceAndResetDoesNotRediscoverThem() {
        val root = Files.createTempDirectory("host-sword-isolation")
        val modules = root.resolve("Documents/sword")
        val loaded = HostSwordEnvironment::class.java.getDeclaredField("loaded").apply { isAccessible = true }
        val previousLoaded = loaded.getBoolean(HostSwordEnvironment)
        val previousScan = SwordBookPath.scanDefaultLocations
        val downloadField = SwordBookPath::class.java.getDeclaredField("overrideDownloadDir").apply { isAccessible = true }
        val previousDownloadOverride = downloadField.get(null)
        // Initialize the lazy default under the original home, before the temporary fixture replaces it.
        SwordBookPath.getSwordDownloadDir()
        val previousAugment = SwordBookPath.getAugmentPath()
        val properties = listOf("user.home", "jsword.home").associateWith(System::getProperty)
        val projectFields = listOf("instance", "homeProperty", "homeDirectory", "homeAltDirectory").associate { name ->
            CWProject::class.java.getDeclaredField(name).apply { isAccessible = true }.let { it to it.get(null) }
        }
        val resetProject = CWProject.Companion::class.java.getDeclaredMethod("reset").apply { isAccessible = true }
        try {
            System.setProperty("user.home", root.toString())
            System.clearProperty("jsword.home")
            resetProject.invoke(CWProject.Companion)
            Files.createDirectories(modules.resolve("mods.d"))
            Files.createDirectories(modules.resolve("modules/test"))
            Files.writeString(modules.resolve("mods.d/owned.conf"), "[Owned]\nModDrv=RawText\nDataPath=./modules/test/\n")
            Files.writeString(modules.resolve("modules/test/payload"), "module data")
            loaded.setBoolean(HostSwordEnvironment, false)
            SwordBookPath.scanDefaultLocations = true
            // Avoid initializing JSword's process-wide lazy default download path with this temporary home.
            SwordBookPath.setDownloadDir(modules.toOkioPath())
            HostSwordEnvironment.initialiseJSwordFolders(modules.toOkioPath(), rethrowFailures = true)

            assertTrue(Files.exists(modules.resolve("mods.d/owned.conf")), "startup must not relocate app-owned modules")
            assertFalse(Files.exists(root.resolve(".sword/mods.d/owned.conf")))
            assertEquals(listOf(modules.toOkioPath()), SwordBookPath.getSwordPath().toList())

            modules.toFile().deleteRecursively()
            loaded.setBoolean(HostSwordEnvironment, false)
            HostSwordEnvironment.initialiseJSwordFolders(modules.toOkioPath(), rethrowFailures = true)
            assertTrue(SwordBookPath.getSwordPath().all { path -> SwordBookPath.getBookList(path).orEmpty().isEmpty() },
                "a fresh fixture must not rediscover modules outside the app-owned directory")
        } finally {
            loaded.setBoolean(HostSwordEnvironment, previousLoaded)
            projectFields.forEach { (field, value) -> field.set(null, value) }
            properties.forEach { (name, value) -> if (value == null) System.clearProperty(name) else System.setProperty(name, value) }
            downloadField.set(null, previousDownloadOverride)
            SwordBookPath.scanDefaultLocations = previousScan
            SwordBookPath.setAugmentPath(previousAugment)
            root.toFile().deleteRecursively()
        }
    }
}
