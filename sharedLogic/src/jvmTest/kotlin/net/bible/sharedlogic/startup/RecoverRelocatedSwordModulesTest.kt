package net.bible.sharedlogic.startup

import net.bible.sharedlogic.platform.host.recoverRelocatedSwordModules
import okio.Path.Companion.toOkioPath
import okio.Path
import okio.FileSystem
import okio.ForwardingFileSystem
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoverRelocatedSwordModulesTest {
    @Test
    fun movesSourceTreeWhenDestinationDoesNotExist() = withTempRoot { root ->
        val source = root.resolve("home/.sword")
        val destination = root.resolve("Documents/sword")
        Files.createDirectories(source.resolve("mods.d"))
        Files.writeString(source.resolve("mods.d/module.conf"), "module config")
        Files.createDirectories(source.resolve("modules/texts"))
        Files.writeString(source.resolve("modules/texts/module.dat"), "module data")
        val messages = mutableListOf<String>()

        recover(source.toOkioPath(), destination.toOkioPath(), log = messages::add)

        assertEquals("module config", Files.readString(destination.resolve("mods.d/module.conf")))
        assertEquals("module data", Files.readString(destination.resolve("modules/texts/module.dat")))
        assertFalse(Files.exists(source.resolve("mods.d/module.conf")))
        assertEquals(1, messages.size)
    }

    @Test
    fun existingDestinationPayloadPreventsMovingAnySourceFiles() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        Files.createDirectories(source.resolve("modules"))
        Files.createDirectories(destination.resolve("mods.d"))
        Files.writeString(source.resolve("modules/old.dat"), "old")
        Files.writeString(destination.resolve("mods.d/current.conf"), "current")

        recover(source.toOkioPath(), destination.toOkioPath())

        assertEquals("old", Files.readString(source.resolve("modules/old.dat")))
        assertFalse(Files.exists(destination.resolve("modules/old.dat")))
        assertEquals("current", Files.readString(destination.resolve("mods.d/current.conf")))
    }

    @Test
    fun emptyDestinationDirectorySkeletonAllowsWholeStoreMove() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        Files.createDirectories(source.resolve("modules/test"))
        Files.writeString(source.resolve("modules/test/data"), "module data")
        Files.createDirectories(destination.resolve("mods.d"))
        Files.createDirectories(destination.resolve("modules"))

        recover(source.toOkioPath(), destination.toOkioPath())

        assertEquals("module data", Files.readString(destination.resolve("modules/test/data")))
        assertFalse(Files.exists(source))
    }

    @Test
    fun preservesDifferingCollisionsAtBothLocations() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        Files.createDirectories(source.resolve("modules"))
        Files.createDirectories(destination.resolve("modules"))
        Files.writeString(source.resolve("modules/module.dat"), "source copy")
        Files.writeString(destination.resolve("modules/module.dat"), "destination copy")

        recover(source.toOkioPath(), destination.toOkioPath())

        assertEquals("source copy", Files.readString(source.resolve("modules/module.dat")))
        assertEquals("destination copy", Files.readString(destination.resolve("modules/module.dat")))
    }

    @Test
    fun sameModuleWithDisjointConfigAndDataStillSkipsWholeSourceTree() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        Files.createDirectories(source.resolve("mods.d"))
        Files.createDirectories(source.resolve("modules/test"))
        Files.createDirectories(destination.resolve("mods.d"))
        Files.createDirectories(destination.resolve("modules/test"))
        Files.writeString(source.resolve("mods.d/test.conf"), "old config")
        Files.writeString(source.resolve("modules/test/data"), "old data")
        Files.writeString(source.resolve("modules/disjoint.dat"), "unrelated old module")
        Files.writeString(destination.resolve("modules/test/index"), "new index")

        recover(source.toOkioPath(), destination.toOkioPath())

        assertEquals("old config", Files.readString(source.resolve("mods.d/test.conf")))
        assertEquals("old data", Files.readString(source.resolve("modules/test/data")))
        assertEquals("unrelated old module", Files.readString(source.resolve("modules/disjoint.dat")))
        assertFalse(Files.exists(destination.resolve("modules/disjoint.dat")))
    }

    @Test
    fun failedMovePropagatesAndRetryCompletesWithoutLosingSourceBytes() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        Files.createDirectories(source.resolve("mods.d"))
        Files.createDirectories(source.resolve("modules/test"))
        Files.writeString(source.resolve("mods.d/test.conf"), "config bytes")
        Files.writeString(source.resolve("modules/test/index"), "index bytes")
        Files.writeString(source.resolve("modules/test/data"), "data bytes")
        val sourcePath = source.toOkioPath()
        val destinationPath = destination.toOkioPath()
        var moves = 0
        val failingFiles = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(source: Path, target: Path) {
                moves++
                if (moves == 1) throw IOException("simulated move failure")
                super.atomicMove(source, target)
            }
        }

        kotlin.test.assertFailsWith<IOException> {
            recoverRelocatedSwordModules(sourcePath, destinationPath, failingFiles)
        }

        assertEquals("config bytes", Files.readString(source.resolve("mods.d/test.conf")))
        assertEquals("index bytes", Files.readString(source.resolve("modules/test/index")))
        assertEquals("data bytes", Files.readString(source.resolve("modules/test/data")))
        assertFalse(Files.exists(destination))

        recover(sourcePath, destinationPath)

        assertEquals("config bytes", Files.readString(destination.resolve("mods.d/test.conf")))
        assertEquals("index bytes", Files.readString(destination.resolve("modules/test/index")))
        assertEquals("data bytes", Files.readString(destination.resolve("modules/test/data")))
        assertFalse(Files.exists(source))
    }

    @Test
    fun repeatedRecoveryDoesNotDuplicateOrLogPreviouslyMovedFiles() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        Files.createDirectories(source.resolve("mods.d"))
        Files.writeString(source.resolve("mods.d/module.conf"), "config")
        val messages = mutableListOf<String>()

        repeat(2) { recover(source.toOkioPath(), destination.toOkioPath(), log = messages::add) }

        assertEquals("config", Files.readString(destination.resolve("mods.d/module.conf")))
        assertFalse(Files.exists(source.resolve("mods.d/module.conf")))
        assertEquals(1, messages.size)
    }

    @Test
    fun missingSourceIsANoOp() = withTempRoot { root ->
        val destination = root.resolve("new")
        val messages = mutableListOf<String>()

        recover(root.resolve("missing").toOkioPath(), destination.toOkioPath(), log = messages::add)

        assertFalse(Files.exists(destination))
        assertTrue(messages.isEmpty())
    }

    @Test
    fun doesNotFollowSymbolicLinksOutOfSourceTree() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        val outside = root.resolve("outside")
        Files.createDirectories(source)
        Files.createDirectories(outside)
        Files.writeString(source.resolve("module.dat"), "module data")
        Files.writeString(outside.resolve("private.dat"), "outside data")
        Files.createSymbolicLink(source.resolve("linked"), outside)

        recover(source.toOkioPath(), destination.toOkioPath())

        assertEquals("outside data", Files.readString(outside.resolve("private.dat")))
        assertEquals("module data", Files.readString(source.resolve("module.dat")))
        assertFalse(Files.exists(destination))
    }

    @Test
    fun doesNotWriteThroughSymbolicLinkDestinationDirectory() = withTempRoot { root ->
        val source = root.resolve("old")
        val destination = root.resolve("new")
        val outside = root.resolve("outside")
        Files.createDirectories(source.resolve("linked"))
        Files.createDirectories(outside)
        Files.writeString(source.resolve("linked/module.dat"), "module data")
        Files.createSymbolicLink(destination, outside)

        recover(source.toOkioPath(), destination.toOkioPath())

        assertFalse(Files.exists(outside.resolve("linked/module.dat")))
        assertEquals("module data", Files.readString(source.resolve("linked/module.dat")))
    }

    @Test
    fun doesNotWriteThroughSymbolicLinkInDestinationParents() = withTempRoot { root ->
        val source = root.resolve("old")
        val outside = root.resolve("outside")
        val destinationParent = root.resolve("app-data")
        Files.createDirectories(source)
        Files.createDirectories(outside)
        Files.writeString(source.resolve("module.dat"), "module data")
        Files.createSymbolicLink(destinationParent, outside)

        recover(source.toOkioPath(), destinationParent.resolve(".sword").toOkioPath())

        assertFalse(Files.exists(outside.resolve(".sword/module.dat")))
        assertEquals("module data", Files.readString(source.resolve("module.dat")))
    }

    private fun recover(source: Path, destination: Path, log: (String) -> Unit = {}) =
        recoverRelocatedSwordModules(source, destination, FileSystem.SYSTEM, log)

    private fun withTempRoot(block: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("relocated-sword-modules")
        try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
