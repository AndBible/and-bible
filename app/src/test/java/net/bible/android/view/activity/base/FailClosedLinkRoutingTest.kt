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
package net.bible.android.view.activity.base

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.components.AbHtmlText
import net.bible.sharedui.components.AbInfoDialog
import net.bible.sharedui.components.AbLinkRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 31 addendum item 3 (correction 12, parked in run 2): [FailClosedLinkRouting] is provided at
 * every Compose host root so a bare [AbHtmlText] link that forgot its own [net.bible.sharedui.components.AbLinkRouting]
 * wrap still asks before opening in discrete mode. Renders exactly that shape -- a bare `AbHtmlText`
 * with no dialog/own-wrap around it, directly under [FailClosedLinkRouting] the way
 * `NavHostComposeActivity`/`CalculatorComposeActivity`/`ComposeReadingViewHost`'s `AbAppTheme` roots
 * do -- and proves a tap asks (the "open external link?" confirmation appears) rather than opening
 * (opening for real would need a live `CurrentActivityHolder.currentActivity`, which a bare
 * `createComposeRule` test has none of -- the ask-branch never reaches that call, which is exactly
 * the point).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = TestBibleApplication::class)
class FailClosedLinkRoutingTest {
    @get:Rule val compose = createComposeRule()

    @After
    fun tearDown() {
        CommonUtils.realSharedPreferences.edit().remove("discrete_mode").apply()
    }

    @Test fun aBareLinkUnderTheHostRoot_inDiscreteMode_asksRatherThanOpens() {
        CommonUtils.realSharedPreferences.edit().putBoolean("discrete_mode", true).apply()
        compose.setContent {
            AbAppTheme {
                FailClosedLinkRouting {
                    AbHtmlText(html = "<a href=\"https://example.com\">tap me</a>")
                }
            }
        }
        compose.onNodeWithText("tap me").performClick()
        // The fail-closed question dialog (title = Strings.externalLink) is now on top -- the link
        // was never opened directly (which would have thrown, with no CurrentActivityHolder set up).
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.onNodeWithText(context.getString(R.string.external_link)).assertExists()
    }

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) { opened += uri }
    }

    private fun setInfoDialog(askFirst: Boolean, opened: MutableList<String>, platform: RecordingUriHandler) {
        compose.setContent {
            AbAppTheme {
                CompositionLocalProvider(LocalUriHandler provides platform) {
                    AbLinkRouting(askFirst = askFirst, onOpenExternal = { opened += it }) {
                        AbInfoDialog(
                            title = "Help", body = "Body", onDismiss = {},
                            readMoreLabel = "Read more", readMoreUrl = "https://example.org/x",
                        )
                    }
                }
            }
        }
    }

    @Test fun infoDialogReadMoreGoesThroughTheLinkOpener() {
        val opened = mutableListOf<String>()
        val platform = RecordingUriHandler()
        setInfoDialog(askFirst = false, opened = opened, platform = platform)
        compose.onNodeWithText("Read more").performClick()
        assertEquals(listOf("https://example.org/x"), opened)
        assertTrue("platform UriHandler must not be used", platform.opened.isEmpty())
    }

    @Test fun infoDialogReadMoreAsksFirstWhenRoutingSaysSo() {
        val opened = mutableListOf<String>()
        val platform = RecordingUriHandler()
        setInfoDialog(askFirst = true, opened = opened, platform = platform)
        compose.onNodeWithText("Read more").performClick()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.onNodeWithText(context.getString(R.string.external_link)).assertExists()
        assertTrue(opened.isEmpty())
        assertTrue(platform.opened.isEmpty())
    }
}
