package net.bible.sharedui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The iOS app on a Mac corrects Compose's insets through `LocalPlatformWindowInsets` (`IosAppOnMacInsets.kt`: no
 * phantom keyboard, no window-controls band at the top). CMP's inset modifiers bypass that local: they read the scene's
 * raw insets from the root node. So shared UI pads a top or IME inset only through `WindowInsets.*`
 * (`windowInsetsPadding`, Material's defaults), never through these modifiers; `navigationBarsPadding()` (bottom only)
 * stays allowed.
 */
class InsetModifierGuardTest {
    private val roots = listOf(File("src/commonMain/kotlin"), File("src/iosMain/kotlin"))
    private val banned = listOf(
        "imePadding", "statusBarsPadding", "systemBarsPadding", "safeDrawingPadding", "safeContentPadding",
        "safeGesturesPadding", "displayCutoutPadding", "captionBarPadding", "mandatorySystemGesturesPadding",
        "systemGesturesPadding", "waterfallPadding",
    )
    private val call = Regex("""\.(${banned.joinToString("|")})\(""")

    internal fun offenders(files: List<File>): List<String> = files
        .filter { it.extension == "kt" }
        .flatMap { f ->
            f.readLines().withIndex()
                .filter { (_, line) -> line.trimStart().let { !it.startsWith("*") && !it.startsWith("//") } }
                .filter { (_, line) -> call.containsMatchIn(line.substringBefore("//")) }
                .map { (i, _) -> "${f.path}:${i + 1}" }
        }

    @Test fun sharedUiUsesNoRawInsetModifier() =
        assertEquals(emptyList(), offenders(roots.flatMap { it.walkTopDown().toList() }))

    @Test fun theScanSeesTheSources() =
        assertTrue(roots.all { it.isDirectory }, "run from sharedUi/: the scan would pass vacuously")

    @Test fun theGuardCanFail() {
        val probe = File.createTempFile("insetProbe", ".kt").apply {
            writeText("val m = Modifier.fillMaxSize().imePadding()\n// Modifier.statusBarsPadding() in a comment\n")
            deleteOnExit()
        }
        assertEquals(listOf("${probe.path}:1"), offenders(listOf(probe)))
    }
}
