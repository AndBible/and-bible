/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.page.window

import net.bible.sharedcore.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.StudyPadKey
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.save
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils.settings
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.blockingAtEdge
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.service.common.CommonUtils
import net.bible.service.history.HistoryManager
import org.crosswire.jsword.versification.BookName
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.min

open class WindowRepository(val scope: CoroutineScope) : KoinComponent {
    /**
     * The application write-through. Resolved lazily: this repository is constructed while Koin is
     * still starting (the graph builds [net.bible.android.control.page.window.WindowControl] first).
     */
    private val appPersistence: WorkspacePersistence by inject()
    private var persistenceOverride: WorkspacePersistence? = null

    /** Tests substitute a persistence whose execution they control; production always uses the Koin one. */
    internal var persistence: WorkspacePersistence
        get() = persistenceOverride ?: appPersistence
        set(value) { persistenceOverride = value }
    val speakControl: SpeakControl by inject()
    private val windowStateService: WindowStateServiceImpl by inject()

    val windowSync: WindowSync = WindowSync(this)
    var unPinnedWeight: Float? = null
    var orderNumber: Int = 0
    val lastSyncWindow: Window? get() = getWindow(lastSyncWindowId)
    // CopyOnWriteArrayList (not a plain ArrayList): windowList is iterated on the background
    // cloud-sync thread (saveIntoDb, via MainBibleActivity.synchronize/periodicSync on
    // Dispatchers.IO) while the main thread structurally modifies it (opening/closing/moving
    // windows). A fail-fast ArrayList throws ConcurrentModificationException in that race
    // (OSTicket 3374); a copy-on-write list gives iterators a stable snapshot instead. Window
    // counts are tiny, so the copy-on-mutation cost is negligible.
    var windowList: MutableList<Window> = CopyOnWriteArrayList()
    var textDisplaySettings = WorkspaceEntities.TextDisplaySettings()
    var workspaceSettings = WorkspaceEntities.WorkspaceSettings.default
    var maximizedWindowId: IdType? = null
    var primaryTargetLinksWindowId: IdType? = null

    val primaryTargetLinksWindow: Window get() =
        getWindow(primaryTargetLinksWindowId)
            ?: run {
                windowList.find { it.isLinksWindow }?.also {
                    primaryTargetLinksWindowId = it.id
                }
            } ?: addNewLinksWindow().also {
                primaryTargetLinksWindowId = it.id
            }

    fun addNewLinksWindow(): Window {
        val sizeSource = activeWindow
        return addNewWindow().also {
            it.isSynchronised = false
            it.isLinksWindow = true
            // Again, now that it is a links window: addNewWindow set the weight while the window
            // could still be in the shared unpinned slot (Window.usesUnpinnedSlot), leaving its
            // own weight at whatever raw value the copied entity carried.
            it.weight = sizeSource.weight
        }
    }

    val isMaximized get() = maximizedWindowId != null
    val maximizedWindow get() = getWindow(maximizedWindowId)

    var id: IdType = IdType.empty()
    var name = ""
        set(value) {
            SharedActivityState.currentWorkspaceName = value
            field = value
        }

    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    private fun restoreInProgress(epoch: Long) = DatabaseContainer.replacing || epoch < DatabaseContainer.replaceEpoch

    /**
     * The single entry for every workspace write. Skipped when a restore is already running or this
     * repository was loaded before one; the epoch is captured now and re-checked inside the block,
     * so a restore (or reload) that happens before the queue drains skips the stale write (fix batch 5 §1.1).
     */
    private fun enqueueWrite(block: suspend net.bible.android.database.WorkspaceDao.() -> Unit) {
        val epoch = loadedEpoch
        if (restoreInProgress(epoch)) return
        persistence.enqueue { if (!restoreInProgress(epoch)) block() }
    }

    val sortedWindows: List<Window> get() = windowList.sortedWith(compareBy({it.isLinksWindow}, { !it.isPinMode }))

