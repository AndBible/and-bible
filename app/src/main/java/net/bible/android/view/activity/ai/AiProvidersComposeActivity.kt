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
package net.bible.android.view.activity.ai

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiProvidersScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Compose host for the AI providers list/edit screen — the new-path twin of classic
 * [AiProvidersActivity]/[AiProvidersFragment]. Wires the shared [AiProvidersController] over
 * [LlmProviderService] and renders [AiProvidersScreen].
 *
 * Two things stay host-side (Android-resource / dialog concerns the shared layer can't own):
 * - **Disclaimer gate**: [onAdd] runs [ensureDisclaimerAccepted] before opening the add-provider
 *   dialog (ports classic `AiSettingsFragmentBase.ensureDisclaimerAccepted` — the HTML disclaimer is
 *   built from ~13 string resources), then calls [AiProvidersController.startAdd].
 * - **Help overflow** (parity with classic `ai_providers_options_menu`) as a Compose top-bar action.
 *
 * The type-picker list is filtered here (classic parity): builtin types already configured are
 * hidden, CUSTOM is always offered. Recomputed whenever the provider list changes so a just-added
 * builtin drops out of the picker.
 */
class AiProvidersComposeActivity : ActivityBase() {
    private val service: LlmProviderService by inject()

    private val controller by lazy { AiProvidersController(service, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val providers by controller.providers.collectAsState()
                    val dialog by controller.dialog.collectAsState()
                    // Classic showAddProviderTypeDialog hides already-configured builtin types and
                    // always keeps CUSTOM. Recompute on every provider-list change.
                    val providerTypes = remember(providers) {
                        val configuredTypeIds = providers.map { it.providerTypeId }.toSet()
                        service.providerTypes().filter { it.isCustom || it.id !in configuredTypeIds }
                    }
                    AiProvidersScreen(
                        providers = providers,
                        providerTypes = providerTypes,
                        editState = dialog,
                        onUp = { finish() },
                        onAdd = { ensureDisclaimerAccepted { controller.startAdd() } },
                        onPickType = controller::pickType,
                        onStartEdit = controller::startEdit,
                        onField = controller::updateField,
                        onSave = controller::save,
                        onDelete = controller::delete,
                        onDismiss = controller::dismissDialog,
                        actions = { HelpAction() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Parity with classic's onResume refresh (e.g. models/keys changed in a child activity).
        service.refresh()
    }

    // --- Disclaimer gate (ported from AiSettingsFragmentBase) -------------------------------------

    /** Gate that ensures the AI disclaimer is accepted before [onAccepted]. Mirrors classic. */
    private fun ensureDisclaimerAccepted(onAccepted: () -> Unit) {
        if (service.disclaimerAccepted()) {
            onAccepted()
            return
        }
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val textView = TextView(this).apply {
            text = htmlToSpan(buildDisclaimerHtml())
            movementMethod = LinkMovementMethod.getInstance()
            setTextIsSelectable(true)
        }
        val acceptButton = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = getString(R.string.ai_disclaimer_accept_button)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = (16 * density).toInt() }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(textView)
            addView(acceptButton)
        }
        val scrollView = ScrollView(this).apply { addView(layout) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.ai_disclaimer_accept_title)
            .setView(scrollView)
            .setNegativeButton(R.string.cancel, null)
            .create()

        acceptButton.setOnClickListener {
            service.acceptDisclaimer()
            dialog.dismiss()
            onAccepted()
        }
        dialog.show()
    }

    /** Assembles the disclaimer HTML from string resources (verbatim from classic). */
    private fun buildDisclaimerHtml(): String {
        val intro = getString(R.string.ai_disclaimer_intro)
        val approach = getString(R.string.ai_disclaimer_approach)
        val responsibility = getString(R.string.ai_disclaimer_responsibility)
        val p1 = getString(R.string.ai_disclaimer_point1)
        val p2 = getString(R.string.ai_disclaimer_point2)
        val p3 = getString(R.string.ai_disclaimer_point3)
        val p4 = getString(R.string.ai_disclaimer_point4)
        val p5 = getString(R.string.ai_disclaimer_point5)
        val p6 = getString(R.string.ai_disclaimer_point6)
        val p7 = getString(R.string.ai_disclaimer_point7)
        val p8 = getString(R.string.ai_disclaimer_point8)
        val p9 = getString(R.string.ai_disclaimer_point9)
        return "$intro $approach $responsibility<br><br>• $p1<br><br>• $p2<br><br>• $p3<br><br>• $p4<br><br>$p6<br><br>$p7 $p8<br><br>$p9<br><br><i>$p5</i>"
    }

    // --- Help overflow (parity with classic ai_providers_options_menu) ----------------------------

    @Composable
    private fun RowScope.HelpAction() {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {
                expanded = false
                CommonUtils.showHelpDialog(
                    activity = this@AiProvidersComposeActivity,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_providers_text,
                    helpPath = "ai.html#choosing-a-provider",
                )
            })
        }
    }
}
