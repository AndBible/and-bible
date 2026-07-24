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

package net.bible.sharedui.reading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Classic `bible_ref_overlay_offset` = 80dip (`res/values/dimens.xml`). */
private val OverlayBottomOffset = 80.dp

/**
 * The floating current-reference capsule shown at the bottom-centre of the reading area in
 * fullscreen — the Compose port of classic `SplitBibleArea.bibleReferenceOverlay`
 * (`screen/SplitBibleArea.kt:139-148, 646-671`). Rendered as a sibling inside [SplitContent]'s
 * bottom-overlay slot (never wrapping the panes). Visibility is decided upstream by
 * `bibleReferenceOverlayVisible` (`:sharedCore`); this composable only fades [text] in/out on
 * [visible]. Styling borrows iOS's `ultraThinMaterial` capsule (translucent surface + hairline
 * outline); theme-role colours so BW/e-ink degrade. [text] is `abbr:reference` (Android-faithful).
 *
 * The fade honours `AbTheme`'s disable-animations flag automatically: under `LocalInspectionMode`
 * (Roborazzi/preview) `AnimatedVisibility` renders the final (visible) frame, so goldens are stable.
 */
@Composable
fun BoxScope.BibleReferenceOverlay(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.align(Alignment.BottomCenter).padding(bottom = OverlayBottomOffset),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.87f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 3.dp,
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
