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

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.AiProvidersScreen
import net.bible.sharedui.ai.EasySetupState
import net.bible.sharedui.ai.EasySetupStep
import net.bible.sharedui.ai.EasySetupTestResult
import net.bible.sharedui.ai.EasySetupWizard
import org.koin.android.ext.android.inject

/**
 * Compose host for the AI providers list/edit screen — the new-path twin of classic
 * [AiProvidersActivity]/[AiProvidersFragment]. Wires the shared [AiProvidersController] over
 * [LlmProviderService] and renders [AiProvidersScreen].
 *
 * Two things stay host-side (Android-resource / dialog concerns the shared layer can't own):
 * - **Disclaimer gate**: [onAdd] runs [ensureDisclaimerAccepted] before opening the add-provider
 *   dialog (ports classic `AiSettingsFragmentBase.ensureDisclaimerAccepted`), then calls
 *   [AiProvidersController.startAdd]; likewise gates [startEasySetup] for the Quick-setup launch
 *   extra. F31: the actual "Accept AI disclaimer" dialog is now [AiProvidersScreen]'s
 *   `AbConfirmDialog` (`showAcceptDisclaimerDialog`), not a classic `AlertDialog.Builder` — this
 *   host only stashes the pending continuation ([pendingDisclaimerAction]) while it's shown and
 *   resumes it once [AiProvidersController.acceptDisclaimer] confirms.
 *
 * The help dialog (F30) is owned by [AiProvidersScreen] itself as an `AbInfoDialog` — this host only
 * supplies the Android-resource-backed help body text and the full "Read more" docs URL (both must
 * come from here since commonMain can't read `R.string.*` / build a docs-relative link).
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

    /**
     * F31: the continuation stashed by [ensureDisclaimerAccepted] while the disclaimer hasn't been
     * accepted yet (`null` = no accept-dialog pending). Set to the gated action ([onAdd] / opening
     * the Quick-setup wizard) when the gate fails; [AiProvidersScreen]'s `showAcceptDisclaimerDialog`
     * renders whenever this is non-null. Resumed and cleared by the dialog's confirm callback.
     */
    private val pendingDisclaimerAction = MutableStateFlow<(() -> Unit)?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_START_EASY_SETUP, false) == true) {
            ensureDisclaimerAccepted { startEasySetup() }
        }
        setContent {
            AbAppTheme {
                    val providers by controller.providers.collectAsState()
                    val dialog by controller.dialog.collectAsState()
                    val pendingDisclaimer by pendingDisclaimerAction.collectAsState()
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
                        helpBody = getString(R.string.help_ai_providers_text),
                        helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#choosing-a-provider",
                        showAcceptDisclaimerDialog = pendingDisclaimer != null,
                        onAcceptDisclaimer = { confirmDisclaimerAccepted() },
                        onDismissAcceptDisclaimer = { pendingDisclaimerAction.value = null },
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

    override fun onResume() {
        super.onResume()
        // Parity with classic's onResume refresh (e.g. models/keys changed in a child activity).
        service.refresh()
    }

    // --- Disclaimer gate (ported from AiSettingsFragmentBase; F31 replaced the classic dialog) -----

    /**
     * Gate that ensures the AI disclaimer is accepted before [onAccepted]. If already accepted, runs
     * it immediately; otherwise stashes it in [pendingDisclaimerAction], which makes
     * [AiProvidersScreen]'s `AbConfirmDialog` appear ([confirmDisclaimerAccepted] resumes it on
     * accept).
     */
    private fun ensureDisclaimerAccepted(onAccepted: () -> Unit) {
        if (service.disclaimerAccepted()) {
            onAccepted()
            return
        }
        pendingDisclaimerAction.value = onAccepted
    }

    /** [AiProvidersScreen]'s accept-disclaimer dialog confirm callback: records acceptance via the
     *  controller (never the service directly from the composable), then resumes the stashed
     *  continuation. */
    private fun confirmDisclaimerAccepted() {
        controller.acceptDisclaimer()
        val onAccepted = pendingDisclaimerAction.value
        pendingDisclaimerAction.value = null
        onAccepted?.invoke()
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
