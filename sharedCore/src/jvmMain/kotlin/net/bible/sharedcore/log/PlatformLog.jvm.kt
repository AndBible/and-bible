package net.bible.sharedcore.log

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
    System.err.println("${level.name.first()}/$tag: $msg")
    tr?.printStackTrace()
    DesktopLogFile.write(level, tag, msg, tr)
}

/**
 * Starts writing every log line to `<dir>/andbible.log` as well as stderr. Called once by desktop startup; until then
 * (and in every JVM test) logging is stderr-only. See [DesktopLogFile].
 */
fun configureDesktopLogFile(dir: Path) = DesktopLogFile.configure(dir)

/**
 * The desktop log file: `andbible.log` is rotated when it reaches [maxBytes]; the previous files become
 * `andbible.log.1` (newest) to `andbible.log.<keep>`, and older ones are deleted. A failure to write the file never
 * breaks logging; it is reported once on stderr and the file is dropped.
 */
object DesktopLogFile {
    const val FILE_NAME = "andbible.log"

    @Volatile private var sink: Sink? = null

    /** Opens (appending to) the log file in [dir]; replaces any earlier configuration. */
    fun configure(dir: Path, maxBytes: Long = 1024L * 1024L, keep: Int = 3) {
        try {
            Files.createDirectories(dir)
            sink = Sink(dir.resolve(FILE_NAME), maxBytes, keep)
        } catch (e: IOException) {
            System.err.println("W/DesktopLogFile: cannot open log file in $dir: $e")
            sink = null
        }
    }

    /** Stops file logging; later lines go to stderr only. */
    fun close() { sink = null }

    internal fun write(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
        val current = sink ?: return
        try {
            current.append(format(level, tag, msg, tr))
        } catch (e: IOException) {
            System.err.println("W/DesktopLogFile: log file write failed, file logging stopped: $e")
            if (sink === current) sink = null
        }
    }

    private fun format(level: LogLevel, tag: String, msg: String, tr: Throwable?): String = buildString {
        append(Instant.now()).append(' ').append(level.name.first()).append('/').append(tag).append(": ").append(msg).append('\n')
        if (tr != null) append(StringWriter().also { tr.printStackTrace(PrintWriter(it)) }.toString())
    }

    private class Sink(private val file: Path, private val maxBytes: Long, private val keep: Int) {
        private var size = if (Files.exists(file)) Files.size(file) else 0L

        @Synchronized
        fun append(text: String) {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            if (size > 0 && size + bytes.size > maxBytes) rotate()
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            size += bytes.size
        }

        private fun rotate() {
            fun archive(n: Int) = file.resolveSibling("${file.fileName}.$n")
            Files.deleteIfExists(archive(keep))
            for (n in keep - 1 downTo 1) {
                if (Files.exists(archive(n))) Files.move(archive(n), archive(n + 1), StandardCopyOption.REPLACE_EXISTING)
            }
            if (keep > 0) Files.move(file, archive(1), StandardCopyOption.REPLACE_EXISTING) else Files.deleteIfExists(file)
            size = 0
        }
    }
}
