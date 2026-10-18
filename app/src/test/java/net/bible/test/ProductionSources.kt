package net.bible.test

import java.io.File

/**
 * Locates production Kotlin sources for the source-scan guard tests. Domain code moved from `:app`
 * (`app/src/main/java`) to `:sharedLogic` (`commonMain`) with unchanged package names (L1b phase 2),
 * so a `net/bible/...` file lives under exactly one of two roots. Tests run with `app/` as the
 * working directory.
 */
object ProductionSources {
    private const val APP_PREFIX = "src/main/java/"

    /** Every root that holds production code `:app` ships: its own tree and the `:sharedLogic` one. */
    val roots: List<File> = listOf(File("src/main/java"), File("../sharedLogic/src/commonMain/kotlin"))

    /**
     * Resolves [path] to the file that exists. [path] is either the legacy `src/main/java/net/bible/...`
     * spelling or a bare `net/bible/...` one, or any path that already exists as given. A path that exists in neither root resolves to its `:app`
     * spelling, so callers' "missing file" checks keep failing loudly. Paths outside `src/main/java`
     * (resources, manifests) are returned unchanged.
     */
    fun resolve(path: String): File {
        File(path).takeIf { it.exists() }?.let { return it } // already a concrete path (e.g. from [sources])
        val rel = path.removePrefix(APP_PREFIX)
        if (rel == path && path.startsWith("src/")) return File(path)
        return roots.map { File(it, rel) }.firstOrNull { it.exists() } ?: File(roots.first(), rel)
    }

    /** All `.kt`/`.java` files under every root, with the vacuity floor already checked. */
    fun sources(): List<File> = roots.flatMap { root ->
        check(root.isDirectory) { "production source root missing (run from app/): $root" }
        root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()
    }
}
