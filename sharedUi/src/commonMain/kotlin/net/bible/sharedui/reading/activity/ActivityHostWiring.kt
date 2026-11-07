@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import net.bible.service.cloudsync.SyncNow
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityMenuPreference
import net.bible.android.control.activity.ActivityState
import net.bible.sharedcore.nav.NavRoutes

/**
 * What a reading host needs to show background activity (#4007): the registry, the user's preference and what a
 * tapped row does. A host that leaves this out (`null`) renders today's toolbar.
 *
 * @param listRequests each emission opens the full list when the preference is on (a desktop notification click, #4012)
 * @param onOpenItem routes a tapped row to existing navigation (download screen, sync settings); other rows do nothing.
 * Kept last so Android's trailing-lambda call `ActivityHostWiring(center, preference) { ... }` still compiles.
 */
@kotlin.native.HiddenFromObjC
class ActivityHostWiring(
    val center: ActivityCenter,
    val preference: ActivityMenuPreference,
    val listRequests: Flow<Unit>? = null,
    val syncNow: SyncNow? = null,
    val syncReady: () -> Boolean = { net.bible.service.startup.ReadingAppInit.initialized },
    val onOpenItem: (ActivityItem) -> Unit,
)

/**
 * Collects the activity flows into an [ActivityMenuState] for [content] (`null` while the preference is off or
 * [wiring] is `null`, so the toolbar renders as before) and owns the full list's visibility.
 *
 * The state is remembered and carries the flows as Compose `State`s that only the drawing composables read, so
 * activity updates do not recompose the reading screen (with the preference off nothing reads them at all).
 */
@kotlin.native.HiddenFromObjC
@Composable
fun ActivityHost(wiring: ActivityHostWiring?, content: @Composable (ActivityMenuState?) -> Unit) =
    ActivityHostImpl(wiring, onHostComposed = null, content = content)

/**
 * [ActivityHost]'s body. [onHostComposed] is a test probe, called after each composition of this function itself
 * (not of [content]): tests use it to prove the host never reads the item flows on the reading screen's behalf.
 */
@Composable
internal fun ActivityHostImpl(
    wiring: ActivityHostWiring?,
    onHostComposed: (() -> Unit)?,
    content: @Composable (ActivityMenuState?) -> Unit,
) {
    if (onHostComposed != null) SideEffect(onHostComposed)
    if (wiring == null) {
        content(null)
        return
    }
    val enabled by wiring.preference.enabled.collectAsState()
    // The State objects are only handed on: reading `.value` here would recompose the whole reading screen on
    // every progress event. The readers (button, menu section, list layer) read them where they draw.
    val items = wiring.center.items.collectAsState()
    val unread = wiring.center.unreadFailures.collectAsState()
    var listVisible by remember { mutableStateOf(false) }
    val onShowAll = remember { { listVisible = true } }
    val sync = wiring.syncNow
    val available = remember(sync, wiring) {
        object : State<Boolean> {
            override val value: Boolean get() = wiring.syncReady() && sync?.available() == true
        }
    }
    val running = sync?.running?.collectAsState()
    val syncState = remember(sync, available, running) {
        if (sync != null && running != null) SyncNowMenuState(available, running, sync::start) else null
    }
    val onMenuOpened = remember(wiring, sync, available) {
        { wiring.center.markAllRead() }
    }

    // Turning the preference off must not leave the list orphaned on screen.
    LaunchedEffect(enabled) { if (!enabled) listVisible = false }

    // A request while the preference is off is dropped, not remembered.
    val enabledNow by rememberUpdatedState(enabled)
    LaunchedEffect(wiring.listRequests) { wiring.listRequests?.collect { if (enabledNow) listVisible = true } }

    val state = remember(enabled, items, unread, onShowAll, onMenuOpened, syncState) {
        if (enabled) ActivityMenuState(items, unread, onShowAll, onMenuOpened, syncState) else null
    }
    content(state)
    if (enabled && listVisible) {
        ActivityListLayer(
            items = items,
            wiring = wiring,
            onOpenItem = { item -> listVisible = false; wiring.onOpenItem(item) },
            onClose = { listVisible = false },
        )
    }
}

/** The full list; it is the only host-level reader of the items, and exists only while the list is open. */
@Composable
private fun ActivityListLayer(
    items: State<List<ActivityItem>>,
    wiring: ActivityHostWiring,
    onOpenItem: (ActivityItem) -> Unit,
    onClose: () -> Unit,
) {
    ActivityListHost(
        visible = true,
        items = items.value,
        onDismissItem = wiring.center::dismiss,
        onClearFinished = wiring.center::clearFinished,
        onOpenItem = onOpenItem,
        onClose = onClose,
    )
}

/**
 * The existing screen a tapped activity row leads to, or `null` for rows that lead nowhere: downloads open the download
 * screen (a local zip install has no screen), a failed sync opens the sync settings.
 */
@kotlin.native.HiddenFromObjC
fun activityItemRoute(item: ActivityItem): String? = when {
    item.kind == ActivityKind.DOWNLOAD -> NavRoutes.download()
    item.kind == ActivityKind.SYNC && item.state == ActivityState.FAILED -> NavRoutes.SYNC_SETTINGS
    else -> null
}
