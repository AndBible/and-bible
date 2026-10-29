package net.bible.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Access to the bibleview-js bundle that the build packs into the classpath resources under `bibleview-js/`. */
object BibleViewBundle {
    private const val ROOT = "bibleview-js"

    private fun open(name: String) =
        BibleViewBundle::class.java.classLoader.getResourceAsStream("$ROOT/$name")
            ?: error("Missing resource $ROOT/$name; was prepareBibleViewBundle run?")

    /** Copies the bundle to `dir/bibleview-js` (listed by `files.txt`) and returns the path of its `index.html`. */
    fun extractTo(dir: Path): Path {
        val target = dir.resolve(ROOT).normalize()
        val files = open("files.txt").use { it.readBytes().decodeToString() }.lines().filter { it.isNotBlank() }
        for (name in files) {
            val out = target.resolve(name).normalize()
            require(out.startsWith(target)) { "Bad resource path $name" }
            Files.createDirectories(out.parent)
            open(name).use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
        }
        return target.resolve("index.html")
    }

    /** The PoC document JSON fixture. */
    fun pocDocumentJson(): String = open("poc-document.json").use { it.readBytes().decodeToString() }
}
