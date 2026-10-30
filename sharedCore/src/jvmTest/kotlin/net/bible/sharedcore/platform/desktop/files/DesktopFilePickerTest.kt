package net.bible.sharedcore.platform.desktop.files

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import java.nio.file.Path
import java.nio.file.Files
import okio.Buffer

class DesktopFilePickerTest {
    @Test fun discoveryLinkageFailureFallsBackToAwt() {
        val dbus = object : net.bible.sharedcore.platform.desktop.dbus.DesktopDbus() {
            override fun portalAvailable(): Boolean = throw NoClassDefFoundError("UnixSystem")
        }
        kotlin.test.assertIs<AwtFileChooserBackend>(desktopFileChooser(dbus))
    }

    @Test
    fun cancelReturnsCancelled() = runTest {
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
        }
        assertNull(DesktopFilePicker(chooser).pickBackupFile())
    }

    @Test
    fun coroutineCancellationIsNotSwallowed() = runTest {
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? =
                throw kotlinx.coroutines.CancellationException("cancelled")
            override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
        }
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            DesktopFilePicker(chooser).pickBackupFile()
        }
    }

    @Test
    fun portalPathsAreUsedVerbatim() = runTest {
        val path = Files.createTempFile("portal\\name", ".abk")
        try {
            Files.writeString(path, "backup")
            val chooser = object : FileChooserBackend {
                override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean) = listOf(path)
                override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
            }
            val picker = DesktopFilePicker(chooser)
            val picked = picker.pickBackupFile()!!
            assertEquals(path.toString(), picked.token)
            val buffer = Buffer()
            picker.openInput(picked).use { it.read(buffer, 100) }
            assertEquals("backup", buffer.readUtf8())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test fun fileDialogsUseAwtOnWindows() {
        var connects = 0
        val dbus = object : net.bible.sharedcore.platform.desktop.dbus.DesktopDbus("Windows 11") {
            override fun connect(): org.freedesktop.dbus.connections.impl.DBusConnection { connects++; error("no D-Bus on Windows") }
        }
        kotlin.test.assertIs<AwtFileChooserBackend>(desktopFileChooser(dbus))
        assertEquals(0, connects, "no D-Bus portal attempt on Windows")
    }
}
