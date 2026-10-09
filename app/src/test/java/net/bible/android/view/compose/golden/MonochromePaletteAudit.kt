package net.bible.android.view.compose.golden

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.io.Reader
import kotlin.math.abs

/**
 * Allows ink, paper and MonoDisabled (within two per channel), plus achromatic anti-aliased
 * edges (adjacent channel differences at most four). A 3x3 all-other block is a fill, not an edge.
 * Alpha is ignored: the harness audits rendered RGB, not source paint colours.
 */
object MonochromePaletteAudit {
    data class AuditResult(val badPixels: Int, val bounds: Rectangle?, val chromatic: Int) {
        val passed: Boolean get() = badPixels == 0
    }

    fun audit(img: BufferedImage, dark: Boolean): AuditResult {
        val w = img.width
        val h = img.height
        val ink = if (dark) 0xFFFFFF else 0x000000
        val paper = if (dark) 0x000000 else 0xFFFFFF
        fun rgb(x: Int, y: Int) = img.getRGB(x, y) and 0xFFFFFF
        fun near(a: Int, b: Int): Boolean =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= 2 &&
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= 2 &&
                abs((a and 0xFF) - (b and 0xFF)) <= 2
        fun inkOrPaper(c: Int) = near(c, ink) || near(c, paper)
        fun allowed(c: Int) = inkOrPaper(c) || near(c, 0x808080)
        fun chroma(c: Int): Boolean {
            val r = c shr 16 and 0xFF
            val g = c shr 8 and 0xFF
            val b = c and 0xFF
            return abs(r - g) > 4 || abs(g - b) > 4
        }
        val other = Array(w) { x -> BooleanArray(h) { y -> !allowed(rgb(x, y)) } }
        var bad = 0
        var chromatic = 0
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (x in 0 until w) for (y in 0 until h) {
            if (!other[x][y]) continue
            val isChroma = chroma(rgb(x, y))
            val hasEdgeNeighbour = (-1..1).any { dx -> (-1..1).any { dy ->
                val nx = x + dx
                val ny = y + dy
                (dx != 0 || dy != 0) && nx in 0 until w && ny in 0 until h && inkOrPaper(rgb(nx, ny))
            } }
            val inBlock = (-2..0).any { ox -> (-2..0).any { oy ->
                (0..2).all { dx -> (0..2).all { dy ->
                    val nx = x + ox + dx
                    val ny = y + oy + dy
                    nx in 0 until w && ny in 0 until h && other[nx][ny]
                } }
            } }
            if (isChroma || !hasEdgeNeighbour || inBlock) {
                bad++
                if (isChroma) chromatic++
                minX = minOf(minX, x); minY = minOf(minY, y)
                maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            }
        }
        val bounds = if (bad == 0) null else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
        return AuditResult(bad, bounds, chromatic)
    }
}

/** Classpath scene lists and failure handling, separate from Compose so the policy is cheaply testable. */
internal class MonochromeAuditPolicy(private val allowlist: Set<String>, private val exempt: Set<String>) {
    fun shouldAudit(key: String): Boolean = key !in exempt

    fun check(key: String, tag: String, result: MonochromePaletteAudit.AuditResult, image: File, report: String?) {
        if (!shouldAudit(key)) return
        val message = when {
            key in allowlist && result.passed ->
                "$key passes the monochrome audit; remove it from monochrome-audit-allowlist.txt"
            key !in allowlist && !result.passed ->
                "${key}_${tag}: ${result.badPixels} non-monochrome px (${result.chromatic} chromatic) in ${result.bounds}; see ${image.path}"
            else -> return
        }
        if (report.isNullOrEmpty()) throw AssertionError(message)
        val file = File(report)
        file.parentFile?.mkdirs()
        file.appendText("$message\n")
    }

    companion object {
        fun parseEntries(reader: Reader): Set<String> = reader.buffered().useLines { lines ->
            lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        }

        fun fromResources(): MonochromeAuditPolicy {
            fun entries(name: String): Set<String> {
                val stream = checkNotNull(MonochromeAuditPolicy::class.java.getResourceAsStream("/$name")) {
                    "Missing monochrome audit resource: $name"
                }
                return parseEntries(stream.reader())
            }
            return MonochromeAuditPolicy(entries("monochrome-audit-allowlist.txt"), entries("monochrome-audit-exempt.txt"))
        }
    }
}
