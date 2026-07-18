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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.htmlToSpan
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiProvidersScreen
import net.bible.sharedui.ai.EasySetupState
import net.bible.sharedui.ai.EasySetupStep
import net.bible.sharedui.ai.EasySetupTestResult
import net.bible.sharedui.ai.EasySetupWizard
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

    /**
     * Host-owned UI-flow state for the easy-setup wizard (`null` = wizard closed). The wizard
     * ([EasySetupWizard]) is presentation-only; all transitions live here. Opened either by the
     * [EXTRA_START_EASY_SETUP] launch extra (disclaimer-gated) or programmatically via
     * [startEasySetup].
     */
    private val easySetupState = MutableStateFlow<EasySetupState?>(null)

    /**
     * Swallows the single synchronous `onDismiss` that the step-1 `AbListChoiceDialog` fires right
     * after `onSelect` when a setup is picked (its own `onClick = { onSelect(...); onDismiss() }`).
     * Set in the wizard's `onPick`, cleared by the paired synchronous `onDismiss`, so that spurious
     * dismiss is a no-op (it would otherwise close the wizard, undoing the PICK → ENTER_KEY
     * transition `onPick` just made) while every genuine dismiss — a PICK-step cancel, an
     * ENTER_KEY-step cancel, or the DONE-step "OK" — still closes the wizard. (A pure
     * `step == PICK` guard cannot work: the spurious dismiss and a genuine ENTER_KEY cancel are
     * both observed at `step == ENTER_KEY`, and the DONE "OK" is at `step == DONE`, so a
     * step-only check would leave the wizard un-closable from those steps.) Resolves the Task-6
     * host-wiring must-verify.
     */
    private var swallowNextEasySetupDismiss = false

    /**
     * Swallows the single synchronous `onDismiss` that the PICK_TYPE-step `AbListChoiceDialog`
     * fires right after `onSelect` when a provider type is picked (its own
     * `onClick = { onSelect(...); onDismiss() }`). Set in [onPickType]'s wiring below, cleared by
     * the paired synchronous `onDismiss`, so that spurious dismiss is a no-op (it would otherwise
     * close the whole add-provider dialog, undoing the PICK_TYPE → FORM transition `onPickType`
     * just made via [AiProvidersController.pickType]) while a genuine dismiss — a PICK_TYPE-step
     * cancel/outside-tap (no pick), or a FORM-step cancel — still calls
     * [AiProvidersController.dismissDialog]. Same class of fix as [swallowNextEasySetupDismiss] and
     * `AiModelsComposeActivity`'s `swallowNextDismiss` (Task 9); a pure `step == PICK_TYPE` guard
     * cannot work for the same reason documented there.
     */
    private var swallowNextPickTypeDismiss = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_START_EASY_SETUP, false) == true) {
            ensureDisclaimerAccepted { startEasySetup() }
        }
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
                        onPickType = { typeId ->
                            swallowNextPickTypeDismiss = true
                            controller.pickType(typeId)
                        },
                        onStartEdit = controller::startEdit,
                        onField = controller::updateField,
                        onSave = controller::save,
                        onDelete = controller::delete,
                        onDismiss = {
                            if (swallowNextPickTypeDismiss) {
                                swallowNextPickTypeDismiss = false
                            } else {
                                controller.dismissDialog()
                            }
                        },
                        actions = { HelpAction() },
                    )

                    val easySetup by easySetupState.collectAsState()
                    easySetup?.let { state ->
                        EasySetupWizard(
                            state = state,
                            onPick = { setupId ->
                                swallowNextEasySetupDismiss = true
                                easySetupState.value = state.copy(
                                    selectedSetupId = setupId,
                                    step = EasySetupStep.ENTER_KEY,
                                )
                            },
                            onKeyChange = { key ->
                                easySetupState.value = easySetupState.value
                                    ?.copy(apiKey = key, testResult = null)
                            },
                            onTest = { testEasySetupConnection() },
                            onConfirm = { confirmEasySetup() },
                            onDismiss = {
                                if (swallowNextEasySetupDismiss) {
                                    swallowNextEasySetupDismiss = false
                                } else {
                                    easySetupState.value = null
                                }
                            },
                        )
                    }
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

    // --- Easy-setup wizard (host state-holder; ported flow lives in LlmProviderServiceImpl) --------

    /** Opens the wizard at its initial (PICK) step. Caller must have passed the disclaimer gate. */
    private fun startEasySetup() {
        swallowNextEasySetupDismiss = false
        easySetupState.value = EasySetupState.initial(service.recommendedSetups())
    }

    /** Step-2 "Test connection": validates the entered key against the picked setup's provider. */
    private fun testEasySetupConnection() {
        val current = easySetupState.value ?: return
        val setup = current.selectedSetup ?: return
        easySetupState.value = current.copy(testing = true, testResult = null)
        lifecycleScope.launch {
            val result = service.testConnection(setup.providerTypeId, "", current.apiKey)
            val testResult = if (result.isSuccess) {
                EasySetupTestResult.Success
            } else {
                EasySetupTestResult.Failure(
                    result.exceptionOrNull()?.message ?: getString(R.string.unknown_error)
                )
            }
            easySetupState.value = easySetupState.value?.copy(testing = false, testResult = testResult)
        }
    }

    /** Step-2 "OK": creates the provider + default model, then advances to the DONE step. */
    private fun confirmEasySetup() {
        val current = easySetupState.value ?: return
        val setupId = current.selectedSetupId ?: return
        lifecycleScope.launch {
            runCatching { service.performEasySetup(setupId, current.apiKey) }
                .onSuccess {
                    easySetupState.value = easySetupState.value?.copy(step = EasySetupStep.DONE)
                }
                .onFailure { e ->
                    easySetupState.value = easySetupState.value?.copy(
                        testResult = EasySetupTestResult.Failure(
                            e.message ?: getString(R.string.unknown_error)
                        ),
                    )
                }
        }
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

    companion object {
        /**
         * Boolean launch extra: when true, the host opens the easy-setup wizard immediately on
         * create (disclaimer-gated). Lets the connection screen route "Quick setup" straight into
         * this Compose host instead of the interim classic `AiProvidersActivity` (Task 10 wires the
         * routing).
         */
        const val EXTRA_START_EASY_SETUP = "start_easy_setup"
    }
}