    /**
     * True for the whole of [loadFromDb]. Load starts with [clear], which nulls `_activeWindow`, so
     * [initialized] reads `false` until `setDefaultActiveWindow()` at the very end — and anything
     * that touches [activeWindow] in between would make its lazy getter call [initialize], which
     * calls [loadFromDb] again, RE-ENTRANTLY.
     *
     * That is not hypothetical: restoring a window's page emits `PageChange.BibleVerseChanged`
     * (`CurrentBibleVerse.setVerseSelected`, reached from `CurrentPageManager.restoreFrom`), which
     * `ComposeReadingViewHost` handles synchronously by rebuilding the toolbar snapshot from
     * `windowControl.activeWindowPageManager`. Measured on a workspace switch: 340 nested loads,
     * ~4.7 s of blocked main thread (ANR), and a [windowList] with hundreds of DUPLICATE windows —
     * each nesting level clears the list and the unwinding outer levels re-append their own. The
     * duplicate ids then crash the Compose reading view, whose panes are keyed by window id: two
     * panes resolve to the SAME cached `BibleView`, and the second `AndroidView` host throws
     * `IllegalStateException: The specified child already has a parent`.
     *
     * See also the guard in `ToolbarStateServiceImpl.refresh()`, which keeps that observer from
     * reading a repository that is mid-load at all.
     */
    private var loadingFromDb = false

    /**
     * First load of this repository, off the main thread: read phase, then the apply phase on Main.
     * Production cold start uses [initializeAtEdge] (the host's bootstrap is a synchronous sequence
     * whose next steps read the repository); this is the entry for callers that can suspend.
     */
    suspend fun initialize() {
        if(initialized || loadingFromDb) return
        val createIfMissing = beginInitialize()
        loadFromDb(id, createIfMissing)
        finishInitialize(createIfMissing)
    }

    /**
     * An empty id means first start of this repository: a missing or unknown stored workspace is
     * created, not replaced by another existing workspace.
     */
    private fun beginInitialize(): Boolean {
        val createIfMissing = id.isEmpty
        if(createIfMissing) {
            settings.getString("current_workspace_id")?.let { this.id = IdType(it) }
        }
        return createIfMissing
    }

    private fun finishInitialize(createIfMissing: Boolean) {
        // Only a freshly created (or re-pointed) workspace changes the stored id; a plain load never did.
        if (createIfMissing && settings.getString("current_workspace_id") != id.toString()) {
            settings.setString("current_workspace_id", id.toString())
        }
        val firstTime = settings.getBoolean("first-time", true)
        if(firstTime) {
            activeWindow.pageManager.setFirstUseDefaultVerse()
            settings.setBoolean("first-time", false)
        }
    }

    /**
     * The synchronous entry to the first load, for the callers that cannot suspend: the host bootstrap
     * and the [activeWindow] read before any load finished (media buttons, services and tools can read
     * it with no UI host at all, so a throwing getter would be reachable from production).
     *
     * Joins a [loadFromDb] that is already mid-read instead of reading twice: the read phase never needs
     * the main thread, so waiting for it cannot deadlock a main-thread reader, and the reader then
     * applies the result itself (the suspended load finds it claimed and skips its own apply).
     */
    fun initializeAtEdge() {
        if(initialized) return
        val pending = pendingLoad
        if(pending == null && loadingFromDb) return // mid-apply: the re-entrancy guard, see [loadingFromDb]
        val createIfMissing = if(pending == null) beginInitialize() else false
        if(pending == null) loadingFromDb = true
        try {
            if(pending == null) loadedEpoch = DatabaseContainer.replaceEpoch
            val readId = id
            val loaded = blockingAtEdge { // L1-edge: activeWindow read or host bootstrap before the first load finished
                pending?.loaded?.await() ?: currentLoader().read(readId, createIfMissing)
            }
            if(pending == null || pending.claim()) applyLoaded(loaded)
        } finally {
            if(pending == null) loadingFromDb = false
        }
        if(pending == null) finishInitialize(createIfMissing)
    }

    private var _activeWindow: Window? = null

    // 1 based screen no
    var activeWindow: Window
        get() {
            if(!initialized) initializeAtEdge()
            return _activeWindow!!
        }
        set (newActiveWindow) {
            if (!initialized || newActiveWindow != this._activeWindow) {
                _activeWindow = newActiveWindow
                Log.i(TAG, "Active window: $newActiveWindow")
                notifyActiveWindowChanged(newActiveWindow)
            }
            _activeWindow?.bibleView?.requestFocus()
        }

    val initialized get() = _activeWindow != null

    // When in maximized mode, keep track of last used
    // window that was synchronized
    var lastSyncWindowId: IdType? = null

