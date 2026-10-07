package net.bible.android.control.event

import java.io.File

/** Finds every event class posted to or subscribed on [ABEventBus] in production sources (spec §3.4). */
internal object EventBusUsageScanner {
    data class Usage(val eventClass: String, val location: String)

    /** Marker for `ABEventBus.post(someVariable)`: the class cannot be read from source, so it is never allowed. */
    const val VARIABLE_POST = "<variable post>"

    private val post = Regex("""ABEventBus\s*\.\s*post\(\s*([A-Za-z_][A-Za-z0-9_.]*)""")
    private val subscription = Regex("""\b(?:on|onMain|eventsOf|stateFromEvents)<\s*([A-Za-z_][A-Za-z0-9_.]*)""")
    private val subscribingFile = Regex("""ABEventBus\.(?:register|safelyRegister)\(|\beventsOf<|\bstateFromEvents<""")

    /** The bus and its bridge define the generic API itself; their `<T>` is not an event. */
    private val excludedFiles = setOf("ABEventBus.kt", "EventBridge.kt")

    fun scan(fileName: String, source: String): List<Usage> {
        if (fileName.substringAfterLast('/') in excludedFiles) return emptyList()
        // Comment lines become empty lines so line numbers stay true while the regexes run over the whole text.
        val code = source.lines().map { line ->
            val t = line.trimStart()
            if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) "" else line
        }
        val text = code.joinToString("\n")
        fun lineOf(index: Int) = text.take(index).count { it == '\n' } + 1
        val posts = post.findAll(text).map { m ->
            val name = m.groupValues[1]
            Usage(if (name.first().isLowerCase()) VARIABLE_POST else name.substringAfterLast('.'), "$fileName:${lineOf(m.range.first)}")
        }.toList()
        if (!subscribingFile.containsMatchIn(text)) return posts
        val subs = code.withIndex().flatMap { (i, line) ->
            subscription.findAll(line).map { Usage(it.groupValues[1].substringAfterLast('.'), "$fileName:${i + 1}") }.toList()
        }
        return posts + subs
    }

    fun repoRoot(): File = File("..").canonicalFile.also {
        check(File(it, "settings.gradle.kts").isFile) { "unit tests must run from :app; repo root not found at $it" }
    }

    /** Production Kotlin under :app, :sharedCore and :sharedUi (no test source sets). */
    fun productionUsages(): List<Usage> {
        val root = repoRoot()
        val roots = listOf("app/src/main", "sharedCore/src", "sharedUi/src").map { File(root, it) }
        return roots.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && !it.path.contains("Test/") && !it.path.contains("/test/") }
                .flatMap { f -> scan(f.relativeTo(root).path, f.readText()) }
                .toList()
        }
    }
}
