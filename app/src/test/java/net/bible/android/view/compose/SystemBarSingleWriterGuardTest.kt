package net.bible.android.view.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fix batch 2 §2.4: the nav host has exactly one system-bar writer, `SystemBarController`. */
class SystemBarSingleWriterGuardTest {
    private val host = File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")

    private val banned = listOf(
        Regex("""systemUiVisibility\s*="""),
        Regex("""windowInsetsController\s*\?\.\s*apply"""),
        Regex("""\.(hide|show)\(\s*WindowInsets(Compat)?\.Type\."""),
        Regex("""setSystemBarsAppearance\("""),
    )

    internal fun offendingLines(text: String): List<String> = text.lines()
        .map { it.substringBefore("//") }
        .filter { line -> banned.any { it.containsMatchIn(line) } }

    @Test fun theNavHostHasNoRawSystemBarWrites() =
        assertEquals(emptyList<String>(), offendingLines(host.readText()))

    @Test fun theScanSeesTheSource() = assertTrue("run from app/", host.isFile)

    @Test fun theGuardCanFail() {
        assertEquals(1, offendingLines("window.decorView.systemUiVisibility = uiFlags").size)
        assertEquals(1, offendingLines("c.hide(WindowInsets.Type.statusBars())").size)
        assertEquals(1, offendingLines("window.decorView.windowInsetsController?.apply {").size)
        assertEquals(0, offendingLines("// window.decorView.systemUiVisibility = uiFlags").size)
    }
}
