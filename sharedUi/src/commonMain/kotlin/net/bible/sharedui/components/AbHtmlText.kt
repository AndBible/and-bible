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

package net.bible.sharedui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import net.bible.sharedcore.ui.dialog.HtmlRun
import net.bible.sharedcore.ui.dialog.parseHtmlRuns

/**
 * Builds the [AnnotatedString] for a dialog's HTML body. Links are [LinkAnnotation.Url] with no
 * listener, so a click goes to `LocalUriHandler` — which `AppDialogHost` points at the host's
 * `CommonUtils.openLink` (discrete mode asks first).
 */
fun htmlToAnnotatedString(html: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    for (run in parseHtmlRuns(html)) {
        val style = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontSize = when (run.size) { HtmlRun.Size.Big -> 1.25.em; HtmlRun.Size.Small -> 0.8.em; HtmlRun.Size.Normal -> androidx.compose.ui.unit.TextUnit.Unspecified },
        )
        val href = run.href
        if (href != null) {
            withLink(LinkAnnotation.Url(href, linkStyles)) { withStyle(style) { append(run.text) } }
        } else {
            withStyle(style) { append(run.text) }
        }
    }
}

@Composable
fun AbHtmlText(html: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, linkColor) { htmlToAnnotatedString(html, linkColor) }
    Text(text = text, modifier = modifier, style = style)
}
