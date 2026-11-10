package net.bible.service.sword.mybible

import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.sword.SqliteSwordDriver
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedlogic.platform.ReadOnlyModuleDbOpener
import net.bible.sharedlogic.platform.host.HostReadOnlyModuleDbOpener
import okio.Path.Companion.toPath
import org.crosswire.common.activate.Activator
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class MyBibleVersificationLifecycleTest {
    @Test
    fun firstStartupPreservesExistingGermanModuleAsKjvaAcrossRestarts() = withModules { root ->
        fixture(root)
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        assertEquals("KJVA", installed().versification.name)
        restart()
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        assertEquals("KJVA", installed().versification.name)
    }

    @Test
    fun migrationDoesNotReadLegacyDatabaseToChooseItsNumbering() = withModules { root ->
        // Even an unreadable legacy module must be pinned before metadata can be loaded.
        val file = root.resolve("modules/mybible/Lifecycle.SQLite3").apply { parentFile.mkdirs(); writeText("not SQLite") }
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        file.delete()
        fixture(root)
        addMyBibleBook(file.path.toPath())
        assertEquals("KJVA", installed().versification.name)
    }

    @Test
    fun emptyFirstStartupCompletesMigrationAndNewGermanImportKeepsDecisionAfterRestart() = withModules { root ->
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        val file = fixture(root)
        addMyBibleBook(file.path.toPath())
        assertEquals("German", installed().versification.name)
        restart()
        // Change the content without removing the installation: a stored decision must win over detection.
        file.delete()
        fixture(root, german = false)
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        assertEquals("German", installed().versification.name)
    }

    @Test
    fun deletingLegacyModuleClearsDecisionSoReimportDetectsGerman() = withModules { root ->
        fixture(root)
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        assertEquals("KJVA", installed().versification.name)
        val book = installed()
        close(book)
        SqliteSwordDriver().delete(book)
        val file = fixture(root)
        addMyBibleBook(file.path.toPath())
        assertEquals("German", installed().versification.name)
    }

    @Test
    fun secondStartupDoesNotMigrateAFreshlyDiscoveredModule() = withModules { root ->
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        restart()
        fixture(root)
        addManuallyInstalledMyBibleBooks(startupDiscovery = true)
        assertEquals("German", installed().versification.name)
    }

    private fun fixture(root: File, german: Boolean = true): File {
        val file = root.resolve("modules/mybible/Lifecycle.SQLite3").apply { parentFile.mkdirs() }
        openSqlite(file.path).use { db ->
            db.exec("CREATE TABLE info (name TEXT, value TEXT)")
            db.exec("INSERT INTO info VALUES ('language', 'de')")
            db.exec("CREATE TABLE verses (book_number INTEGER, chapter INTEGER, verse INTEGER, text TEXT)")
            if (german) {
                javaClass.getResourceAsStream("/mybible/elb06-chapter-bounds.txt")!!.bufferedReader().useLines { lines ->
                    lines.filterNot { it.startsWith("#") || it.isBlank() }.forEach { line ->
                        val (book, counts) = line.split(":")
                        counts.split(",").forEachIndexed { chapter, verse ->
                            db.exec("INSERT INTO verses VALUES ($book, ${chapter + 1}, $verse, 'Chapter end')")
                        }
                    }
                }
            } else {
                listOf(14, 17, 18, 6).forEachIndexed { chapter, verse ->
                    db.exec("INSERT INTO verses VALUES (460, ${chapter + 1}, $verse, 'Chapter end')")
                }
            }
        }
        return file
    }

    private fun installed() = Books.installed().getBook("MyBible-Lifecycle") as SwordBook

    private fun close(book: SwordBook) {
        Activator.deactivate(book)
        (book.backend as SqliteBackend).state.close()
    }

    private fun restart() {
        (Books.installed().getBook("MyBible-Lifecycle") as? SwordBook)?.let {
            close(it)
            Books.installed().removeBook(it)
        }
        stopKoin()
        start()
    }

    private lateinit var root: File
    private fun start() {
        val dirs = object : AppDirectories {
            override val internalFilesDir = root.resolve("internal").path.toPath()
            override val modulesDir = root.resolve("modules").path.toPath()
            override val manualInstallDir = root.resolve("manual").path.toPath()
            override val manualInstallDir2 = root.resolve("manual2").path.toPath()
            override val manualReadingPlanDir = root.resolve("plans").path.toPath()
        }
        startKoin { modules(module {
            single<AppDirectories> { dirs }
            single<ReadOnlyModuleDbOpener> { HostReadOnlyModuleDbOpener() }
        }) }
    }

    private fun withModules(check: (File) -> Unit) {
        root = Files.createTempDirectory("mybible-lifecycle").toFile()
        start()
        try { check(root) } finally {
            (Books.installed().getBook("MyBible-Lifecycle") as? SwordBook)?.let {
                close(it)
                Books.installed().removeBook(it)
            }
            stopKoin()
            root.deleteRecursively()
        }
    }
}
