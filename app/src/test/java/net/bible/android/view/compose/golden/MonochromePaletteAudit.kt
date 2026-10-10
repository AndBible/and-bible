package net.bible.android.view.compose.golden

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.abs

/**
 * Allows ink, paper and MonoDisabled (within two per channel), plus achromatic anti-aliased
 * edges (adjacent channel differences at most four) within three pixels of ink or paper.
 * All-other 3x3 blocks with channel range at most four are rejected as low-variation fills;
 * high-variation blocks occur in real opaque glyph rasterization and are not fill evidence.
 * Alpha is ignored: the harness audits rendered RGB, not source paint colours. This bounded
 * heuristic cannot distinguish source paint for thin grey motifs or steep, edge-like gradients.
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
            // Radius two still rejects four pixels in the six original glyph renders.
            val hasEdgeNeighbour = (-3..3).any { dx -> (-3..3).any { dy ->
                val nx = x + dx
                val ny = y + dy
                (dx != 0 || dy != 0) && nx in 0 until w && ny in 0 until h && inkOrPaper(rgb(nx, ny))
            } }
            val inFillBlock = (-2..0).any { ox -> (-2..0).any { oy ->
                var min = 255
                var max = 0
                val allOther = (0..2).all { dx -> (0..2).all { dy ->
                    val nx = x + ox + dx
                    val ny = y + oy + dy
                    if (nx !in 0 until w || ny !in 0 until h || !other[nx][ny]) false else {
                        val c = rgb(nx, ny)
                        min = minOf(min, c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)
                        max = maxOf(max, c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)
                        true
                    }
                } }
                // Four is the diameter of the unchanged +/-2 per-channel palette tolerance.
                allOther && max - min <= 4
            } }
            if (isChroma || !hasEdgeNeighbour || inFillBlock) {
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

/** Permanent purpose-colour exemptions and failure handling, separate from Compose for cheap testing. */
internal class MonochromeAuditPolicy(private val exempt: Set<String>) {
    fun shouldAudit(key: String): Boolean = key !in exempt

    data class Render(val tag: String, val result: MonochromePaletteAudit.AuditResult, val image: File)

    fun check(key: String, renders: List<Render>, report: String?) {
        if (!shouldAudit(key)) return
        require(renders.size == 2) { "A scene audit requires both monochrome themes" }
        val messages = renders.filter { !it.result.passed }.map { (tag, result, image) ->
            "${key}_${tag}: ${result.badPixels} non-monochrome px (${result.chromatic} chromatic) in ${result.bounds}; see ${image.path}"
        }
        if (messages.isEmpty()) return
        if (report.isNullOrEmpty()) throw AssertionError(messages.joinToString("\n"))
        val file = File(report)
        file.parentFile?.mkdirs()
        file.appendText(messages.joinToString("\n", postfix = "\n"))
    }

    companion object {
        fun fromResources(): MonochromeAuditPolicy {
            val stream = checkNotNull(MonochromeAuditPolicy::class.java.getResourceAsStream("/monochrome-audit-exempt.txt")) {
                "Missing monochrome audit resource: monochrome-audit-exempt.txt"
            }
            val exempt = stream.bufferedReader().useLines { lines ->
                lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
            }
            return MonochromeAuditPolicy(exempt)
        }
    }
}
