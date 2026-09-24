package net.bible.android.view.compose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Slice 8 §5.3: `MainBibleActivity` is deleted. F1: nothing reaches its former nested types through it.
 * (Task F4 adds the file/manifest/layout assertions.)
 */
class MainBibleActivityRemovalGuardTest {

    private val nested = listOf(
        "SystemInsetsChangedEvent", "KeyIsNull", "AgentLogOffsetsUpdated", "SearchSheetOffsetsUpdated",
        "ImePaddingChanged", "FullScreenEvent", "UpdateRestoreWindowButtons", "ConfigurationChanged",
        "MainBibleAfterRestore", "UpdateMainBibleActivityDocuments", "WORKSPACE_CHANGED",
    )

    @Test
    fun nothingReachesTheReadingEventsThroughMainBibleActivity() {
        val pattern = Regex("""MainBibleActivity\.(""" + nested.joinToString("|") + """)\b""")
        val offenders = listOf(File("src/main/java"), File("src/test/java")).flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }
                .filter { f -> f.readLines().any { l -> !l.trimStart().startsWith("*") && !l.trimStart().startsWith("//") && pattern.containsMatchIn(l) } }
                .map { it.name }.toList()
        }.sorted()
        assertEquals(
            "these files still reach a former MainBibleActivity nested type through the class; they live top " +
                "level in net.bible.android.view.activity.page since slice 8 F1 (same names)",
            emptyList<String>(),
            offenders,
        )
    }
}
