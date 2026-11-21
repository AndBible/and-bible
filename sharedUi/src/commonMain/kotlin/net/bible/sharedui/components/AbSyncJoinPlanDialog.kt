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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.DialogAnswer
import net.bible.sharedui.components.dialog.AbDialogButton
import net.bible.sharedui.components.dialog.AbDialogShell
import net.bible.sharedui.components.dialog.AbDialogTokens
import net.bible.sharedui.components.dialog.DialogButtonRole
import net.bible.sharedui.components.dialog.abDialogDetailStyle
import net.bible.sharedui.components.dialog.dialogButtonLook
import net.bible.sharedui.theme.LocalIsDarkTheme
import net.bible.sharedui.theme.isPureMonochrome
import net.bible.sharedui.theme.monoInk

/**
 * The collected "the cloud already has data" plan of a sync start ([AppDialogRequest.SyncJoinPlan]), on [AbDialogShell].
 *
 * Step 0 asks one question per category: a label and a full-width dropdown showing the chosen answer, whose menu lists
 * each answer's label and consequence. Step 1 confirms everything that will happen, one line per category; when any
 * chosen answer is destructive the line is marked and the confirm button reads `replaceText` with the warning box
 * above it. All text comes from [request]. Neither the scrim nor back dismisses either step: only Cancel/Back leave
 * (a request without rows has nothing to go back to, so Back is Cancel). [initialStep] and [initialChoices] are the golden seam.
 *
 * A destructive answer is never told apart by hue alone: it carries a warning icon and bold weight, and in pure
 * monochrome the ink colour.
 */
@Composable
fun AbSyncJoinPlanDialog(
    request: AppDialogRequest.SyncJoinPlan,
    onResult: (AppDialogResult) -> Unit,
    initialStep: Int = 0,
    initialChoices: Map<String, String> = emptyMap(),
) {
    var step by remember { mutableIntStateOf(initialStep) }
    val chosen = remember { mutableStateMapOf<String, String>().apply { request.rows.forEach { put(it.key, initialChoices[it.key] ?: request.defaultChoice) } } }
    fun answer(value: String?) = request.choices.firstOrNull { it.value == value }
    val anyDestructive = request.rows.any { answer(chosen[it.key])?.destructive == true }
    val cancel = { onResult(AppDialogResult.Cancel) }

    AbDialogShell(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        lead = (if (step == 0) request.title else request.confirmTitle)?.let { t -> { Text(t) } },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == 0) {
                    Text(request.lead)
                    request.rows.forEach { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.label, style = MaterialTheme.typography.titleSmall)
                            ChoiceDropdown(
                                tag = row.key, selected = answer(chosen[row.key]) ?: request.choices.first(),
                                choices = request.choices, onSelect = { chosen[row.key] = it.value },
                            )
                        }
                    }
                    request.uploadNotes.forEach { Text(it, style = abDialogDetailStyle()) }
                } else {
                    Text(request.confirmLead)
                    request.rows.forEach { row ->
                        val destructive = answer(chosen[row.key])?.destructive == true
                        OutcomeLine(request.outcomeLine(row.key, chosen[row.key] ?: request.defaultChoice), destructive, "join-outcome-${row.key}")
                    }
                    request.createOutcomes.forEach { OutcomeLine(it, destructive = false, tag = "join-create-outcome") }
                    if (anyDestructive) WarningBox(request.warning)
                }
            }
        },
        buttons = {
            if (step == 0) {
                AbDialogButton(request.cancelText, DialogButtonRole.Cancel, cancel)
                AbDialogButton(request.nextText, DialogButtonRole.Confirm, { step = 1 })
            } else {
                AbDialogButton(request.backText, DialogButtonRole.Cancel, { if (request.rows.isEmpty()) cancel() else step = 0 })
                AbDialogButton(
                    if (anyDestructive) request.replaceText else request.startText,
                    if (anyDestructive) DialogButtonRole.Destructive else DialogButtonRole.Confirm,
                    { onResult(AppDialogResult.SyncJoinChoices(chosen.toMap())) },
                )
            }
        },
    )
}

/** A full-width bordered field showing [selected]; tapping opens a menu of [choices] (label + consequence). */
@Composable
private fun ChoiceDropdown(tag: String, selected: DialogAnswer, choices: List<DialogAnswer>, onSelect: (DialogAnswer) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val mono = isPureMonochrome()
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(AbDialogTokens.CardCorner)
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("join-row-$tag")
                .border(BorderStroke(1.dp, if (mono) monoInk(LocalIsDarkTheme.current) else scheme.outline), shape)
                .clickable(role = Role.DropdownList) { expanded = true }
                .padding(horizontal = AbDialogTokens.CardPaddingH, vertical = AbDialogTokens.CardPaddingV),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChoiceLabel(selected, Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        AbDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    modifier = Modifier.testTag("join-choice-${choice.value}"),
                    text = {
                        Column {
                            ChoiceLabel(choice)
                            choice.consequence?.let { Text(it, style = abDialogDetailStyle(), color = scheme.onSurfaceVariant) }
                        }
                    },
                    onClick = { onSelect(choice); expanded = false },
                )
            }
        }
    }
}

/** A choice label; a destructive one gets a warning icon, bold weight and the error colour (ink in monochrome). */
@Composable
private fun ChoiceLabel(choice: DialogAnswer, modifier: Modifier = Modifier) {
    if (!choice.destructive) {
        Text(choice.label, modifier, style = MaterialTheme.typography.bodyLarge)
        return
    }
    val look = dialogButtonLook(DialogButtonRole.Destructive, isPureMonochrome(), MaterialTheme.colorScheme)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = look.color, modifier = Modifier.size(18.dp))
        Text(choice.label, style = MaterialTheme.typography.bodyLarge, color = look.color, fontWeight = look.weight)
    }
}

@Composable
private fun OutcomeLine(text: String, destructive: Boolean, tag: String) {
    if (!destructive) {
        Text(text, Modifier.testTag(tag))
        return
    }
    val look = dialogButtonLook(DialogButtonRole.Destructive, isPureMonochrome(), MaterialTheme.colorScheme)
    Row(Modifier.testTag(tag), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = look.color, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Text(text, color = look.color, fontWeight = look.weight)
    }
}

@Composable
private fun WarningBox(text: String) {
    val mono = isPureMonochrome()
    val scheme = MaterialTheme.colorScheme
    val look = dialogButtonLook(DialogButtonRole.Destructive, mono, scheme)
    Row(
        Modifier.fillMaxWidth().testTag("join-warning")
            .border(BorderStroke(if (mono) 2.dp else 1.dp, if (mono) monoInk(LocalIsDarkTheme.current) else scheme.error), RoundedCornerShape(AbDialogTokens.CardCorner))
            .padding(AbDialogTokens.CardPaddingH),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = look.color, modifier = Modifier.size(20.dp))
        Text(text, style = abDialogDetailStyle(), fontWeight = look.weight)
    }
}
