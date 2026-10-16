package net.bible.android.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Temporary source gate until the move set compiles as common Kotlin. Not a full dependency analysis. */
class SharedDataReadinessTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test fun everyMoveSetFileExists() {
        assertEquals(emptyList<String>(), MOVE_SET.filterNot { File(root, it).isFile })
        val databaseFiles = File(root, DATABASE_DIRECTORY).walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        assertEquals("New database sources must be added explicitly", databaseFiles,
            MOVE_SET.filter { it.startsWith("$DATABASE_DIRECTORY/") }.toSet())
    }

    @Test fun noForbiddenImportsBeyondAllowList() {
        val sources = MOVE_SET.associateWith { File(root, it).readText() }
        val declarations = sources.values.flatMap { declarations(it) }.toSet()
        val actual = sources.filterKeys { it !in PLATFORM_BACKED }
            .mapValues { (_, source) -> forbiddenImports(source, declarations) }
            .filterValues { it.isNotEmpty() }
        println("Current readiness violations: $actual")
        assertEquals("Unexpected or stale readiness exceptions", ALLOWED_VIOLATIONS, actual)
    }

    /** Platform seams are exempt from [ALLOWED_VIOLATIONS] but must use exactly their approved backing, nothing else. */
    @Test fun platformBackedFilesUseOnlyTheirApprovedBacking() {
        val declarations = MOVE_SET.flatMap { declarations(File(root, it).readText()) }.toSet()
        for ((file, approved) in PLATFORM_BACKED) {
            assertEquals(file, approved, forbiddenImports(File(root, file).readText(), declarations))
            assertTrue("$file must be in MOVE_SET", file in MOVE_SET)
        }
    }

    @Test fun guardSeesPlantedViolation() {
        val moved = declarations("""
            package net.bible.android.database
            object Outer {
                class Nested
                fun member() = Unit
            }
            fun String.extension() = this
            val topLevel = 1
            fun localScope() {
                class Local
            }
            typealias Alias = Outer.Nested
        """.trimIndent())
        assertEquals(setOf(
            "net.bible.android.database.Outer", "net.bible.android.database.Outer.Nested",
            "net.bible.android.database.Outer.member", "net.bible.android.database.extension",
            "net.bible.android.database.topLevel", "net.bible.android.database.localScope",
            "net.bible.android.database.Alias"
        ), moved)
        val source = """
            import android.util.Log
            import java.util.Date as Clock
            import javax.crypto.Cipher
            import net.bible.android.database.Outer.Nested
            import net.bible.android.database.Outer.member
            import net.bible.android.database.Outer.NotMoved
            import net.bible.android.database.extension
            import net.bible.android.database.topLevel
            import net.bible.android.database.NotMoved
            import net.bible.android.database.*
            import net.bible.service.db.NotMoved
            // import android.fake.Comment
            val text = "import android.fake.String"
        """.trimIndent()
        assertEquals(setOf(
            "android.util.Log", "java.util.Date", "javax.crypto.Cipher",
            "net.bible.android.database.Outer.NotMoved", "net.bible.android.database.NotMoved",
            "net.bible.android.database.*", "net.bible.service.db.NotMoved"
        ), forbiddenImports(source, moved))
    }

    private fun forbiddenImports(source: String, movedDeclarations: Set<String>): Set<String> =
        Regex("(?m)^\\s*import\\s+([\\w.`*]+)").findAll(codeOnly(source))
            .map { it.groupValues[1].replace("`", "") }
            .filter { target ->
                listOf("android.", "java.", "javax.", "net.bible.android.", "net.bible.service.")
                    .any { target.startsWith(it) } && target !in movedDeclarations
            }.toSortedSet()

    // Mask comments and strings before reading import/declaration tokens. Preserve line breaks.
    private fun codeOnly(source: String): String {
        val result = source.toCharArray()
        var i = 0
        while (i < source.length) {
            val start = i
            when {
                source.startsWith("//", i) -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                source.startsWith("/*", i) -> {
                    i += 2
                    var depth = 1
                    while (i < source.length && depth > 0) {
                        when {
                            source.startsWith("/*", i) -> { depth++; i += 2 }
                            source.startsWith("*/", i) -> { depth--; i += 2 }
                            else -> i++
                        }
                    }
                }
                source.startsWith("\"\"\"", i) -> {
                    i = source.indexOf("\"\"\"", i + 3).let { if (it < 0) source.length else it + 3 }
                }
                source[i] == '"' || source[i] == '\'' -> {
                    val quote = source[i++]
                    while (i < source.length) {
                        if (source[i] == '\\') i = (i + 2).coerceAtMost(source.length)
                        else if (source[i++] == quote) break
                    }
                }
                else -> { i++; continue }
            }
            for (j in start until i) if (result[j] != '\n') result[j] = ' '
        }
        return result.concatToString()
    }

    /** Index exact declarations, not package or class prefixes: Outer.Unknown must still fail. */
    private fun declarations(source: String): Set<String> {
        val code = codeOnly(source)
        val pkg = Regex("(?m)^\\s*package\\s+([\\w.]+)").find(code)?.groupValues?.get(1)
            ?: return emptySet()
        val tokens = Regex("`[^`]+`|[A-Za-z_][A-Za-z_0-9]*|[^\\s]").findAll(code)
            .map { it.value.removeSurrounding("`") }.toList()
        val result = mutableSetOf<String>()
        val scopes = mutableListOf<String?>()
        var parentheses = 0
        var pendingClass: String? = null
        tokens.forEachIndexed { index, token ->
            val atDeclarationScope = scopes.all { it != null } && parentheses == 0
            if (atDeclarationScope && token in setOf("class", "object", "interface", "typealias", "fun", "val", "var")) {
                pendingClass = null
                val name = when (token) {
                    "fun" -> tokens.drop(index + 1).takeWhile { it != "(" }.lastOrNull()
                    "val", "var" -> tokens.drop(index + 1)
                        .takeWhile { it !in setOf(":", "=", "{", "}", ";", "get", "by") }.lastOrNull()
                    else -> tokens.getOrNull(index + 1)
                }
                if (name != null && Regex("[A-Za-z_][A-Za-z_0-9]*").matches(name)) {
                    result += (listOf(pkg) + scopes.filterNotNull() + name).joinToString(".")
                    if (token in setOf("class", "object", "interface")) pendingClass = name
                }
            }
            when (token) {
                "(" -> parentheses++
                ")" -> parentheses--
                "{" -> {
                    scopes += if (parentheses == 0) pendingClass else null
                    pendingClass = null
                }
                "}" -> { if (scopes.isNotEmpty()) scopes.removeAt(scopes.lastIndex) }
                "=" , ";" -> if (parentheses == 0) pendingClass = null
            }
        }
        return result
    }

    companion object {
        private const val DATABASE_DIRECTORY = "app/src/main/java/net/bible/android/database"

        // Explicit inventory: missing/deleted files fail instead of silently shrinking the gate.
        val MOVE_SET: List<String> = listOf(
            "ArgbColor.kt", "CachedCloudDocument.kt", "Converters.kt", "CustomRepository.kt", "Databases.kt",
            "DbDate.kt", "DocumentSearch.kt", "DocumentSyncEntities.kt", "Epub.kt", "IdType.kt",
            "OldMonolithicAppDatabase.kt", "PlatformRandom.kt", "Settings.kt", "SwordDocumentInfo.kt", "SyncableRoomDatabase.kt",
            "WorkspaceDao.kt", "WorkspaceEntities.kt", "bookmarks/BookmarkEntities.kt",
            "bookmarks/BookmarksDao.kt", "bookmarks/SpeakSettings.kt",
            "migrations/AiSettingsMigrations.kt", "migrations/BookmarkMigrations.kt",
            "migrations/DEPRECATED_BookmarkDatabaseDefinition.kt", "migrations/DEPRECATED_MyNoteDatabaseDefinition.kt",
            "migrations/DatabaseSplitMigrations.kt", "migrations/MyDocumentMigrations.kt",
            "migrations/OldMonolithicAppDatabaseMigrations.kt", "migrations/ProgressMigrations.kt",
            "migrations/ReadingPlansMigrations.kt", "migrations/Utilities.kt", "migrations/WorkspacesMigrations.kt",
            "mydocument/MyDocumentDao.kt", "mydocument/MyDocumentDatabase.kt", "mydocument/MyDocumentEntities.kt",
            "progress/ProgressDao.kt", "progress/ProgressDatabase.kt", "progress/ProgressEntities.kt",
            "readingplan/ReadingPlanDao.kt", "readingplan/ReadingPlanEntities.kt"
        ).map { "$DATABASE_DIRECTORY/$it" } + listOf(
            "app/src/main/java/net/bible/android/common/VerseExtensions.kt",
            "app/src/main/java/net/bible/service/db/SqliteConnectionExt.kt",
            "app/src/main/java/net/bible/service/db/OpenSqlite.kt",
            "app/src/main/java/net/bible/service/db/RoomSettingsBackend.kt",
            "app/src/main/java/net/bible/service/llm/AgentPromptEntities.kt"
        )

        // Platform seams whose JVM backing is validated exactly and separately; replaced by expect/actual in Task 6.
        val PLATFORM_BACKED: Map<String, Set<String>> = mapOf(
            "$DATABASE_DIRECTORY/DbDate.kt" to setOf("java.util.Date"),
            "$DATABASE_DIRECTORY/PlatformRandom.kt" to setOf("java.security.SecureRandom")
        )

        // Exact import targets, including existing wildcards; removals fail until the entry is removed.
        val ALLOWED_VIOLATIONS: Map<String, Set<String>> = mapOf(
            "$DATABASE_DIRECTORY/CachedCloudDocument.kt" to setOf(
                "net.bible.service.cloudsync.documents.DocumentSyncMeta", "net.bible.service.cloudsync.documents.DocumentType"),
            "$DATABASE_DIRECTORY/Converters.kt" to setOf(
                "net.bible.service.llm.ApiFormat", "net.bible.service.llm.agent.PermissionMode"),
            "$DATABASE_DIRECTORY/CustomRepository.kt" to setOf("java.util.*"),
            "$DATABASE_DIRECTORY/WorkspaceEntities.kt" to setOf("java.util.*"),
            "$DATABASE_DIRECTORY/bookmarks/BookmarkEntities.kt" to setOf("java.util.*", "net.bible.android.misc.OsisFragment"),
            "$DATABASE_DIRECTORY/bookmarks/BookmarksDao.kt" to setOf("java.util.*"),
            "$DATABASE_DIRECTORY/bookmarks/SpeakSettings.kt" to setOf("java.lang.IllegalArgumentException"),
            "$DATABASE_DIRECTORY/migrations/DatabaseSplitMigrations.kt" to setOf("android.app.Application"),
            "app/src/main/java/net/bible/service/db/OpenSqlite.kt" to setOf("java.io.File"),
            "app/src/main/java/net/bible/service/llm/AgentPromptEntities.kt" to setOf("net.bible.service.llm.agent.PermissionMode")
        )
    }
}
