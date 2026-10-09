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
