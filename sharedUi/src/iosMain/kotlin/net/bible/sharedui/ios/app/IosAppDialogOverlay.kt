package net.bible.sharedui.ios.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.app.QueuedAppDialogs
import org.koin.mp.KoinPlatform

/**
 * One renderer for the existing app-wide queues. Background/native coverage only withdraws rendering;
 * it never answers a caller or discards its draft. Compose 1.11 scene-layer dialogs embed views,
 * not presented controllers, so their own visibility does not close the UIKit gate.
 */
@Composable
internal fun IosAppDialogOverlay(host: AppDialogHost, onSheetOpening: () -> Unit = {}) {
    val koin = remember { KoinPlatform.getKoin() }
    val dialogs = remember { koin.get<AppDialogController>() }
    val permissions = remember { koin.get<AgentPermissionController>() }
    val links = remember { host.newExternalLinks() }
    val canRenderNow = host.rememberCanRenderDialogs()
    // Native presentation state is not snapshot state. Poll on Compose's Main context even with no queue changes.
    val canRender by produceState(false, canRenderNow) {
        while (true) {
            value = canRenderNow()
            delay(100)
        }
    }
    if (!canRender) return
    QueuedAppDialogs(
        dialogs = dialogs,
        permissions = permissions,
        onOpenExternal = links::open,
        askBeforeOpeningLink = links.askBeforeOpening,
        onSheetOpening = onSheetOpening,
    )
}
