@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.download

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.event.UserMessages
import net.bible.android.view.activity.bookmark.CloudFiltersValue
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.cloudsync.documents.SyncPlan
import net.bible.sharedcore.cloud.CloudDocAction
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.cloud.pushOnUnblock
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.PlatformText
import net.bible.sharedui.app.ports.PluralKey
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.docCategoryOf
import net.bible.sharedui.download.nav.CloudDocumentsDeps
import net.bible.sharedui.strings.Strings

/**
 * Classic `CloudDocumentsComposeActivity`'s own key prefix (round 17e-2), reused VERBATIM (not the Download
 * cluster's arrangement pair) so a user's persisted sync-list arrangement survives while the classic Activity and
 * this arm coexist -- both read/write the same settings key.
 */
private const val CLOUD_ARRANGEMENT_KEY = "cloudDocs.arrangement"
private const val CLOUD_ARRANGEMENT_REMEMBER_KEY = "cloudDocs.arrangement.remember"

/**
 * Delivers every document-sync running edge to [onRunning] on [scope] until the returned stop runs.
 * `asFlow()` is loss-free: a drain that emits `true` then `false` before the collector resumes still
 * delivers both (a `StateFlow` would conflate them to nothing and skip the rescan).
 */
@kotlin.native.HiddenFromObjC
fun subscribeToDocumentSyncRunning(
    scope: CoroutineScope,
    runningChanged: Events<Boolean>,
    onRunning: (Boolean) -> Unit,
): () -> Unit {
    val job = scope.launch { runningChanged.asFlow().collect { onRunning(it) } }
    return { job.cancel() }
}

/**
 * The cloud documents destination's deps builder and host baggage, ported from the classic
 * `CloudDocumentsComposeActivity`, whose line numbers the comments below cite. It shares its graph with
 * [DownloadHost], which takes [deps] into its `DownloadNavDeps`.
 *
 * Every collaborator sits behind a provider, so constructing the host touches no database.
 */
