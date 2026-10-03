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

package net.bible.android.view.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import net.bible.android.TEST_SDK
import net.bible.sharedui.components.htmlToAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * C1 fix: [htmlToAnnotatedString] must give every link an EXPLICIT
 * [androidx.compose.ui.text.LinkInteractionListener], not rely on whatever `LocalUriHandler` a
 * `Text`'s ambient composition happens to supply -- see `AbHtmlText.kt`'s kdoc for why (a
 * `Dialog`/`Popup`/sheet window re-provides `LocalUriHandler` internally, silently shadowing any
 * override installed outside it). This is a pure, non-Composable check of the built
 * [androidx.compose.ui.text.AnnotatedString]'s link annotations; it fails on the pre-fix
 * `LinkAnnotation.Url(href, linkStyles)` (no listener) form.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class HtmlToAnnotatedStringTest {

    @Test fun everyLinkAnnotationCarriesANonNullListener() {
        val text = htmlToAnnotatedString("a <a href=\"https://x.org\">here</a> b", Color.Blue) {}
        val links = text.getLinkAnnotations(0, text.length).map { it.item as LinkAnnotation.Url }
        assertEquals(1, links.size)
        assertNotNull(links.single().linkInteractionListener)
    }

    @Test fun theListenerCallsOpenWithTheLinksUrl() {
        val opened = mutableListOf<String>()
        val text = htmlToAnnotatedString("<a href=\"https://x.org\">here</a>", Color.Blue) { opened += it }
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Url
        link.linkInteractionListener?.onClick(link)
        assertEquals(listOf("https://x.org"), opened)
    }

    @Test fun plainTextHasNoLinkAnnotations() {
        val text = htmlToAnnotatedString("no links here", Color.Blue) {}
        assertEquals(0, text.getLinkAnnotations(0, text.length).size)
    }
}
