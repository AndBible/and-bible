/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.desktop

import net.bible.sharedui.desktop.update.DesktopChannel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop packages are beta (beta notice, beta features) when built from a `beta-*` or `test-*` tag. The CI
 * workflow turns the tag into the `desktopReleaseKind` the launcher passes as `andbible.releaseKind`; this checks
 * that every tag the workflow maps reaches [DesktopChannel.isBetaBuild] with the intended answer, so a renamed kind
 * cannot silently drop the notice from beta packages (or add it to production ones).
 */
class DesktopBetaBuildWorkflowTest {
    private val linuxAssignment = Regex("""REF_NAME" == ([a-z]+)-\* ]]; then kind=([a-z]+); fi""")
    private val windowsAssignment = Regex("""REF_NAME -like '([a-z]+)-\*'\) \{ \${'$'}kind = '([a-z]+)' }""")
    private val workflow = File("../.github/workflows/desktop.yml").readText()

    private fun kinds(pattern: Regex) = pattern.findAll(workflow).map { it.groupValues[1] to it.groupValues[2] }.toSet()

    @Test
    fun linuxAndWindowsPackagesUseTheSameKinds() {
        assertEquals(kinds(linuxAssignment), kinds(windowsAssignment))
    }

    @Test
    fun workflowReleaseKindsMapToTheBetaDecision() {
        val kinds = kinds(linuxAssignment)
        assertEquals(setOf("test" to "test", "production" to "production", "beta" to "beta"), kinds)
        val expected = mapOf("test" to true, "beta" to true, "production" to false)
        kinds.forEach { (tagPrefix, kind) ->
            val beta = DesktopChannel.isBetaBuild({ if (it == "andbible.releaseKind") kind else null }, { null })
            assertEquals(expected.getValue(tagPrefix), beta, "tag $tagPrefix-* (kind $kind)")
        }
        // Untagged builds and `gradle run` keep the default kind.
        assertTrue(workflow.contains("kind=dev"))
        assertEquals(false, DesktopChannel.isBetaBuild({ if (it == "andbible.releaseKind") "dev" else null }, { null }))
    }
}