    val visibleWindows: List<Window> get() {
        if (isMaximized) {
            val maxWindow = getWindow(maximizedWindowId)
            if (maxWindow != null) {
                return listOf(maxWindow)
            } else {
                maximizedWindowId = null
            }
        }
        return getWindows(WindowState.VISIBLE)
    }

    val minimisedWindows  get() = getWindows(WindowState.MINIMISED)

    val isMultiWindow get() = visibleWindows.size > 1

    private val defaultState = WindowState.VISIBLE

    val firstVisibleWindow: Window? get() = visibleWindows.firstOrNull()
    val lastVisibleWindow: Window? get() = visibleWindows.lastOrNull()

    private fun getDefaultActiveWindow() =
        windowList.find { it.isVisible } ?: createNewWindow(null, true)

    private fun setDefaultActiveWindow(): Window {
        val newWindow = getDefaultActiveWindow()
        activeWindow = newWindow
        return newWindow
    }

    private fun getWindows(state: WindowState)= sortedWindows.filter { it.windowState == state}

    fun getWindow(windowId: IdType?): Window? = if(windowId == null) null else sortedWindows.find {it.id == windowId}

    fun addNewWindow(sourceWindow: Window? = null): Window {
        Log.i(TAG, "addNewWindow $sourceWindow")
        val newWindow = createNewWindow(sourceWindow)
        newWindow.weight = (sourceWindow?: activeWindow).weight

        activeWindow.windowState = WindowState.VISIBLE

        return newWindow
    }

    fun getWindowsToSynchronise(sourceWindow: Window?): List<Window> = windowList.filter { it != sourceWindow }

    fun minimise(window: Window) {
        Log.i(TAG, "minimise $window")
        window.windowState = WindowState.MINIMISED

        // has the active screen been minimised?
        if (activeWindow == window) {
            setDefaultActiveWindow()
        }
    }

    fun close(window: Window) {
        Log.i(TAG, "close $window, windowList before: $windowList")
        window.windowState = WindowState.CLOSED
        val currentPos = windowList.indexOf(window)

        val closedId = window.id
        enqueueWrite { deleteWindow(closedId) }
        destroy(window)
        if(visibleWindows.isEmpty()) {
            activeWindow = windowList[min(currentPos, windowList.size - 1)]
            activeWindow.windowState = WindowState.VISIBLE
        } else setDefaultActiveWindow()
    }

    private fun destroy(window: Window) {
        Log.i(TAG, "destroy $window")
        if (!windowList.remove(window)) {
            Log.e(TAG, "Failed to remove window " + window.id)
        }
        window.destroy()
    }

    fun moveWindowToPosition(window: Window, position: Int) {
        Log.i(TAG, "moveWindowToPosition $window $position")
        val pinnedWindows = windowList.filter {it.isPinMode}.toMutableList()
        val unPinnedWindows = windowList.filter {!it.isPinMode}.toMutableList()
        val windowList = if(window.isPinMode) pinnedWindows else unPinnedWindows

        val originalWindowIndex = windowList.indexOf(window)

        if (originalWindowIndex == -1) {
            Log.w(TAG, "Attempt to move missing window")
            return
        }
        if (position > windowList.size) {
            Log.w(TAG, "Attempt to move window beyond end of window list")
            return
        }

        windowList.removeAt(originalWindowIndex)

        windowList.add(position, window)
        this.windowList.clear()
        this.windowList.addAll(pinnedWindows)
        this.windowList.addAll(unPinnedWindows)
    }

    private fun createNewWindow(sourceWindow_: Window?, first: Boolean = false): Window {
        val sourceWindow = sourceWindow_?: if(initialized) activeWindow else null
        val pageManager = get<CurrentPageManager>()
        val winEntity =
            (
                sourceWindow?.entity?.copy()
                    ?: WorkspaceEntities.Window(
                        isPinMode = true,
                        isSynchronized = true,
                        windowLayout = WorkspaceEntities.WindowLayout(defaultState.toString()),
                        workspaceId = id
                    )
                ).apply {
                    targetLinksWindowId = null
                    id = IdType()
                    deepCopy().let { copy -> this@WindowRepository.enqueueWrite { insertWindow(copy) } }
                }

        val newWindow = Window(winEntity, pageManager, this)
        if(sourceWindow != null) {
            pageManager.restoreFrom(
                sourceWindow.pageManager.entity, textDisplaySettings,
                (sourceWindow.pageManager.currentGeneralBook.key as? StudyPadKey)?.label,
            )
        }
        pageManager.entity.deepCopy().let { copy -> enqueueWrite { insertPageManager(copy) } }
        val pos =
            if(first) 0
            else if(sourceWindow?.isLinksWindow == true) windowList.size
            else windowList.indexOf(sourceWindow) + 1
        windowList.add(pos, newWindow)
        Log.i(TAG, "createNewWindow source:$sourceWindow new:$newWindow first:$first windowList after: $windowList")
        return newWindow
    }

