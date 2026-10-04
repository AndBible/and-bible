package net.bible.android.view

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F63. A `<path>` whose subpath starts with a RELATIVE command directly after a `Z`, at a point where
 * the contour start differs from the pre-close point, renders correctly under the framework's vector
 * parser and WRONG under Compose's `painterResource` on every renderer whose `SkPath::rMoveTo` ignores
 * the close (measured broken on API 30 and in Robolectric's Skia; correct on API 35).
 *
 * The goldens CANNOT catch this -- Roborazzi's graphics come from one shared native runtime per host
 * platform regardless of `@Config(sdk = …)`, so every "981/0 changed" verify run re-blessed the broken
 * output. This guard can: it reads the geometry, not the pixels.
 *
 * The fix is always the same and is machine-derivable: rewrite the subpath to start with an absolute
 * `M` at `contour start + the relative delta`. Absolute `M` removes the dependency on any renderer's
 * post-`Z` semantics and changes nothing on renderers that were already correct.
 */
class VectorPathDataGuardTest {

    private val roots = listOf(
        File("src/main/res"),
        File("src/debug/res"),
        File("src/discrete/res"),
    )

    private data class Drift(val file: String, val dx: Double, val dy: Double, val cmd: Char)

    @Test
    fun noSubpathStartsWithARelativeCommandAfterADriftedClose() {
        val offenders = mutableListOf<Drift>()
        var filesScanned = 0

        roots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "xml" && it.parentFile?.name?.startsWith("drawable") == true }
                .forEach { file ->
                    filesScanned++
                    PATH_DATA.findAll(file.readText()).forEach { m ->
                        offenders += driftsIn(m.groupValues[1]).map {
                            Drift(file.name, it.first, it.second, it.third)
                        }
                    }
                }
        }

        assertTrue(
            "the scan found no drawable XML at all -- the paths are wrong, not the tree " +
                "(231 vector XMLs existed under src/main/res on 2026-09-18)",
            filesScanned > 100,
        )
        assertTrue(
            offenders.joinToString("\n") {
                "${it.file}: a '${it.cmd}' directly after a Z resolves from a point that drifted by " +
                    "(${it.dx}, ${it.dy}). Compose's PathParser does not re-anchor after close, so this " +
                    "subpath renders in the wrong place below API 35 (F63). Rewrite it to start with an " +
                    "absolute M at contourStart + the relative delta."
            },
            offenders.isEmpty(),
        )
    }

    private companion object {
        val PATH_DATA = Regex("""android:pathData\s*=\s*"([^"]*)"""", RegexOption.DOT_MATCHES_ALL)
        val TOKEN = Regex("""[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+(?:[eE][-+]?\d+)?|\d+\.?(?:[eE][-+]?\d+)?)""")
        val ARGC = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7, 'Z' to 0)

        /**
         * Walks one `pathData` with correct SVG semantics (on `Z` the current point returns to the
         * contour start) and reports, for every relative command that directly follows a `Z`, how far
         * the pre-close point had drifted from that contour start. A zero drift is harmless: both
         * readings agree.
         *
         * Accumulates in `Double`, not `Float`. `Float` addition accumulates rounding error across a
         * multi-segment subpath -- observed up to ~2e-5 on coordinates in the 0-500dp range used here --
         * which false-positives past a `1e-6` epsilon on subpaths that have zero real drift (measured:
         * 42 files flagged in `Float`, against the 30 confirmed independently in the design spec's
         * prototype, with the same `1e-6` epsilon and the same `ic_bible_24dp.xml` drift of
         * `55.935547`). `Double` has enough headroom that this epsilon sees only genuine drift.
         */
        fun driftsIn(d: String): List<Triple<Double, Double, Char>> {
            val toks = TOKEN.findAll(d).map { it.value }.toList()
            var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0
            var i = 0; var cmd: Char? = null
            var pending: Pair<Double, Double>? = null
            val hits = mutableListOf<Triple<Double, Double, Char>>()
            while (i < toks.size) {
                val t = toks[i]
                if (t.length == 1 && t[0].isLetter()) {
                    cmd = t[0]; i++
                    if (cmd == 'Z' || cmd == 'z') { pending = (cx - sx) to (cy - sy); cx = sx; cy = sy }
                    continue
                }
                val c = cmd ?: run { i++; return@run null } ?: continue
                val up = c.uppercaseChar(); val rel = c.isLowerCase(); val n = ARGC.getValue(up)
                pending?.let { p ->
                    if (rel && (kotlin.math.abs(p.first) > 1e-6 || kotlin.math.abs(p.second) > 1e-6)) {
                        hits += Triple(p.first, p.second, c)
                    }
                    pending = null
                }
                if (i + n > toks.size) break
                val a = (0 until n).map { toks[i + it].toDouble() }
                i += n
                when (up) {
                    'M' -> { if (rel) { cx += a[0]; cy += a[1] } else { cx = a[0]; cy = a[1] }
                             sx = cx; sy = cy; cmd = if (rel) 'l' else 'L' }
                    'L' -> if (rel) { cx += a[0]; cy += a[1] } else { cx = a[0]; cy = a[1] }
                    'H' -> cx = if (rel) cx + a[0] else a[0]
                    'V' -> cy = if (rel) cy + a[0] else a[0]
                    'C' -> if (rel) { cx += a[4]; cy += a[5] } else { cx = a[4]; cy = a[5] }
                    'S', 'Q' -> if (rel) { cx += a[2]; cy += a[3] } else { cx = a[2]; cy = a[3] }
                    'T' -> if (rel) { cx += a[0]; cy += a[1] } else { cx = a[0]; cy = a[1] }
                    'A' -> if (rel) { cx += a[5]; cy += a[6] } else { cx = a[5]; cy = a[6] }
                }
            }
            return hits
        }
    }
}
