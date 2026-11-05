package net.bible.sharedlogic.sync

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** New monitor sites require an explicit audit: a target must never mean a process-wide Native lock. */
class MonitorUsageGuardTest {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "sharedLogic/src").isDirectory }

    @Test fun nativeMonitorsMustNotShareAProcessWideMutex() {
        val implementation = File(root, "sharedLogic/src/iosMain/kotlin/net/bible/sharedlogic/sync/Monitors.ios.kt").readText()
        assertFalse(Regex("(?:object|class)\\s+GlobalMonitor\\b").containsMatchIn(implementation),
            "Native monitor targets must own independent locks; GlobalMonitor ignores target identity (#3978)")
    }

    @Test fun everyProductionMonitorSiteHasAnAuditReason() {
        val modules = listOf("jsword", "sharedCore", "sharedData", "sharedLogic", "sharedUi", "kdom")
        val actual = modules.flatMap { module ->
            File(root, "$module/src").walkTopDown().filter {
                it.isFile && it.extension == "kt" && it.relativeTo(File(root, "$module/src")).path
                    .substringBefore('/').endsWith("Main")
            }.mapNotNull { file ->
                val count = monitorCalls(file.readText())
                if (count == 0) null else file.relativeTo(root).invariantSeparatorsPath to count
            }.toList()
        }.toMap()
        val manifest = File(root, "docs/monitor-usage-allowlist.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }.associate { line ->
                val fields = line.split('|', limit = 3)
                assertTrue(fields.size == 3 && fields[2].trim().isNotEmpty(), "Missing audit reason: $line")
                fields[0].trim() to fields[1].trim().toInt()
            }
        assertEquals(manifest, actual, "Monitor inventory changed: audit each new site and document its lock scope/reason")
    }

    @Test fun scannerDetectsAnUnlistedCallButNotImportsOrComments() {
        assertEquals(1, monitorCalls("fun newCode() = withMonitor(Any()) { foreignCall() }"))
        assertEquals(0, monitorCalls("import net.bible.sharedlogic.sync.withMonitor\n// withMonitor(a) {}\n/* monitorWait(a) */"))
    }

    private fun monitorCalls(source: String): Int {
        val withoutComments = source.replace(Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*"), "")
        return Regex("\\b(?:withMonitor|monitorWait|monitorNotifyAll)\\s*\\(").findAll(withoutComments).count()
    }
}
