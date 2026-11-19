@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import net.bible.sharedcore.links.ExternalLinkDecision
import net.bible.sharedcore.links.TrustedLinkDomains
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.strings.Strings

/** Opening web links; Compose link routing asks first in discrete builds. */
@kotlin.native.HiddenFromObjC
interface ExternalLinks {
    val askBeforeOpening: Boolean

    /** The user's trusted hosts (#4155); null (test doubles) never skips nor offers to skip the question. */
    val trustedDomains: TrustedLinkDomains? get() = null

    /** Opens immediately; callers that have already confirmed must not ask again. */
    fun open(url: String)

    /**
     * Non-Compose document links ask unless the link's host is trusted (#4155); discrete mode
     * ([askBeforeOpening]) always asks, independently of the discrete-mode UI routing.
     */
    fun confirmAndOpen(url: String, dialogs: AppDialogController, strings: () -> Strings) {
        val trust = trustedDomains
        val decision = trust?.decide(url, askRequested = true, discrete = askBeforeOpening)
            ?: ExternalLinkDecision.Ask(trustableHost = null)
        val host = when (decision) {
            ExternalLinkDecision.Open -> return open(url)
            is ExternalLinkDecision.Ask -> decision.trustableHost
        }
        dialogs.post(AppDialogRequest.Localized { externalLinkQuestion(strings(), url, host) }) { result ->
            if (externalLinkAnswer(result, host, trust)) open(url)
        }
    }
}

/** The single [AppDialogResult.SelectedWithCheckbox] option value of [externalLinkQuestion]. */
@kotlin.native.HiddenFromObjC
const val EXTERNAL_LINK_OPEN = "open"

/**
 * The "open external link?" question shared by every queued asker (#4155). With a [trustableHost] it carries the
 * "Don't ask again for <host>" checkbox ([AppDialogRequest.Options]); without one it is the plain confirmation.
 */
@kotlin.native.HiddenFromObjC
fun externalLinkQuestion(s: Strings, url: String, trustableHost: String?): AppDialogRequest =
    if (trustableHost == null) AppDialogRequest.Confirm(
        title = s.externalLink,
        message = s.externalLinkQuestion(url),
        confirmText = s.okay,
        dismissText = s.cancel,
    ) else AppDialogRequest.Options(
        title = s.externalLink,
        message = s.externalLinkQuestion(url),
        options = listOf(SettingsItem.Choice(EXTERNAL_LINK_OPEN, s.okay)),
        dismissText = s.cancel,
        asActionSheet = false,
        checkboxLabel = s.externalLinkDontAskAgain(trustableHost),
    )

/**
 * Whether [result] of an [externalLinkQuestion] means "open"; a ticked checkbox first adds [trustableHost] to
 * [trust].
 */
@kotlin.native.HiddenFromObjC
fun externalLinkAnswer(result: AppDialogResult, trustableHost: String?, trust: TrustedLinkDomains?): Boolean =
    when (result) {
        AppDialogResult.Ok -> true
        is AppDialogResult.SelectedWithCheckbox -> {
            if (result.checked && trustableHost != null) trust?.trust(trustableHost)
            result.value == EXTERNAL_LINK_OPEN
        }
        is AppDialogResult.Selected -> result.value == EXTERNAL_LINK_OPEN
        else -> false
    }
