package net.bible.sharedlogic.startup

import net.bible.sharedlogic.platform.host.HostSwordEnvironment
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HostSwordEnvironmentFailureTest {
    @Test fun failedFolderSetupIsToleratedByDefaultAsOnIos() {
        val loaded = HostSwordEnvironment::class.java.getDeclaredField("loaded").apply { isAccessible = true }
        val previous = loaded.getBoolean(HostSwordEnvironment)
        val root = Files.createTempDirectory("sword-host-tolerant")
        try {
            loaded.setBoolean(HostSwordEnvironment, false)
            val file = Files.writeString(root.resolve("not-a-folder"), "occupied")
            HostSwordEnvironment.initialiseJSwordFolders(file.toOkioPath())
            assertFalse(HostSwordEnvironment.isLoaded)
        } finally {
            loaded.setBoolean(HostSwordEnvironment, previous)
            root.toFile().deleteRecursively()
        }
    }

    @Test fun failedFolderSetupPropagatesToTheHostWithoutMarkingLoaded() {
        val loaded = HostSwordEnvironment::class.java.getDeclaredField("loaded").apply { isAccessible = true }
        val previous = loaded.getBoolean(HostSwordEnvironment)
        val root = Files.createTempDirectory("sword-host-failure")
        try {
            loaded.setBoolean(HostSwordEnvironment, false)
            val file = Files.writeString(root.resolve("not-a-folder"), "occupied")
            assertFailsWith<Exception> { HostSwordEnvironment.initialiseJSwordFolders(file.toOkioPath(), rethrowFailures = true) }
            assertFalse(HostSwordEnvironment.isLoaded)
        } finally {
            loaded.setBoolean(HostSwordEnvironment, previous)
            root.toFile().deleteRecursively()
        }
    }
}
