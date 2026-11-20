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

package net.bible.sharedui.components.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.theme.LocalDisableAnimations
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoInk

/**
 * One answer of an options dialog as a bordered card: the [DialogAnswer.label] (15sp medium) and, when given, its
 * [DialogAnswer.consequence] (13sp, secondary colour). A destructive answer's label takes the colour and weight of
 * [DialogButtonRole.Destructive] (bold ink in pure monochrome). At least 48dp tall (touch target). Tagged `dialog-answer-<value>`.
 * With animations off the press feedback is a static tint instead of a ripple.
 */
@Composable
fun AbAnswerCard(answer: DialogAnswer, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mono = isPureMonochrome()
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AbDialogTokens.CardCorner)
    val borderColor = if (mono) monoInk(LocalIsDarkTheme.current) else scheme.outline
    val labelLook = if (answer.destructive) dialogButtonLook(DialogButtonRole.Destructive, mono, scheme)
    else DialogButtonLook(scheme.onSurface, FontWeight.Medium)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val staticPress = LocalDisableAnimations.current
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("dialog-answer-${answer.value}")
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .then(if (staticPress && pressed) Modifier.background(scheme.onSurface.copy(alpha = 0.12f)) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = if (staticPress) null else ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = AbDialogTokens.CardPaddingH, vertical = AbDialogTokens.CardPaddingV),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            answer.label,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = AbDialogTokens.CardLabelSize, fontWeight = labelLook.weight),
            color = labelLook.color,
        )
        if (answer.consequence != null) {
            Text(
                answer.consequence!!,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = AbDialogTokens.CardConsequenceSize),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