    private val contentText: String get() {
        val keyTitle = ArrayList<String>()
        synchronized(BookName::class.java) {
            val prevFullBookNameValue = BookName.isFullBookName()
            BookName.setFullBookName(false)
            try {
                for (it in windowList) {
                    keyTitle.add("${it.pageManager.currentPage.singleKey?.getName()} (${it.pageManager.currentPage.currentDocument?.abbreviation})")
                }
            } finally {
                BookName.setFullBookName(prevFullBookNameValue)
            }
        }
        return keyTitle.joinToString(", ")
    }

    /** Restore epoch this repository's state was loaded at (fix batch 5 §1.1). */
    private var loadedEpoch = DatabaseContainer.replaceEpoch

    fun saveIntoDb(stopSpeak: Boolean = true) {
        Log.i(TAG, "saveIntoDb")
        if(!CommonUtils.initialized || !DatabaseContainer.ready) return
        // Before the first load there is nothing to save, and `savedEntity` is not set yet.
        if(!initialized) return
        // Fix batch 5 §1.1: mid-restore, or loaded before a restore and not reloaded since -- this
        // repository's state is pre-restore and would overwrite what was just restored.
        if (restoreInProgress(loadedEpoch)) {
            Log.i(TAG, "saveIntoDb skipped: database restore in progress or not yet reloaded")
            return
        }
        if(stopSpeak) speakControl.stop()
        workspaceSettings.speakSettings = SpeakSettings.currentSettings
        SpeakSettings.currentSettings?.save()
        val ws = WorkspaceEntities.Workspace(
            name = name,
            contentsText = contentText,
            id = id,
            orderNumber = orderNumber,
            textDisplaySettings = textDisplaySettings,
            workspaceSettings = workspaceSettings,
            unPinnedWeight = unPinnedWeight,
            maximizedWindowId = maximizedWindowId,
            primaryTargetLinksWindowId = primaryTargetLinksWindowId
        )
        val workspaceChanged = ws != savedEntity
        if(workspaceChanged) {
            savedEntity = ws.deepCopy()
        }

        val historyManager = get<HistoryManager>()
        val historyUpdates = windowList.map { it.id to historyManager.getEntities(it.id) }

        val windowEntities = windowList.mapIndexed { i, it ->
            val entity = it.entity.apply {
                orderNumber = i
            }
            if(it.savedEntity != entity) {
                it.savedEntity = entity.deepCopy()
                entity.deepCopy()
            }
            else null
        }.filterNotNull()

        val pageManagers = windowList.mapNotNull {
            if (it.pageManager.isModified) {
                it.pageManager.savedEntity = it.pageManager.entity.deepCopy()
                it.pageManager.entity.deepCopy()
            } else null
        }
        // Captured before return: the live workspace shares its settings lists, so a later mutation
        // must not reach the row.
        val workspaceCopy = if (workspaceChanged) ws.deepCopy() else null
        val historyCopies = historyUpdates.map { (windowId, entities) -> windowId to entities.map { it.copy() } }

        enqueueWrite {
            if (workspaceCopy != null) updateWorkspace(workspaceCopy)
            for ((windowId, entities) in historyCopies) updateHistoryItems(windowId, entities)
            updateWindows(windowEntities)
            updatePageManagers(pageManagers)
        }
    }

    lateinit var savedEntity: WorkspaceEntities.Workspace

