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
package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedcore.ui.dialog.parseHtmlRuns
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * I1: [parseHtmlRuns] against the REAL `R.raw.license` body (the licence Message's own text,
 * `MenuCommandHandlerDialogTest.appLicencePostsTheRawLicenseTextWithOneOkButton` already pins that
 * the whole raw file is posted as `request.message` unmodified) -- proves the comment/list/heading
 * fixes hold on the actual content, not just fixtures. Fails today on the 16-line header comment:
 * every emitted run's text still contains a literal `<!--`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class LicenseHtmlRunsTest {
    // Bounded so genuine content is never mistaken for a leftover tag -- the GPL's own boilerplate
    // literally contains "<https://www.gnu.org/licenses/>" as escaped, decoded plain text, whose
    // "<h" (from "http") a bare substring check would wrongly flag.
    private val leftoverLi = Regex("""<li[\s>]""")
    private val leftoverHeading = Regex("""<h[1-6][\s>]""")

    @Test fun theWholeLicenseParsesWithNoLeftoverMarkup() {
        val html = RuntimeEnvironment.getApplication().resources
            .openRawResource(R.raw.license).readBytes().decodeToString()
        val runs = parseHtmlRuns(html)
        assertTrue("the file must actually produce runs", runs.isNotEmpty())
        for (run in runs) {
            assertFalse("run text must not contain a leftover comment start: ${run.text}", run.text.contains("<!--"))
            assertFalse("run text must not contain a leftover comment end: ${run.text}", run.text.contains("-->"))
            assertFalse("run text must not contain a leftover <li: ${run.text}", leftoverLi.containsMatchIn(run.text))
            assertFalse("run text must not contain a leftover heading tag: ${run.text}", leftoverHeading.containsMatchIn(run.text))
        }
        val text = runs.joinToString("") { it.text }
        assertTrue(text.trimStart().startsWith("GNU GENERAL PUBLIC LICENSE"))
    }
}
