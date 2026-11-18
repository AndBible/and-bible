package net.bible.sharedui.host.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use

/** Bounded A/B generations with append-only, checksummed commits between rotations. Single writer only. */
internal class RuntimeDiagnosticStore(
    private val directory: Path,
    private val fileSystem: FileSystem,
    private val onFileCreated: (Path) -> Unit = {},
    private val nowMillis: () -> Long,
) {
    private val files = listOf(directory / "diagnostics.0.ndjson", directory / "diagnostics.1.ndjson")
    private data class Line(val text: String, val runId: String, val sequence: Long, val time: Long) {
        val bytes = (text + "\n").encodeToByteArray().size
    }
    private data class Generation(val index: Int, val number: Long, val lines: List<Line>, val bytes: Int, val hash: Long)
    private var generation: Generation? = null
    private var rotateBeforeAppend = true
    private var omitted = 0L

    init {
        fileSystem.createDirectories(directory)
        val loaded = files.indices.mapNotNull { index ->
            try { readGeneration(index) } catch (_: IOException) { null }
        }
        generation = loaded.maxByOrNull { it.number }
        val current = generation
        if (current != null && live(current.lines).size != current.lines.size) write(live(current.lines))
        files.indices.filter { it != generation?.index }.forEach { index ->
            val previous = loaded.find { it.index == index }
            if (previous == null || live(previous.lines).size != previous.lines.size) {
                fileSystem.delete(files[index], mustExist = false)
            }
        }
    }

    fun append(record: DiagnosticRecord) = appendBatch(listOf(record))

    fun appendBatch(records: List<DiagnosticRecord>) {
        val lines = records.mapNotNull { record ->
            val encoded = record.encode()
            if (encoded.length >= 4096 || (encoded + "\n").encodeToByteArray().size > 4096) {
                if (omitted < Long.MAX_VALUE) omitted++
                null
            } else Line(encoded, record.runId, record.sequence, record.epochMillis)
        }
        if (lines.isEmpty()) return
        val current = generation
        val body = lines.joinToString("") { it.text + "\n" }
        // Leave room for the cumulative checksum footer; rotation retains headroom for many appends.
        if (current == null || rotateBeforeAppend || current.bytes + body.encodeToByteArray().size + 64 > MAX_BYTES) {
            val previous = current?.lines ?: emptyList()
            val fresh = live(lines)
            write(live(previous) + fresh, fresh.size)
            if (current != null && live(current.lines).size != current.lines.size) {
                fileSystem.delete(files[current.index], mustExist = false)
            }
        } else {
            val hash = checksum(body, current.hash)
            val footer = "{\"checksum\":$hash}\n"
            val path = files[current.index]
            // On any failed append, rotate next time: a partial tail must never become committed data.
            rotateBeforeAppend = true
            fileSystem.appendingSink(path, mustExist = true).buffer().use { it.writeUtf8(body); it.writeUtf8(footer) }
            generation = current.copy(lines = current.lines + lines,
                bytes = current.bytes + (body + footer).encodeToByteArray().size, hash = checksum(footer, hash))
            rotateBeforeAppend = false
        }
    }

    /** Returns and resets the count for the background writer to emit as EventsDropped. */
    fun takeOmittedCount(): Long = omitted.also { omitted = 0 }

    /** Reports only the current and previous retained run, not a week of app-opening history. */
    fun snapshot(): String {
        val runs = linkedMapOf<String, MutableList<Line>>()
        live(generation?.lines ?: emptyList()).forEach { runs.getOrPut(it.runId) { mutableListOf() }.add(it) }
        return runs.values.toList().takeLast(2).flatMap { it.sortedBy(Line::sequence) }.joinToString("") { it.text + "\n" }
    }

    private fun live(lines: List<Line>): List<Line> {
        val now = nowMillis()
        val cutoff = if (now < Long.MIN_VALUE + 604800000L) Long.MIN_VALUE else now - 604800000L
        return lines.filter { it.time >= cutoff }
    }

    private fun write(input: List<Line>, newLineCount: Int = 0) {
        // Even a failed close can leave a complete newer generation on disk.
        // Never append to the older in-memory generation after any rotation failure.
        rotateBeforeAppend = true
        val next = (generation?.number ?: -1L) + 1
        val index = generation?.let { 1 - it.index } ?: 0
        val header = "{\"generation\":$next}\n"
        var size = header.encodeToByteArray().size + 64
        var start = input.size
        // Compact to half the limit rather than rewrite a full history again on the next event.
        while (start > 0 && size + input[start - 1].bytes <= MAX_BYTES / 2) {
            size += input[--start].bytes
        }
        val lines = input.drop(start)
        val body = lines.joinToString("") { it.text + "\n" }
        val hash = checksum(header + body)
        val footer = "{\"checksum\":$hash}\n"
        val path = files[index]
        val created = !fileSystem.exists(path)
        try {
            fileSystem.sink(path).buffer().use { it.writeUtf8(header); it.writeUtf8(body); it.writeUtf8(footer) }
        } finally {
            if (created && fileSystem.exists(path)) onFileCreated(path)
        }
        generation = Generation(index, next, lines, (header + body + footer).encodeToByteArray().size, checksum(footer, hash))
        // Evicting retained history is normal; losing records from this batch is reportable.
        val lost = (newLineCount - lines.size).coerceAtLeast(0).toLong()
        omitted += minOf(lost, Long.MAX_VALUE - omitted)
        rotateBeforeAppend = false
    }

    private fun checksum(text: String, initial: Long = 1469598103934665603L): Long {
        var value = initial
        for (byte in text.encodeToByteArray()) value = (value xor (byte.toLong() and 255)) * 1099511628211L
        return value
    }

    private fun readGeneration(index: Int): Generation? {
        val path = files[index]
        if (!fileSystem.exists(path)) return null
        if ((fileSystem.metadata(path).size ?: 0L) > MAX_BYTES) return null
        val text = fileSystem.source(path).buffer().use { it.readUtf8() }
        val pieces = text.split('\n')
        val number = (parseObject(pieces[0])?.get("generation") as? JsonPrimitive)?.longOrNull ?: return null
        var hash = checksum(pieces[0] + "\n")
        var bytes = (pieces[0] + "\n").encodeToByteArray().size
        val lines = mutableListOf<Line>()
        var committedCount = -1
        var committedBytes = 0
        var committedHash = 0L
        // Old single-footer files and new cumulative checkpoints use the same checksum format.
        // Only newline-terminated, verified prefixes are recoverable; ignore an interrupted tail.
        for (raw in pieces.drop(1).dropLast(1)) {
            val obj = parseObject(raw)
            val expected = (obj?.get("checksum") as? JsonPrimitive)?.longOrNull
            val valid = expected != null && expected == hash
            if (expected == null && obj != null) {
                val run = (obj["runId"] as? JsonPrimitive)?.content
                val seq = (obj["sequence"] as? JsonPrimitive)?.longOrNull
                val time = (obj["epochMillis"] as? JsonPrimitive)?.longOrNull
                if (run != null && seq != null && time != null) lines.add(Line(raw, run, seq, time))
            }
            hash = checksum(raw + "\n", hash)
            bytes += (raw + "\n").encodeToByteArray().size
            if (valid) {
                committedCount = lines.size
                committedBytes = bytes
                committedHash = hash
            }
        }
        return if (committedCount < 0) null
        else Generation(index, number, lines.take(committedCount), committedBytes, committedHash)
    }

    private fun parseObject(value: String) = try { Json.parseToJsonElement(value).jsonObject } catch (_: IllegalArgumentException) { null }

    companion object { private const val MAX_BYTES = 524288 }
}