    /**
     * Window-domain change notifiers (Batch 12a). Each refreshes the reactive SSOT
     * ([WindowStateServiceImpl.layout]) AND emits the matching [WindowChange], so both
     * are updated at one site. `WindowControl`/`Window` route their mutations through these.
     */
    fun notifyWindowsChanged() {
        windowStateService.refresh(this)
        windowStateService.notify(WindowChange.WindowsChanged)
    }
    fun notifyActiveWindowChanged(window: Window) {
        windowStateService.refresh(this)
        windowStateService.notify(WindowChange.ActiveWindowChanged(window))
    }
    /** Only refreshes the layout; nothing is emitted for a single-window change. */
    fun notifyWindowChanged(window: Window) {
        windowStateService.refresh(this)
    }
    /** Refreshes the layout and emits [WindowChange.RestoreButtonsChanged]; the only consumer is BibleView, which re-reads its bottom offsets. */
    fun notifyRestoreButtonsChanged() {
        windowStateService.refresh(this)
        windowStateService.notify(WindowChange.RestoreButtonsChanged)
    }

    /**
     * A load whose read phase is running. [claim] makes the apply phase happen exactly once, whoever
     * gets there first: the suspended load, or an [activeWindow] reader that joined it.
     */
    private class PendingLoad {
        val loaded = CompletableDeferred<LoadedWorkspace>()
        private val claimed = java.util.concurrent.atomic.AtomicBoolean(false)
        fun claim() = claimed.compareAndSet(false, true)
    }
    @Volatile private var pendingLoad: PendingLoad? = null

    /** Serialises loads: with a suspending read two switches can overlap, and the later one must win. */
    private val loadMutex = Mutex()

    /** Tests substitute a loader whose read they control; production builds one per load around the current [persistence]. */
    internal var loaderOverride: WorkspaceLoader? = null

    private fun currentLoader(): WorkspaceLoader = loaderOverride ?: WorkspaceLoader(
        persistence, { dao }, get<BookmarkControl>(), get<FakeBookFactory>().journalDocument.initials,
    )

    /**
     * Rebuilds this repository from the given (or first) workspace: the read phase off the main
     * thread, the apply phase on it. Not re-entrant — see [loadingFromDb].
     */
    suspend fun loadFromDb(workspaceId: IdType?) = loadFromDb(workspaceId, false)

    private suspend fun loadFromDb(workspaceId: IdType?, createIfMissing: Boolean) = loadMutex.withLock {
        val pending = PendingLoad()
        pendingLoad = pending
        loadingFromDb = true
        try {
            loadedEpoch = DatabaseContainer.replaceEpoch
            Log.i(TAG, "onLoadDb for workspaceId=$workspaceId")
            val loader = currentLoader()
            // The deferred completes on the read dispatcher, never on Main: a main-thread reader waiting on it
            // (see [initializeAtEdge]) would otherwise wait for the very thread it blocks.
            val loaded = withContext(Dispatchers.IO) {
                try {
                    loader.read(workspaceId, createIfMissing).also { pending.loaded.complete(it) }
                } catch (e: Throwable) {
                    pending.loaded.completeExceptionally(e)
                    throw e
                }
            }
            withContext(Dispatchers.Main.immediate) { if (pending.claim()) applyLoaded(loaded) }
        } finally {
            if (pendingLoad === pending) pendingLoad = null
            loadingFromDb = false
        }
    }

    /** The apply phase: replaces this repository's state wholesale from [loaded]. Synchronous; runs under [loadingFromDb]. */
    private fun applyLoaded(loaded: LoadedWorkspace) {
        pendingLoad = null
        val entity = loaded.entity
        savedEntity = entity.deepCopy()
        clear()

        orderNumber = entity.orderNumber
        id = entity.id
        name = entity.name
        unPinnedWeight = entity.unPinnedWeight
        maximizedWindowId = entity.maximizedWindowId
        primaryTargetLinksWindowId = entity.primaryTargetLinksWindowId

        textDisplaySettings = entity.textDisplaySettings ?: WorkspaceEntities.TextDisplaySettings()
        workspaceSettings = entity.workspaceSettings?: WorkspaceEntities.WorkspaceSettings.default
        SpeakSettings.currentSettings = workspaceSettings.speakSettings

        val historyManager = get<HistoryManager>()
        for (loadedWindow in loaded.windows) {
            val it = loadedWindow.entity
            val pageManager = get<CurrentPageManager>()
            pageManager.restoreFrom(loadedWindow.pageManager, textDisplaySettings, loadedWindow.studyPadLabel)
            val window = Window(it, pageManager, this)
            windowList.add(window)
            historyManager.restoreFrom(window, loadedWindow.history)
        }
        setDefaultActiveWindow()
        notifyWindowsChanged()
        // Everything workspace-scoped — colours included — has just been replaced wholesale. See
        // WorkspaceChange.Switched's kdoc for why this is not ColorEdited.
        WorkspaceChanges.notifySwitched()
    }

