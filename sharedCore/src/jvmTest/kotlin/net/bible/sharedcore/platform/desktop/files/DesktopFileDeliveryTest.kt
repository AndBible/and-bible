package net.bible.sharedcore.platform.desktop.files

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.SaveOrShare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopFileDeliveryTest {
    @Test
    fun cancelReturnsCancelled() = runTest {
        val source = Files.createTempFile("delivery-test", ".abk")
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>): Path? = null
        }
        try {
            val result = DesktopFileDelivery(chooser).deliver(
                source.toString(), "backup.abk", SaveOrShare.SAVE,
                "application/zip", "application/zip", null, null, "Save"
            )
            assertEquals(DeliveryResult.CANCELLED, result)
        } finally {
            Files.deleteIfExists(source)
        }
    }

    @Test
    fun successfulSaveReplacesTarget() = runTest {
        val directory = Files.createTempDirectory("delivery-test")
        val source = directory.resolve("source.abk")
        val target = directory.resolve("target.abk")
        Files.writeString(source, "new backup")
        Files.writeString(target, "old backup")
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>) = target
        }
        try {
            val result = DesktopFileDelivery(chooser).deliver(
                source.toString(), "backup.abk", SaveOrShare.SAVE,
                "application/zip", "application/zip", null, null, "Save"
            )
            assertEquals(DeliveryResult.DELIVERED, result)
            assertEquals("new backup", Files.readString(target))
            assertFalse(Files.exists(directory.resolve("target.abk.part")))
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(target)
            Files.deleteIfExists(directory.resolve("target.abk.part"))
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun existingPartialFileIsNotOverwritten() = runTest {
        val directory = Files.createTempDirectory("delivery-test")
        val source = directory.resolve("source")
        val target = directory.resolve("target")
        val part = directory.resolve("target.part")
        Files.writeString(source, "new backup")
        Files.writeString(part, "other operation")
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>) = target
        }
        try {
            assertEquals(DeliveryResult.FAILED, DesktopFileDelivery(chooser).deliver(
                source.toString(), "backup.abk", SaveOrShare.SAVE, "application/zip", "application/zip", null, null, "Save"))
            assertEquals("other operation", Files.readString(part))
            assertFalse(Files.exists(target))
        } finally {
            Files.deleteIfExists(source); Files.deleteIfExists(part); Files.deleteIfExists(target); Files.deleteIfExists(directory)
        }
    }

    @Test
    fun failedWriteLeavesNoPartialFile() = runTest {
        val directory = Files.createTempDirectory("delivery-test")
        val target = directory.resolve("backup.abk")
        val chooser = object : FileChooserBackend {
            override suspend fun open(filters: List<FileFilter>, multiple: Boolean, directory: Boolean): List<Path>? = null
            override suspend fun save(suggestedName: String, filters: List<FileFilter>) = target
        }
        try {
            val result = DesktopFileDelivery(chooser).deliver(
                directory.resolve("missing").toString(), "backup.abk", SaveOrShare.SAVE,
                "application/zip", "application/zip", null, null, "Save"
            )
            assertEquals(DeliveryResult.FAILED, result)
            assertFalse(Files.exists(target))
            assertFalse(Files.exists(directory.resolve("backup.abk.part")))
        } finally {
            Files.deleteIfExists(target)
            Files.deleteIfExists(directory.resolve("backup.abk.part"))
            Files.deleteIfExists(directory)
        }
    }
}