@kotlin.native.HiddenFromObjC
class CloudDocumentsHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val platformText: PlatformText,
    private val platform: CloudDocumentsHostPlatform,
    private val helpDialogs: HelpDialogs,
    private val syncStarter: DocumentSyncStarter,
    private val appSettingsProvider: () -> AppSettings,
    private val documentSyncSettingsProvider: () -> DocumentSyncSettings,
    private val documentSyncProvider: () -> DocumentSync,
    private val cloudSyncProvider: () -> CloudSync,
    /** The status/category filters the saved bookmark-host state carried for this screen, if any. */
    private val restoredFilters: () -> CloudFiltersValue?,
    /** Called once [restoredFilters] has been applied, so a later leave-and-reopen starts fresh. */
    private val consumeRestoredFilters: () -> Unit,
) {
    private val appSettings: AppSettings by lazy(appSettingsProvider)
    private val documentSyncSettings: DocumentSyncSettings by lazy(documentSyncSettingsProvider)
    private val documentSync: DocumentSync by lazy(documentSyncProvider)
    private val cloudSync: CloudSync by lazy(cloudSyncProvider)

    /** The graph's cloud documents deps. */
    fun deps(): CloudDocumentsDeps = CloudDocumentsDeps(
        // PER-ENTRY factory (task-8 fix round 1) -- see cloudDocumentsEntryRef's kdoc for why a fresh instance
        // every call (not a host-memoised singleton) is required here.
        controllerFor = { buildCloudDocumentsController() },
        title = strings.documentSyncManageTitle,
        topBarActions = { CloudDocumentsOverflowMenu() },
        openOrGate = { cloudDocumentsOpenOrGate() },
        seedItems = { cloudDocumentsSeedItems() },
        refreshFromNetwork = { cloudDocumentsRefreshFromNetwork() },
        subscribeProgress = { onRunning -> cloudDocumentsSubscribeProgress(onRunning) },
        statusFilterLabels = { showRemoved -> statusFilterLabels(showRemoved) },
        categoryFilterLabels = { categoryFilterLabels() },
        countLabel = { count, bytes -> countLabel(count, bytes) },
    )

    /** The filters of the screen's live entry, for the bookmark host's recreation snapshot; only while on top. */
    fun filtersValue(onCloudRoute: Boolean): CloudFiltersValue? =
        // The entry belongs to the screen only while it is on top; a stale entry from an earlier visit is not saved.
        cloudDocumentsEntryRef?.takeIf { onCloudRoute }?.controller?.filterSnapshot()?.let {
            CloudFiltersValue(it.status, it.category)
        }

    /**
     * One CloudDocuments back-stack entry's host-side state: the controller the arm renders, plus every piece of
     * host scratch state whose lifetime is EXACTLY that controller's -- currently just [lastPlan]. This is classic's
     * own shape restored: a `CloudDocumentsComposeActivity` instance WAS one entry, so its `controller by lazy`
     * (`:80-100`) and its `lastPlan` field (`:78`) were born and died together and neither ever needed pruning. In
     * the nav host ONE Activity hosts MANY entries, which pulled "per Activity" and "per entry" apart; this class
     * puts them back together (task-8 fix round 4).
     *
     * **Why this, and not the host-lifetime `Map<CloudDocumentsController, SyncPlan>` fix rounds 2 and 3 had:** a
     * map keyed by controller is a container whose lifetime is the HOST's, so every way an entry can end needs its
     * own pruning path -- and each round closed one and left the next open (confirm, then explicit dismiss, then
     * "the destination was popped while the Sync-now sheet was still up", where neither of those ever fires, round
     * 3's one-shot `syncNowDialog.first { it == null }` watcher waits forever, and the watcher itself -- a
     * suspended host-scope coroutine holding a hard reference -- pins the very controller it was meant to
     * release). A field on a per-entry object needs no pruning path at all: when the entry is abandoned the whole
     * object, plan included, becomes garbage as soon as the arm's composition and any coroutine still working for
     * that entry let go of it.
     */
    private class CloudDocumentsEntry {
        /** Assigned once, immediately after construction, before any of its callbacks can fire. */
        lateinit var controller: CloudDocumentsController

        /**
         * Classic's `lastPlan` field (`:78`): the last resolved Sync-now plan, retained so confirming it dispatches
         * without a second resolve. Set by [cloudDocumentsShowSyncNow], consumed AND cleared by
         * [handleCloudDocumentsSyncNowConfirm], overwritten by the next open. Deliberately NOT cleared on dismiss
         * -- classic did not clear it either, the screen only offers a confirm button while `syncNowDialog` is
         * non-null (so a dismissed sheet has no path to a confirm without a fresh `showSyncNow` resolving a new
         * plan first), and the value dies with this entry regardless.
         */
        var lastPlan: SyncPlan? = null
    }

    /**
     * The CURRENT entry's state -- task-8 fix round 1 (the controller), round 4 (everything else that is
     * per-entry). [buildCloudDocumentsController] REASSIGNS this every time [CloudDocumentsDeps.controllerFor] is
     * called, one per genuine composition entry, the `CustomRepositoriesDeps.controllerFor` idiom rather than a
     * `by lazy` singleton: this destination has TWO distinct entry points (Download's overflow row, Settings' sync
     * row) and no covering child, so a genuine leave-and-reopen within one host session is a real, reachable path,
     * and classic reset every filter/selection/search/arrangement on each such open by getting a fresh Activity
     * and a fresh controller. A singleton would have let all of that silently survive a round trip instead.
     *
     * **Read ONLY from [cloudDocumentsOpenOrGate]/[cloudDocumentsRefreshFromNetwork] (through the
     * [cloudDocumentsController] accessor below), and only ONCE, synchronously, before either function's first
     * suspension point** -- both capture it into a local `val controller` immediately and use ONLY that local
     * afterward, INCLUDING inside a `finally` block (fix round 3: `finally` runs even when the surrounding
     * coroutine is being CANCELLED, e.g. because the arm's composition was just disposed by a leave-and-reopen, so
     * a `finally { cloudDocumentsController.pushBusy(false) }` that re-read the property there could clear busy on
     * a SECOND entry's controller instead of the one that set it true -- capturing once up front removes the
     * second read entirely, not just delays it). **Composable functions** get their own composition-time snapshot
     * instead (see [CloudDocumentsOverflowMenu]). Every other function below that touches an entry does so through
     * an EXPLICIT `controller: CloudDocumentsController` (or `entry: CloudDocumentsEntry`) parameter, captured once
     * at the point the operation started, never by re-reading this property after a suspension point or from a
     * callback that can fire later than its origin (task-8 fix round 2, finding A) -- `shell.hostScope.launch {
     * ... }` is host-scoped, not cancelled when the arm's composition is disposed, so a coroutine started by one
     * entry (or a dialog callback armed by one entry, which can fire arbitrarily later) can easily resume or fire
     * AFTER a leave-and-reopen has already rebuilt this ref onto a second, DIFFERENT entry; reading the property at
     * that point would silently misdirect the first entry's result onto the second entry's live controller.
     * Binding every such callback to the specific entry it was built for -- the `entry` capture in
     * [buildCloudDocumentsController] -- makes that misdirection structurally impossible instead of merely
     * unlikely.
     */
    private var cloudDocumentsEntryRef: CloudDocumentsEntry? = null
    private val cloudDocumentsEntry: CloudDocumentsEntry
        get() = requireNotNull(cloudDocumentsEntryRef) {
            "CloudDocuments controller read before its destination composed"
        }
    private val cloudDocumentsController: CloudDocumentsController
        get() = cloudDocumentsEntry.controller

    /**
     * Classic's own `controller by lazy` field (`CloudDocumentsComposeActivity.kt:80-100`), ported as a PER-ENTRY
     * factory -- see [cloudDocumentsEntryRef]'s kdoc for both halves of why: the per-entry rebuild itself, and why
     * every constructor callback below is a LAMBDA closing over the local `entry` (whose `controller` is assigned
     * before any of them can fire) rather than a bare method reference -- a method reference is the SAME function
     * for every instance and cannot tell "its own" entry from the ref's current one, which is exactly the bug fix
     * round 2 closes.
     */
    private fun buildCloudDocumentsController(): CloudDocumentsController {
        val entry = CloudDocumentsEntry()
        entry.controller = CloudDocumentsController(
            syncEnabled = { documentSyncSettings.enabled },
            onAction = { action, initials -> handleCloudDocumentsAction(entry.controller, action, initials) },
            onBulkAction = { action, initials -> handleCloudDocumentsBulkAction(entry.controller, action, initials) },
            onSyncNow = { download, upload, delete -> handleCloudDocumentsSyncNowConfirm(entry, download, upload, delete) },
            onRescan = { cloudDocumentsRunSyncAction(entry.controller) { documentSync.resetListingCache() } },
            onShowRemovedChange = { show -> handleCloudDocumentsShowRemovedChange(entry.controller, show) },
            onConfirmRemove = { initials -> handleCloudDocumentsRemoveConfirmed(entry.controller, initials) },
            onConfirmPurge = { initials -> handleCloudDocumentsPurgeConfirmed(entry.controller, initials) },
            onConfirmBlock = { initials -> handleCloudDocumentsBlockConfirmed(entry.controller, initials) },
            storedArrangement = if (appSettings.getBoolean(CLOUD_ARRANGEMENT_REMEMBER_KEY, true))
                appSettings.getString(CLOUD_ARRANGEMENT_KEY, null) else null,
            rememberArrangementInitially = appSettings.getBoolean(CLOUD_ARRANGEMENT_REMEMBER_KEY, true),
            onArrangementChange = { encoded, remember ->
                appSettings.setBoolean(CLOUD_ARRANGEMENT_REMEMBER_KEY, remember)
                appSettings.setString(CLOUD_ARRANGEMENT_KEY, encoded)
            },
            scope = shell.hostScope,
        )
        // Consumed once: a later leave-and-reopen within this host starts fresh, like classic. The sign-in error
        // dialog is deliberately not carried: it is re-raised by the gate's sign-in attempt, which is a real
        // network step that cannot be skipped just because the Activity was recreated.
        restoredFilters()?.let {
            entry.controller.restoreFilters(it.toSnapshot())
            consumeRestoredFilters()
        }
        cloudDocumentsEntryRef = entry
        return entry.controller
    }

    /**
     * Classic `openOrGate()` in full (`:193-208`) -- see [CloudDocumentsDeps.openOrGate]'s kdoc. A single cache scan
     * serves both the sign-in-gate's emptiness check and the seed, exactly as classic's own `cached` local did.
     *
     * `controller` is captured on the FIRST LINE, ahead of the `CloudSync.signIn` and [cloudDocumentsSeedItems]
     * suspension points -- see [cloudDocumentsEntryRef]'s kdoc (fix round 3). Slice 4's final-review finding M1: the
     * capture used to sit BELOW both of them, which was still safe (the caller is the arm's own `LaunchedEffect`,
     * so a disposal cancels this coroutine and neither call below runs at all) but did not match what this kdoc
     * claimed, and the guard protecting the invariant -- `CloudDocumentsControllerRebuildIsolationTest` -- counted
     * occurrences without checking position, so it could not have caught the drift its own message described. The
     * capture is now genuinely first, and that guard now asserts the position as well as the count.
     */
    private suspend fun cloudDocumentsOpenOrGate(): Boolean {
        val controller = cloudDocumentsController
        documentSyncSettings.ensureLoaded()
        var signedIn = cloudSync.signedIn
        if (!signedIn) signedIn = cloudSync.signIn() == true
        val items = cloudDocumentsSeedItems()
        if (!signedIn && items.isEmpty()) {
            UserMessages.toast(strings.documentSyncSigninRequired, long = true)
            return false
        }
        controller.setShowRemoved(documentSyncSettings.showRemovedDocuments)
        controller.setItems(items)
        if (signedIn && (!documentSyncSettings.enabled || items.isEmpty())) cloudDocumentsRefreshFromNetwork()
        return true
    }

    /**
     * Classic's shared cache-only scan+flatten, the middle of both `openOrGate()` (`:198`, `:205`) and
     * `renderFromCache()` (`:229`) -- see [CloudDocumentsDeps.seedItems]'s kdoc for why this stays PURE (no
     * controller mutation): it is reached from [handleCloudDocumentsShowRemovedChange], itself reached from
     * [CloudDocumentsController.setShowRemoved], which calls its `onShowRemovedChange` callback UNCONDITIONALLY on
     * every invocation -- a `setShowRemoved` call inside this function would recurse through that callback forever.
     */
    private suspend fun cloudDocumentsSeedItems(): List<CloudDocItem> = withContext(Dispatchers.IO) {
        documentSyncSettings.ensureLoaded()
        documentSync.scanCached(includeDeleted = documentSyncSettings.showRemovedDocuments).map { it.toCloudDocItem() }
    }

    /**
     * Classic `refreshFromNetwork()` (`:212-218`): a network scan, flattened and pushed with a busy pair around it.
     * `controller` is captured ONCE, before the `finally` even exists to run -- fix round 3: the property was read
     * a second time inside `finally { cloudDocumentsController.pushBusy(false) }`, and `finally` blocks run even
     * when this coroutine is CANCELLED (e.g. a leave-and-reopen disposed the arm's composition while the scan
     * above was suspended), so that second read could resolve to a SECOND entry's controller and clear ITS busy
     * flag instead of this call's own. Capturing once removes the second read entirely.
     */
    private suspend fun cloudDocumentsRefreshFromNetwork() {
        val controller = cloudDocumentsController
        controller.pushBusy(true)
        try {
            val items = withContext(Dispatchers.IO) {
                documentSync.scan(includeDeleted = documentSyncSettings.showRemovedDocuments).map { it.toCloudDocItem() }
            }
            controller.setItems(items)
        } finally {
            controller.pushBusy(false)
        }
    }

    /**
     * Classic's `renderFromCache()` (`:226-232`), reached only from [CloudDocumentsController.setShowRemoved]'s
     * `onShowRemovedChange` callback -- persist the setting, then re-seed from the cache with the new
     * `includeDeleted` value. [controller] is the SPECIFIC instance [buildCloudDocumentsController] bound this
     * callback to, not [cloudDocumentsController]'s current value -- see that property's kdoc (fix round 2, finding
     * A): this launches on the host scope, which outlives the arm's composition, so by the time it resumes past the
     * suspending seed a leave-and-reopen may already have rebuilt the ref onto a different controller.
     */
    private fun handleCloudDocumentsShowRemovedChange(controller: CloudDocumentsController, show: Boolean) {
        documentSyncSettings.showRemovedDocuments = show
        shell.hostScope.launch {
            controller.pushBusy(true)
            try {
                controller.setItems(cloudDocumentsSeedItems())
            } finally {
                controller.pushBusy(false)
            }
        }
    }

    /**
     * Classic `bridge.register()`/`unregister()` plus the `bridge.running.drop(1).collect { ... }` body as one
     * subscribe/stop pair -- see [CloudDocumentsDeps.subscribeProgress]'s kdoc. Each call is its own subscription on
     * the host scope (this destination can be entered and left several times within one host instance); there is
     * no bridge and no `drop(1)`, since no initial value exists to skip.
     */
    private fun cloudDocumentsSubscribeProgress(onRunning: (Boolean) -> Unit): () -> Unit =
        subscribeToDocumentSyncRunning(shell.hostScope, documentSync.runningChanged, onRunning)

    /**
     * Classic `runSyncAction` (`:234-241`), used by [buildCloudDocumentsController]'s `onRescan`. [controller] is
     * the SPECIFIC instance the callback was bound to -- see [cloudDocumentsEntryRef]'s kdoc (fix round 2, finding
     * A); this also launches on the host scope, so the same "may resume after a leave-and-reopen" hazard applies.
     */
    private fun cloudDocumentsRunSyncAction(controller: CloudDocumentsController, block: suspend () -> Unit): Job =
        shell.hostScope.launch {
            controller.pushBusy(true)
            try {
                withContext(Dispatchers.IO) { block() }
                val items = withContext(Dispatchers.IO) {
                    documentSync.scan(includeDeleted = documentSyncSettings.showRemovedDocuments).map { it.toCloudDocItem() }
                }
                controller.setItems(items)
            } finally {
                controller.pushBusy(false)
            }
        }

    /**
     * Classic `handleAction` (`:258-268`). [controller] is the instance [buildCloudDocumentsController] bound
     * `onAction` to; [handleCloudDocumentsRemoveConfirmed]/[handleCloudDocumentsPurgeConfirmed] (bound to the SAME
     * instance at construction) are what eventually run the confirmed action, so a confirm tap -- which can fire
     * arbitrarily later than this call, long past any leave-and-reopen -- still lands on the RIGHT controller (fix
     * round 2, finding A).
     */
    private fun handleCloudDocumentsAction(controller: CloudDocumentsController, action: CloudDocAction, initials: String) {
        val item = controller.items.value.firstOrNull { it.initials == initials } ?: return
        when (action) {
            CloudDocAction.DOWNLOAD -> syncStarter.start(emptyList(), listOf(initials))
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> syncStarter.start(listOf(initials), emptyList())
            CloudDocAction.BLOCK -> {
                documentSyncSettings.blockList.block(initials)
                controller.setBlocked(initials, true)
            }
            CloudDocAction.UNBLOCK -> {
                val push = pushOnUnblock(controller.items.value, listOf(initials))
                documentSyncSettings.blockList.unblock(initials)
                controller.setBlocked(initials, false)
                if (push.isNotEmpty()) syncStarter.start(push, emptyList())
            }
            CloudDocAction.REMOVE_CLOUD -> {
                val enabled = documentSyncSettings.enabled
                val message =
                    if (enabled) strings.cloudDocRemoveAllConfirm(item.name) else strings.cloudDocRemoveCloudConfirm(item.name)
                controller.requestConfirmRemove(listOf(initials), message, allDevices = enabled)
            }
            CloudDocAction.PURGE -> {
                controller.requestConfirmPurge(listOf(initials), strings.cloudDocPurgeConfirm(item.name))
            }
        }
    }

    /** Classic `handleBulkAction` (`:270-279`) -- [controller] as [handleCloudDocumentsAction]'s own kdoc explains. */
    private fun handleCloudDocumentsBulkAction(controller: CloudDocumentsController, action: CloudDocAction, initials: List<String>) {
        when (action) {
            CloudDocAction.DOWNLOAD -> {
                syncStarter.start(emptyList(), initials)
                controller.clearSelection()
            }
            CloudDocAction.PUSH, CloudDocAction.RESTORE -> {
                syncStarter.start(initials, emptyList())
                controller.clearSelection()
            }
            CloudDocAction.BLOCK -> {
                // F75: ask first; the confirmed half is handleCloudDocumentsBlockConfirmed.
                val message = platformText.plural(PluralKey.CLOUD_DOC_BULK_BLOCK_CONFIRM, initials.size, initials.size)
                controller.requestConfirmBlock(initials, message)
            }
            CloudDocAction.UNBLOCK -> {
                val push = pushOnUnblock(controller.items.value, initials)
                initials.forEach { documentSyncSettings.blockList.unblock(it); controller.setBlocked(it, false) }
                if (push.isNotEmpty()) syncStarter.start(push, emptyList())
                controller.clearSelection()
            }
            CloudDocAction.REMOVE_CLOUD -> {
                val enabled = documentSyncSettings.enabled
                val message = platformText.plural(
                    if (enabled) PluralKey.CLOUD_DOC_BULK_REMOVE_CONFIRM else PluralKey.CLOUD_DOC_BULK_REMOVE_CLOUD_CONFIRM,
                    initials.size, initials.size,
                )
                controller.requestConfirmRemove(initials, message, allDevices = enabled)
            }
            CloudDocAction.PURGE -> {
                val message = platformText.plural(PluralKey.CLOUD_DOC_BULK_PURGE_CONFIRM, initials.size, initials.size)
                controller.requestConfirmPurge(initials, message)
            }
        }
    }

    /**
     * Task 17 (run-2 plan, NH rows 8574/8586): the confirmed half of both [handleCloudDocumentsAction]'s and
     * [handleCloudDocumentsBulkAction]'s REMOVE_CLOUD branch -- classic's positive button did exactly this
     * (`DocumentSyncService.start` + `applyRemoval`/`clearSelection`) regardless of single-vs-bulk, since a bulk
     * call is just a longer [initials]; unifying the two near-copies here is what the plan's "share it" note asks.
     */
    private fun handleCloudDocumentsRemoveConfirmed(controller: CloudDocumentsController, initials: List<String>) {
        syncStarter.start(emptyList(), emptyList(), remove = initials)
        initials.forEach { controller.applyRemoval(it) }
        controller.clearSelection()
    }

    /** Task 17 -- the PURGE counterpart of [handleCloudDocumentsRemoveConfirmed]. */
    private fun handleCloudDocumentsPurgeConfirmed(controller: CloudDocumentsController, initials: List<String>) {
        syncStarter.start(emptyList(), emptyList(), purge = initials)
        initials.forEach { controller.applyPurge(it) }
        controller.clearSelection()
    }

    /** F75 -- the confirmed half of [handleCloudDocumentsBulkAction]'s BLOCK branch (classic's direct body). */
    private fun handleCloudDocumentsBlockConfirmed(controller: CloudDocumentsController, initials: List<String>) {
        initials.forEach { documentSyncSettings.blockList.block(it); controller.setBlocked(it, true) }
        controller.clearSelection()
    }

    /**
     * Classic `showSyncNow` (`:308-324`), triggered from [CloudDocumentsOverflowMenu]'s "Sync now" row. [entry] is
     * the OverflowMenu's own composition-time snapshot (see that composable's kdoc), not [cloudDocumentsEntry]'s
     * current value -- this launches on the host scope and awaits a network round trip, so a leave-and-reopen could
     * otherwise land the resolved plan and dialog on the WRONG (second) entry's controller (fix round 2, finding A).
     *
     * The resolved plan is parked on THAT entry ([CloudDocumentsEntry.lastPlan]), exactly where classic parked it
     * (a field of the one Activity that was the one entry), so it needs no cleanup of its own on any of the ways
     * this sheet can end -- confirm, cancel, or the destination being popped with the sheet still up (task-8 fix
     * round 4; see [cloudDocumentsEntryRef]'s kdoc for the map-shaped predecessor this replaced and why).
     */
    private fun cloudDocumentsShowSyncNow(entry: CloudDocumentsEntry) {
        val controller = entry.controller
        shell.hostScope.launch {
            controller.pushBusy(true)
            val plan = try {
                withContext(Dispatchers.IO) { documentSync.computeSyncPlan(download = true, upload = true, delete = true) }
            } catch (e: Exception) {
                UserMessages.toast(strings.syncError)
                return@launch
            } finally {
                controller.pushBusy(false)
            }
            cloudDocumentsPresentSyncNow(entry, plan)
        }
    }

    /**
     * The tail of classic's `showSyncNow` (`:316-324`): park the resolved plan on the entry it was resolved FOR,
     * label it, and put the sheet up. Split out of [cloudDocumentsShowSyncNow] so that parking decision -- the whole
     * subject of task-8 fix round 4 -- is reachable without the IO round trip that resolves the plan;
     * `CloudDocumentsControllerRebuildIsolationTest` drives this directly rather than writing
     * [CloudDocumentsEntry.lastPlan] reflectively, so its "an abandoned entry pins nothing on the host" assertion is
     * made against the REAL parking path.
     */
    private fun cloudDocumentsPresentSyncNow(entry: CloudDocumentsEntry, plan: SyncPlan) {
        entry.lastPlan = plan
        val labels = listOf(
            strings.cloudDocSyncNowDownload + "\n" + countLabel(plan.toDownload.size, plan.downloadBytes),
            strings.cloudDocSyncNowUpload + "\n" + countLabel(plan.toUpload.size, plan.uploadBytes),
            strings.cloudDocSyncNowDelete + "\n" + countLabel(plan.toUninstall.size, null),
        )
        val checked = listOf(
            documentSyncSettings.syncNowDownload, documentSyncSettings.syncNowUpload, documentSyncSettings.syncNowDelete,
        )
        entry.controller.showSyncNow(labels, checked)
    }

    /**
     * Classic `handleSyncNowConfirm` (`:326-337`). [entry] is the one [buildCloudDocumentsController] bound
     * `onSyncNow` to, and the resolved plan is read (and cleared) from THAT entry's own
     * [CloudDocumentsEntry.lastPlan], so a plan resolved for one entry can never answer another entry's confirm.
     */
    private fun handleCloudDocumentsSyncNowConfirm(entry: CloudDocumentsEntry, download: Boolean, upload: Boolean, delete: Boolean) {
        val plan = entry.lastPlan ?: return
        entry.lastPlan = null
        documentSyncSettings.syncNowDownload = download
        documentSyncSettings.syncNowUpload = upload
        documentSyncSettings.syncNowDelete = delete
        syncStarter.start(
            push = if (upload) plan.toUpload else emptyList(),
            download = if (download) plan.toDownload else emptyList(),
            uninstall = if (delete) plan.toUninstall else emptyList(),
        )
    }

    /** Classic `countLabel` (`:339-343`) -- the third/fourth of the batch's four plural call sites. */
    internal fun countLabel(count: Int, bytes: Long?): String = when {
        count == 0 -> strings.cloudDocSyncNowCountNone
        bytes != null && bytes > 0 ->
            platformText.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT_SIZE, count, count, platformText.formatFileSize(bytes))
        else -> platformText.plural(PluralKey.CLOUD_DOC_SYNC_NOW_COUNT, count, count)
    }

    /**
     * Classic `OverflowMenu()` (`:346-375`) -- the `topBarActions` slot, host-side because every row is host work
     * (platform icons, `CloudSync.signedIn`, the help dialog). `AbMenuItem`, never `DropdownMenuItem`: this file is
     * scanned by `MenuSeamGuardTest`.
     *
     * The [CloudDocumentsEntry] is read ONCE, as a composition-time snapshot of [cloudDocumentsEntry] -- this
     * composable recomposes fresh for every genuine entry (it is nested inside the arm's own composition via the
     * `topBarActions` slot), so the snapshot always names the CURRENT entry. Its `onClick` rows close over that
     * local val, not the live property, so a tap (which can fire after the menu has been open for a moment) still
     * targets the entry it was opened from rather than whatever the ref happens to hold at tap time (fix round 2,
     * finding A) -- and "Sync now" parks its resolved plan on that same snapshot, so the plan cannot outlive the
     * entry it was resolved for (fix round 4).
     */
    @Composable
    private fun CloudDocumentsOverflowMenu() {
        val entry = cloudDocumentsEntry
        AbOverflowMenu(contentDescription = null) { close ->
            if (cloudSync.signedIn) {
                AbMenuItem(
                    text = strings.cloudDocSyncNow,
                    onClick = { close(); cloudDocumentsShowSyncNow(entry) },
                    icon = { platform.OverflowIcon(CloudDocumentsOverflowItem.SYNC_NOW) },
                )
                AbMenuItem(
                    text = strings.cloudDocRescan,
                    onClick = { close(); entry.controller.rescan() },
                    icon = { platform.OverflowIcon(CloudDocumentsOverflowItem.RESCAN) },
                )
            }
            AbMenuItem(
                text = strings.help,
                onClick = {
                    close()
                    helpDialogs.show(HelpTopic.DOCUMENT_SYNC)
                },
                icon = { platform.OverflowIcon(CloudDocumentsOverflowItem.HELP) },
            )
        }
    }

    /** Classic's view-data flatten (`:378-385`). */
    private fun DocumentSync.DocumentStatusItem.toCloudDocItem(): CloudDocItem = CloudDocItem(
        initials = initials, name = name, category = category?.let { docCategoryOf(it) },
        cloudVersion = cloudVersion, localVersion = localVersion,
        cloudOnly = cloudOnly, localOnly = localOnly, updateAvailable = updateAvailable, localNewer = localNewer,
        blocked = blocked, canDeleteLocal = canDeleteLocal, cloudDeleted = cloudDeleted,
        sizeLabel = if (sizeBytes > 0) platformText.formatFileSize(sizeBytes) else null,
        sizeBytes = sizeBytes.takeIf { it > 0 },
    )

    /** Classic `statusFilterLabels` (`:387-396`), strings only -- see [CloudDocumentsDeps.statusFilterLabels]'s kdoc. */
    private fun statusFilterLabels(showRemoved: Boolean): List<String> = buildList {
        add(strings.cloudDocFilterAll)
        add(strings.cloudDocFilterInstalled)
        add(strings.cloudDocFilterCloud)
        add(strings.cloudDocFilterUpdates)
        add(strings.cloudDocFilterBlocked)
        add(strings.cloudDocFilterDeviceOnly)
        add(strings.cloudDocFilterCloudOnly)
        if (showRemoved) add(strings.cloudDocFilterRemoved)
    }

    /** Classic `categoryFilterLabels` (`:398-406`), strings only. */
    private fun categoryFilterLabels(): List<String> = listOf(
        strings.docTypeAll,
        strings.docTypeBible,
        strings.docTypeCommentary,
        strings.docTypeDictionary,
        strings.docTypeGeneralBook,
        strings.docTypeMaps,
        strings.docTypeAddon,
    )
}
