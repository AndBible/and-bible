package net.bible.sharedui.host.diagnostics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okio.FileSystem
import okio.Path
import okio.buffer

/** Two alternating complete snapshots; usable history is at most one 512 KiB file. */
internal class RuntimeDiagnosticStore(private val directory: Path, private val fileSystem: FileSystem, private val nowMillis: () -> Long) {
    private val files = listOf(directory / "diagnostics.0.ndjson", directory / "diagnostics.1.ndjson")
    private data class Line(val text: String, val runId: String, val sequence: Long, val time: Long)
    private data class Generation(val index: Int, val number: Long, val lines: List<Line>)
    private var generation: Generation? = null
    private var omitted = 0L

    init {
        fileSystem.createDirectories(directory)
        generation = files.indices.mapNotNull(::readGeneration).maxByOrNull { it.number }
        prune()
    }

    fun append(record: DiagnosticRecord) {
        prune()
        val encoded = record.encode()
        if (encoded.length >= 4096 || (encoded + "\n").encodeToByteArray().size > 4096) {
            if (omitted < Long.MAX_VALUE) omitted++
            return
        }
        val line = Line(encoded, record.runId, record.sequence, record.epochMillis)
        write(live((generation?.lines ?: emptyList()) + line))
    }

    /** Returns and resets the count for the background writer to emit as EventsDropped. */
    fun takeOmittedCount(): Long = omitted.also { omitted = 0 }

    fun snapshot(): String {
        val runs = linkedMapOf<String, MutableList<Line>>()
        live(generation?.lines ?: emptyList()).forEach { runs.getOrPut(it.runId) { mutableListOf() }.add(it) }
        return runs.values.flatMap { it.sortedBy(Line::sequence) }.joinToString("") { it.text + "\n" }
    }

    private fun live(lines: List<Line>): List<Line> {
        val now = nowMillis()
        val cutoff = if (now < Long.MIN_VALUE + 604800000L) Long.MIN_VALUE else now - 604800000L
        return lines.filter { it.time >= cutoff }
    }

    private fun prune() {
        val current = generation ?: return
        val retained = live(current.lines)
        if (retained.size != current.lines.size) write(retained)
        val inactive = 1 - generation!!.index
        val previous = readGeneration(inactive)
        if (previous == null || live(previous.lines).size != previous.lines.size) {
            fileSystem.delete(files[inactive], mustExist = false)
        }
    }

    private fun write(input: List<Line>) {
        val next = (generation?.number ?: -1L) + 1
        val index = generation?.let { 1 - it.index } ?: 0
        val header = "{\"generation\":$next}\n"
        var size = header.encodeToByteArray().size + 64
        var start = input.size
        while (start > 0) {
            val nextSize = input[start - 1].text.encodeToByteArray().size + 1
            if (size + nextSize > 524288) break
            size += nextSize
            start--
        }
        val lines = input.drop(start)
        val body = lines.joinToString("") { it.text + "\n" }
        val footer = "{\"checksum\":${checksum(header + body)}}\n"
        fileSystem.sink(files[index]).buffer().use { it.writeUtf8(header); it.writeUtf8(body); it.writeUtf8(footer) }
        generation = Generation(index, next, lines)
    }

    private fun checksum(text: String): Long {
        var value = 1469598103934665603L
        for (byte in text.encodeToByteArray()) value = (value xor (byte.toLong() and 255)) * 1099511628211L
        return value
    }

    private fun readGeneration(index: Int): Generation? {
        val path = files[index]
        if (!fileSystem.exists(path)) return null
        if ((fileSystem.metadata(path).size ?: 0L) > 524288L) return null
        val text = fileSystem.source(path).buffer().use { it.readUtf8() }
        val pieces = text.split('\n')
        // Ignore an incomplete tail, but require a complete, checksummed generation.
        for (end in pieces.size - 2 downTo 1) {
            val footer = parseObject(pieces[end]) ?: continue
            val expected = footer["checksum"]?.jsonPrimitive?.longOrNull ?: continue
            val payload = pieces.subList(0, end).joinToString("\n", postfix = "\n")
            if (checksum(payload) != expected) continue
            val number = parseObject(pieces[0])?.get("generation")?.jsonPrimitive?.longOrNull ?: continue
            val lines = pieces.subList(1, end).mapNotNull { raw ->
                val obj = parseObject(raw) ?: return@mapNotNull null
                val run = obj["runId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val seq = obj["sequence"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                val time = obj["epochMillis"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                Line(raw, run, seq, time)
            }
            return Generation(index, number, lines)
        }
        return null
    }

    private fun parseObject(value: String) = try { Json.parseToJsonElement(value).jsonObject } catch (_: IllegalArgumentException) { null }
}
