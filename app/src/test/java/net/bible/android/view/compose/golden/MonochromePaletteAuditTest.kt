package net.bible.android.view.compose.golden

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MonochromePaletteAuditTest {
    private fun img(w: Int, h: Int, fill: Int) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        for (x in 0 until w) for (y in 0 until h) setRGB(x, y, fill)
    }
    private val ink = 0xFF000000.toInt()
    private val paper = 0xFFFFFFFF.toInt()

    @Test fun `pure paper passes`() = assertTrue(MonochromePaletteAudit.audit(img(20, 20, paper), false).passed)
    @Test fun `808080 passes`() {
        val i = img(20, 20, paper)
        for (x in 5..15) for (y in 5..15) i.setRGB(x, y, 0xFF808080.toInt())
        assertTrue(MonochromePaletteAudit.audit(i, false).passed)
    }
    @Test fun `a grey fill fails and reports its bounds`() {
        val i = img(20, 20, paper)
        for (x in 2..9) for (y in 3..7) i.setRGB(x, y, 0xFFD2D2D2.toInt())
        val r = MonochromePaletteAudit.audit(i, false)
        assertFalse(r.passed)
        assertEquals(40, r.badPixels)
        assertEquals(0, r.chromatic)
        assertEquals(Rectangle(2, 3, 8, 5), r.bounds)
    }
    @Test fun `an isolated anti-aliasing grey next to ink passes`() {
        val i = img(20, 20, paper)
        i.setRGB(10, 10, ink); i.setRGB(11, 10, 0xFF7A7A7A.toInt())
        assertTrue(MonochromePaletteAudit.audit(i, false).passed)
    }
    @Test fun `any chromatic pixel fails even a single one`() {
        val i = img(20, 20, paper); i.setRGB(4, 4, 0xFF2196F3.toInt())
        val r = MonochromePaletteAudit.audit(i, false)
        assertFalse(r.passed)
        assertEquals(1, r.badPixels); assertEquals(1, r.chromatic)
        assertEquals(Rectangle(4, 4, 1, 1), r.bounds)
    }
    @Test fun `a 3x3 block of edge-like greys is a fill not an edge`() {
        val i = img(20, 20, paper); i.setRGB(9, 9, ink)
        for (x in 10..12) for (y in 10..12) i.setRGB(x, y, 0xFF7A7A7A.toInt())
        assertEquals(9, MonochromePaletteAudit.audit(i, false).badPixels)
    }
    @Test fun `dark swaps ink and paper`() {
        assertTrue(MonochromePaletteAudit.audit(img(10, 10, ink), true).passed)
    }
    @Test fun `palette tolerance includes two but excludes three`() {
        assertTrue(MonochromePaletteAudit.audit(img(3, 3, 0xFF828282.toInt()), false).passed)
        assertEquals(9, MonochromePaletteAudit.audit(img(3, 3, 0xFF838383.toInt()), false).badPixels)
    }
    @Test fun `chroma tolerance includes four but excludes five on an edge`() {
        val i = img(2, 1, paper); i.setRGB(0, 0, 0xFF747878.toInt())
        assertTrue(MonochromePaletteAudit.audit(i, false).passed)
        i.setRGB(0, 0, 0xFF737878.toInt())
        assertEquals(1, MonochromePaletteAudit.audit(i, false).chromatic)
    }
    @Test fun `disabled neighbour alone does not permit an edge`() {
        val i = img(2, 1, 0xFF808080.toInt()); i.setRGB(0, 0, 0xFF7A7A7A.toInt())
        assertEquals(1, MonochromePaletteAudit.audit(i, false).badPixels)
    }
    @Test fun `edge proximity is bounded at three pixels including diagonals`() {
        val i = img(5, 1, paper)
        i.setRGB(0, 0, ink)
        for (x in 1..4) i.setRGB(x, 0, 0xFF000000.toInt() or ((0x10 + 0x20 * x) * 0x010101))
        val r = MonochromePaletteAudit.audit(i, false)
        assertEquals(1, r.badPixels)
        assertEquals(Rectangle(4, 0, 1, 1), r.bounds)

        val diagonal = img(4, 4, 0xFF808080.toInt())
        diagonal.setRGB(0, 0, ink)
        diagonal.setRGB(3, 3, 0xFF777777.toInt())
        assertTrue(MonochromePaletteAudit.audit(diagonal, false).passed)
    }
    @Test fun `skinny three pixel fills fail in both orientations and themes`() {
        for (dark in listOf(false, true)) for ((w, h) in listOf(3 to 15, 15 to 3)) {
            val i = img(w + 2, h + 2, if (dark) ink else paper)
            for (x in 1..w) for (y in 1..h) i.setRGB(x, y, 0xFF777777.toInt())
            assertEquals(w * h, MonochromePaletteAudit.audit(i, dark).badPixels)
        }
    }
    @Test fun `smooth grey gradient fill fails even near paper`() {
        val i = img(9, 5, paper)
        for (x in 1..7) for (y in 1..3) {
            val grey = 0x60 + 2 * x
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        }
        assertEquals(21, MonochromePaletteAudit.audit(i, false).badPixels)
    }
    @Test fun `low variation fill threshold includes four but excludes five`() {
        for (range in listOf(4, 5)) {
            val i = img(5, 5, paper)
            for (x in 1..3) for (y in 1..3) i.setRGB(x, y, 0xFF606060.toInt())
            i.setRGB(2, 2, 0xFF000000.toInt() or ((0x60 + range) * 0x010101))
            assertEquals(if (range == 4) 9 else 0, MonochromePaletteAudit.audit(i, false).badPixels)
        }
    }
    @Test fun `rendered alpha grey text core fails in both themes`() {
        // RGB after compositing a non-palette, translucent glyph; PNG alpha is not source alpha.
        for (dark in listOf(false, true)) {
            val bg = if (dark) ink else paper
            val core = if (dark) 0xFF606060.toInt() else 0xFF9F9F9F.toInt()
            val edge = if (dark) 0xFF303030.toInt() else 0xFFCFCFCF.toInt()
            val i = img(15, 15, bg)
            for (y in 3..11) for (x in 4..8) i.setRGB(x, y, if (x in 5..7) core else edge)
            val r = MonochromePaletteAudit.audit(i, dark)
            assertFalse(r.passed)
            assertEquals(0, r.chromatic)
        }
    }
    @Test fun `disabled grey edges near paper pass without adding disabled as an endpoint`() {
        for (dark in listOf(false, true)) {
            val i = img(8, 8, if (dark) ink else paper)
            for (y in 2..5) {
                i.setRGB(3, y, 0xFF808080.toInt())
                i.setRGB(4, y, if (dark) 0xFF404040.toInt() else 0xFFBFBFBF.toInt())
            }
            assertTrue(MonochromePaletteAudit.audit(i, dark).passed)
        }
    }
    @Test fun `chroma in a high variation glyph remains an absolute failure`() {
        val i = img(7, 7, paper)
        for (x in 2..4) for (y in 2..4) i.setRGB(x, y, 0xFF000000.toInt() or ((20 + 30 * x + 20 * y) * 0x010101))
        i.setRGB(3, 3, 0xFF747A78.toInt())
        assertEquals(1, MonochromePaletteAudit.audit(i, false).chromatic)
    }
    @Test fun `real ReadingToolbar_full_mono glyph crop passes`() {
        // Exact RGB crop centered at 59 37 from the original diagnostic render, without resampling.
        val rows = """
            ff ff ff ff ff ff ff ff ff ff ff
            ff ff ff ff ff ff ff ff ff ff ff
            ff ff ff ff ff ff ff ff ff ff ff
            ff ff df 47 e7 ff e5 4f ae ff d8
            ff ff d8 00 e2 f3 42 67 fd ff f9
            ff ff d8 00 df 65 46 f5 ff ff d8
            ff ff d8 00 6a 05 e2 ff ff ff cf
            ff ff d8 00 1e 24 92 ff ff ff cf
            ff ff d8 00 c5 be 08 cf ff ff cf
            ff ff d8 00 e2 ff 79 37 f3 ff cf
            ff ff d8 00 e2 ff f3 34 78 ff cf
        """.trimIndent().lines().map { row -> row.split(" ").map { it.toInt(16) } }
        val i = img(rows.first().size, rows.size, paper)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, grey ->
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        } }
        assertTrue(MonochromePaletteAudit.audit(i, false).toString(), MonochromePaletteAudit.audit(i, false).passed)
    }
    @Test fun `real ReadingToolbar_full_mono_dark glyph crop passes`() {
        // Exact RGB crop centered at 67 40 from the original diagnostic render, without resampling.
        val rows = """
            87 00 51 f0 20 00 00 00 00 00 00
            0a 00 15 63 00 00 00 00 00 00 00
            00 00 51 dc 1e 66 d0 a9 e7 c3 17
            00 00 5e ff 23 77 fe 81 5c f8 7a
            00 00 5e ff 23 77 f7 00 00 e2 93
            00 00 5e ff 23 77 f7 00 00 e2 94
            23 00 5e ff 23 77 f7 00 00 e2 94
            bb 00 5e ff 23 77 f7 00 00 e2 94
            00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00
        """.trimIndent().lines().map { row -> row.split(" ").map { it.toInt(16) } }
        val i = img(rows.first().size, rows.size, paper)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, grey ->
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        } }
        assertTrue(MonochromePaletteAudit.audit(i, true).toString(), MonochromePaletteAudit.audit(i, true).passed)
    }
    @Test fun `real SpeakTransportBar_playing_mono glyph crop passes`() {
        // Exact RGB crop centered at 21 13 from the original diagnostic render, without resampling.
        val rows = """
            a8 e2 ff ff ff ff ff ff ff ff ff
            99 26 c7 ff ff ff ff ff ff ff ff
            ff a7 6b ff ff fe ca b1 e0 ff ff
            ff a7 67 ff ff 7c 64 9c 3b ca fb
            9b 29 cd ff d5 38 fb ff c0 5f f3
            34 b0 ff ff ac 27 5d 5d 52 33 ff
            b8 47 fd ff b5 5c e5 e5 e5 e7 df
            fe 4a b1 ff e7 21 df ff e9 be c7
            ff c8 2f f7 ff bc 31 4a 35 c7 fb
            ff ff ff ff ff ff fe ee fd ff ff
            ff ff ff ff ff ff ff ff ff ff ff
        """.trimIndent().lines().map { row -> row.split(" ").map { it.toInt(16) } }
        val i = img(rows.first().size, rows.size, paper)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, grey ->
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        } }
        assertTrue(MonochromePaletteAudit.audit(i, false).toString(), MonochromePaletteAudit.audit(i, false).passed)
    }
    @Test fun `real SpeakTransportBar_playing_mono_dark glyph crop passes`() {
        // Exact RGB crop centered at 24 14 from the original diagnostic render, without resampling.
        val rows = """
            00 00 00 00 00 00 00 00 00 00 00
            00 00 05 65 84 46 00 00 11 72 87
            00 00 b7 cb 9a e7 66 0f d8 b6 96
            00 57 e8 0f 00 72 ce 24 80 00 00
            00 89 f1 d0 d0 d8 eb 00 6d c8 d3
            00 7f d1 3e 3e 3e 3b 48 ec 3b 00
            00 3b f4 47 00 37 75 6a e4 0f 0f
            1b 00 77 ec dd ea 6a 11 ca ef e7
            00 00 00 05 2d 0a 00 00 00 26 0a
            00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00
        """.trimIndent().lines().map { row -> row.split(" ").map { it.toInt(16) } }
        val i = img(rows.first().size, rows.size, paper)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, grey ->
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        } }
        assertTrue(MonochromePaletteAudit.audit(i, true).toString(), MonochromePaletteAudit.audit(i, true).passed)
    }
    @Test fun `real BibleReferenceOverlay_visible_mono glyph crop passes`() {
        // Exact RGB crop centered at 176 368 from the original diagnostic render, without resampling.
        val rows = """
            ff ff ff ff ff ff ff ff ff ff ff
            ff ff ff ff ff ff ff ff ff ff ff
            ff ff ff ff ff ff ff ff ff ff ff
            fa ff ff ff ff eb 9a 79 8b c8 ff
            59 fd ff ff d8 19 2f 6d 47 00 8c
            00 bb ff ff 69 26 f3 ff fe 7b 00
            35 6c ff ff 5d 2c f4 ff ff ed c9
            2d 47 ff ff c7 0f 27 7f bf f6 ff
            6a 7d ff ff ff e8 95 4e 08 21 ae
            ff ff ff ff ff ff ff ff ee 6c 00
            dc f7 ff f3 23 8f ff ff ff ca 00
        """.trimIndent().lines().map { row -> row.split(" ").map { it.toInt(16) } }
        val i = img(rows.first().size, rows.size, paper)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, grey ->
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        } }
        assertTrue(MonochromePaletteAudit.audit(i, false).toString(), MonochromePaletteAudit.audit(i, false).passed)
    }
    @Test fun `real BibleReferenceOverlay_visible_mono_dark glyph crop passes`() {
        // Exact RGB crop centered at 150 370 from the original diagnostic render, without resampling.
        val rows = """
            00 00 00 00 00 00 00 00 00 00 00
            0a 78 b6 b9 87 13 00 00 00 83 86
            ca fe d0 d2 fd d3 0a 00 00 d3 eb
            ff 6d 00 00 7b ff 78 00 00 d3 ff
            cc 00 00 00 05 ea c5 00 00 d3 e8
            df b6 b6 b6 b6 ee df 00 00 d3 e5
            e2 c7 c7 c7 c7 c7 b6 00 00 d3 e5
            ae 00 00 00 00 00 00 00 00 d3 e5
            f0 21 00 00 00 4c 1b 00 00 d3 e5
            fe db 6e 4d 8a f8 9d 00 00 d3 e5
            6e e5 ff ff f6 a1 0d 00 00 d3 e5
        """.trimIndent().lines().map { row -> row.split(" ").map { it.toInt(16) } }
        val i = img(rows.first().size, rows.size, paper)
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, grey ->
            i.setRGB(x, y, 0xFF000000.toInt() or (grey * 0x010101))
        } }
        assertTrue(MonochromePaletteAudit.audit(i, true).toString(), MonochromePaletteAudit.audit(i, true).passed)
    }
    @Test fun `passing audit has no bounds`() {
        assertNull(MonochromePaletteAudit.audit(img(1, 1, ink), true).bounds)
    }
}

class MonochromeAuditPolicyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val pass = MonochromePaletteAudit.AuditResult(0, null, 0)
    private val fail = MonochromePaletteAudit.AuditResult(5, Rectangle(2, 3, 4, 5), 1)
    private fun policy(allow: Set<String> = emptySet(), exempt: Set<String> = emptySet()) =
        MonochromeAuditPolicy(allow, exempt)

    private fun MonochromeAuditPolicy.check(key: String, tag: String, result: MonochromePaletteAudit.AuditResult, image: File, report: String?) =
        check(key, listOf(MonochromeAuditPolicy.Render(tag, result, image),
            MonochromeAuditPolicy.Render(if (tag == "mono") "mono_dark" else "mono", result, image)), report)

    @Test fun `parses blank comments and inline reasons`() {
        assertEquals(setOf("Screen_state", "Other_state"), MonochromeAuditPolicy.parseEntries(
            "# header\n\n Screen_state # reason\nOther_state\nScreen_state\n".reader()))
    }
    @Test fun `exempt scene skips audit even when also allowlisted`() {
        assertFalse(policy(setOf("Scene_state"), setOf("Scene_state")).shouldAudit("Scene_state"))
        policy(exempt = setOf("Scene_state")).check("Scene_state", "mono", fail, File("scene.png"), null)
    }
    @Test fun `unlisted clean and allowlisted failing scenes pass`() {
        policy().check("Scene_state", "mono", pass, File("scene.png"), null)
        policy(setOf("Scene_state")).check("Scene_state", "mono_dark", fail, File("scene.png"), null)
    }
    @Test fun `unlisted failure reports pixels tag bounds and image path`() {
        val error = assertThrows(AssertionError::class.java) {
            policy().check("Scene_state", "mono_dark", fail, File("build/mono-audit/scene.png"), "")
        }
        assertTrue(error.message!!.contains("Scene_state_mono_dark: 5 non-monochrome px (1 chromatic)"))
        assertTrue(error.message!!.contains("java.awt.Rectangle[x=2,y=3,width=4,height=5]"))
        assertTrue(error.message!!.contains("build/mono-audit/scene.png"))
    }
    @Test fun `allowlisted light pass dark fail is not stale`() {
        val p = policy(setOf("Scene_state"))
        p.check("Scene_state", listOf(
            MonochromeAuditPolicy.Render("mono", pass, File("scene.png")),
            MonochromeAuditPolicy.Render("mono_dark", fail, File("scene-dark.png"))), null)
    }
    @Test fun `allowlisted light fail dark pass is not stale`() {
        val p = policy(setOf("Scene_state"))
        p.check("Scene_state", listOf(
            MonochromeAuditPolicy.Render("mono", fail, File("scene.png")),
            MonochromeAuditPolicy.Render("mono_dark", pass, File("scene-dark.png"))), null)
    }
    @Test fun `stale allowlist throws`() {
        val error = assertThrows(AssertionError::class.java) {
            policy(setOf("Scene_state")).check("Scene_state", "mono", pass, File("scene.png"), null)
        }
        assertTrue(error.message!!.contains("remove it from monochrome-audit-allowlist.txt"))
    }
    @Test fun `report mode appends both failure types without throwing`() {
        val report = File(temporaryFolder.root, "nested/report.txt")
        policy().check("Bad_state", "mono", fail, File("scene.png"), report.path)
        policy(setOf("Clean_state")).check("Clean_state", "mono", pass, File("scene.png"), report.path)
        assertEquals(3, report.readLines().size)
        assertTrue(report.readLines()[0].startsWith("Bad_state_mono:"))
        assertTrue(report.readLines()[1].startsWith("Bad_state_mono_dark:"))
        assertTrue(report.readLines()[2].startsWith("Clean_state passes"))
    }
}