    fun clear(destroy: Boolean = false) {
        Log.i(TAG, "clear $destroy")
        _activeWindow = null
        maximizedWindowId = null
        unPinnedWeight = null
        orderNumber = 0
        id = IdType.empty()
        lastSyncWindowId = null
        primaryTargetLinksWindowId = null
        for (it in windowList) {
            it.bibleView?.listenEvents = false
            // The owner is discarded even when BibleViewFactory retains its view for reuse.
            it.destroy(destroyView = destroy)
        }
        windowList.clear()
        get<HistoryManager>().clear()
        name = ""
    }

    fun updateWindowTextDisplaySettingsValues(types: Set<WorkspaceEntities.TextDisplaySettings.Types>, settings: WorkspaceEntities.TextDisplaySettings) {
        for (it in windowList) {
            for (t in types) {
                val winValue = it.pageManager.textDisplaySettings.getValue(t)
                if (winValue == settings.getValue(t)) {
                    it.pageManager.textDisplaySettings.setNonSpecific(t)
                }
            }
        }
    }

    /**
     * When global settings change, propagate non-specificity to all workspaces and their windows.
     * If a workspace value matches the new global value, it is nulled (inherits from global).
     * If a window value matches its effective parent (workspace ?? global ?? default), it is nulled.
     * Updates both the database (all workspaces) and in-memory state (active workspace windows).
     */
    fun propagateGlobalTextDisplaySettingsChange(
        dirtyTypes: Set<WorkspaceEntities.TextDisplaySettings.Types>,
        globalSettings: WorkspaceEntities.TextDisplaySettings
    ) {
        val globalCopy = globalSettings.copy()
        val dirty = dirtyTypes.toSet()
        enqueueWrite {
            for (ws in allWorkspaces()) {
                val wsTds = ws.textDisplaySettings ?: continue
                var wsChanged = false
                for (t in dirty) {
                    if (wsTds.getValue(t) == globalCopy.getValue(t)) {
                        wsTds.setNonSpecific(t)
                        wsChanged = true
                    }
                }
                if (wsChanged) {
                    updateWorkspace(ws)
                }

                for (win in windows(ws.id)) {
                    val pm = pageManager(win.id) ?: continue
                    val pmTds = pm.textDisplaySettings ?: continue
                    var pmChanged = false
                    for (t in dirty) {
                        val parentValue = wsTds.getValue(t)
                            ?: globalCopy.getValue(t)
                            ?: WorkspaceEntities.TextDisplaySettings.default.getValue(t)
                        if (pmTds.getValue(t) == parentValue) {
                            pmTds.setNonSpecific(t)
                            pmChanged = true
                        }
                    }
                    if (pmChanged) {
                        updatePageManagers(listOf(pm))
                    }
                }
            }
        }

        // Update in-memory active workspace TDS
        val activeWsTds = textDisplaySettings
        for (t in dirtyTypes) {
            if (activeWsTds.getValue(t) == globalSettings.getValue(t)) {
                activeWsTds.setNonSpecific(t)
            }
        }

        // Update in-memory windows of the active workspace
        updateWindowTextDisplaySettingsValues(dirtyTypes, textDisplaySettings)
    }

    fun updateAllWindowsTextDisplaySettings() {
        for (it in sortedWindows) {
            it.bibleView?.updateTextDisplaySettings()
        }
    }

    fun updateRecentLabels(labelIds: List<IdType>) {
        Log.i(TAG, "updateRecentLabels")
        for(labelId in labelIds) {
            val existingLabel = workspaceSettings.recentLabels.find { it.labelId == labelId }
            if (existingLabel != null) {
                existingLabel.lastAccess = System.currentTimeMillis()
                workspaceSettings.recentLabels.sortBy { it.lastAccess }
            } else {
                workspaceSettings.recentLabels.add(WorkspaceEntities.RecentLabel(labelId, System.currentTimeMillis()))
                while (workspaceSettings.recentLabels.size > 15) {
                    workspaceSettings.recentLabels.removeAt(0)
                }
            }
        }
        WorkspaceChanges.notifySettingsEdited()
    }

    companion object {
        private const val TAG = "WinRepository"
    }
}
