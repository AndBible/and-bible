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

import androidx.compose.foundation.Image
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.text.InlineTextContent
import net.bible.sharedcore.ui.dialog.HtmlRun
import net.bible.sharedcore.ui.dialog.parseHtmlRuns
import net.bible.sharedui.strings.LocalStrings

private const val LEADING_ICON_ID = "leadingIcon"

/**
 * A dialog body's link opener. [LocalAbLinkOpener] is a CUSTOM composition local rather than
 * `LocalUriHandler` (C1 fix): material3's `AlertDialog`/`Dialog`/`Popup`/`ModalBottomSheet` window is
 * its own child composition, and its `ProvideCommonCompositionLocals` re-provides a fresh
 * `LocalUriHandler` INSIDE that window — silently shadowing whatever `AbLinkRouting`/`AppDialogHost`
 * installed outside it, so a click reached the platform's bare `AndroidUriHandler` (Chrome, with no
 * discrete-mode question) instead of the host's override. A custom local is never re-provided by the
 * platform, so it survives crossing into a `Dialog`/`Popup`/sheet window.
 */
fun interface AbLinkOpener { fun open(url: String) }
val LocalAbLinkOpener = staticCompositionLocalOf<AbLinkOpener?> { null }

/**
 * Builds the [AnnotatedString] for a dialog's HTML body. Every link is a [LinkAnnotation.Url] with
 * an EXPLICIT [androidx.compose.ui.text.LinkInteractionListener] that calls [open] directly (C1
 * fix) — not the no-listener form that falls through to whatever `LocalUriHandler` the Text's own
 * composition happens to read, which a `Dialog`/`Popup` window can (and does) shadow.
 */
fun htmlToAnnotatedString(html: String, linkColor: Color, open: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    for (run in parseHtmlRuns(html)) {
        val style = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
            fontSize = when (run.size) { HtmlRun.Size.Big -> 1.25.em; HtmlRun.Size.Small -> 0.8.em; HtmlRun.Size.Normal -> androidx.compose.ui.unit.TextUnit.Unspecified },
        )
        val href = run.href
        if (href != null) {
            withLink(LinkAnnotation.Url(href, linkStyles) { open((it as LinkAnnotation.Url).url) }) {
                withStyle(style) { append(run.text) }
            }
        } else {
            withStyle(style) { append(run.text) }
        }
    }
}

/**
 * [LocalAbLinkOpener] when present (every production render path: [AbLinkRouting]/`AppDialogHost`),
 * else [LocalUriHandler] (a bare `AbHtmlText()` with neither wrapper, e.g. a scratch/preview render
 * or a future call site that forgot to wrap — fails OPEN there, exactly like before this fix, rather
 * than crashing).
 *
 * [leadingIcon], when non-null, is rendered as an [InlineTextContent] glyph flowing at the START of
 * the text (`AppDialogRequest.NoticeBlock.IconLine` — the `$` sponsor icon that used to be an
 * `ImageSpan` prepended to a classic `AlertDialog`'s spanned body). It is tinted [leadingIconTint]
 * (today's `getTintedDrawable`, i.e. `MaterialTheme.colorScheme.onSurface` for the money icon).
 */
@Composable
fun AbHtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    leadingIcon: Painter? = null,
    leadingIconTint: Color = LocalContentColor.current,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val opener = LocalAbLinkOpener.current
    val fallback = LocalUriHandler.current
    val text = remember(html, linkColor, opener, leadingIcon) {
        buildAnnotatedString {
            if (leadingIcon != null) appendInlineContent(LEADING_ICON_ID, "[icon]")
            append(htmlToAnnotatedString(html, linkColor) { u -> opener?.open(u) ?: fallback.openUri(u) })
        }
    }
    val inlineContent = if (leadingIcon != null) {
        mapOf(
            LEADING_ICON_ID to InlineTextContent(
                Placeholder(width = 1.1.em, height = 1.1.em, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
            ) {
                Image(leadingIcon, contentDescription = null, colorFilter = ColorFilter.tint(leadingIconTint))
            },
        )
    } else emptyMap()
    Text(text = text, modifier = modifier, style = style, inlineContent = inlineContent)
}

/**
 * Routes every link under [content] through [LocalAbLinkOpener] (C1 fix) instead of
 * `LocalUriHandler`, which a `Dialog`/`Popup`/`ModalBottomSheet` window re-provides internally and
 * so cannot be relied on to carry a host's override across that boundary (see [LocalAbLinkOpener]'s
 * kdoc). `AppDialogHost` wraps the whole app-wide dialog queue in this; a feature dialog with its
 * own inline link (App settings' discrete help, the EPUB search help, the reading view's speak/help
 * dialogs, …) wraps itself the same way, since its destination's ambient locals are the bare
 * platform ones otherwise.
 *
 * [askFirst] is `CommonUtils.isDiscrete` — when true, a tapped link does not open immediately: this
 * composable draws its OWN "open external link?" [AbConfirmDialog] ON TOP of [content] (drawn first,
 * so its window is created first and the question's sits above it — the same creation-order
 * mechanism `AppDialogHost` already uses for a Progress-under-an-answerable-request). [onOpenExternal]
 * is the actual non-asking open call (`CommonUtils.openLinkNow`); it runs directly when [askFirst] is
 * false, and only after the question's own confirm otherwise. This keeps the question a CHILD of the
 * dialog that owns the link (drawn here, not through `AppDialogController`'s queue), so it is never
 * hidden behind an unrelated request and never needs a second "priority" queue slot.
 */
@Composable
fun AbLinkRouting(askFirst: Boolean, onOpenExternal: (String) -> Unit, content: @Composable () -> Unit) {
    var pending by remember { mutableStateOf<String?>(null) }
    // A fresh AbLinkOpener every recomposition (never remember-keyed on askFirst/onOpenExternal):
    // both are read fresh here, so this can never close over a stale isDiscrete/callback the way
    // keying on them could if a caller passes a fresh lambda identity every recomposition (the
    // common case) while some OTHER key held remember from recomputing.
    val opener = AbLinkOpener { url -> if (askFirst) pending = url else onOpenExternal(url) }
    CompositionLocalProvider(LocalAbLinkOpener provides opener, content = content)
    pending?.let { url ->
        val strings = LocalStrings.current
        AbConfirmDialog(
            title = strings.externalLink,
            message = strings.externalLinkQuestion(url),
            confirmText = strings.okay,
            dismissText = strings.cancel,
            onConfirm = { pending = null; onOpenExternal(url) },
            onDismiss = { pending = null },
        )
    }
}
