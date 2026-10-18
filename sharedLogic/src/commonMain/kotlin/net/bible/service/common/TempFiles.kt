package net.bible.service.common

import kotlin.random.Random
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Creates a new, empty, uniquely named file in [dir], like `File.createTempFile`:
 * `prefix + random positive long + (suffix ?: ".tmp")`. [dir] must already exist.
 */
fun createTempFile(dir: Path, prefix: String, suffix: String? = null, fs: FileSystem = FileSystem.SYSTEM): Path {
    var lastError: IOException? = null
    repeat(100) {
        val name = prefix + (Random.nextLong() and Long.MAX_VALUE) + (suffix ?: ".tmp")
        val path = dir / name
        try {
            fs.write(path, mustCreate = true) {}
            return path
        } catch (e: IOException) {
            if (fs.exists(path)) lastError = e else throw e // name collision: retry; anything else: fail
        }
    }
    throw IOException("Unable to create a temporary file in $dir", lastError)
}
