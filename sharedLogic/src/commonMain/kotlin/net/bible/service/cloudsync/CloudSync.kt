/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.cloudsync

import kotlinx.coroutines.IO
import net.bible.sharedcore.ui.dialog.DialogAnswer
import okio.Path.Companion.toPath
import okio.Path
import okio.FileSystem
import net.bible.sharedcore.platform.cloud.CloudFile
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock
import net.bible.sharedcore.platform.CoreStrings

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.sync.SyncScheduler
import androidx.annotation.VisibleForTesting
import net.bible.service.db.useWriterConnectionMarked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.event.UserMessages
import net.bible.android.database.SyncStatus
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.service.common.asyncMap
import net.bible.service.common.createTempFile
import net.bible.service.cloudsync.merge.mergeSnapshotIntoLocal
import net.bible.service.cloudsync.merge.prepareMergeSnapshot
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AiDatabaseLifetime
import net.bible.service.db.exec
import net.bible.service.db.openSqlite
import net.bible.service.db.queryLong
import net.bible.service.db.requireIntactDatabase
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import okio.IOException

const val GZIP_MIMETYPE = "application/gzip"

const val SYNC_FOLDER_FILE_ID_KEY = "syncId"
const val SYNC_DEVICE_FOLDER_FILE_ID_KEY = "deviceFolderId"
const val LAST_PATCH_WRITTEN_KEY = "lastPatchWritten"

/** Per-category config: the [SyncNamespace] this category's stored sync folder was settled under. */
const val SYNC_NAMESPACE_KEY = "syncNamespace"
const val INITIAL_BACKUP_FILENAME = "initial.sqlite3.gz"
const val TAG = "DeviceSync"

/** Bound on a cloud provider's own sign-out (osTicket 3515: Play services may never answer). */
private const val PROVIDER_SIGN_OUT_TIMEOUT_MS = 10_000L

class CancelStartedSync: Exception()

/**
 * The category's sync folder ids are not stored although a sync step needs them (a reset or an interrupted join
 * removed them). Not an error to show: the next pass's probe (see [probeJoin]) sees no folder and joins afresh.
 */
class MissingSyncFolderIds(category: String): Exception("Sync folder ids missing for $category")

class CloudSync(
    private val dialogs: AppDialogController,
    private val strings: CoreStrings,
    private val env: net.bible.sharedcore.platform.AppEnvironment,
    private val settings: net.bible.sharedcore.platform.AppSettings,
    private val adapters: CloudAdapterFactory,
    private val scheduler: SyncScheduler,
    private val documentSyncStarter: net.bible.sharedcore.cloud.DocumentSyncStarter,
    private val documents: DocumentSyncProvider,
    private val documentSettings: DocumentSyncSettings,
    private val syncNamespace: String? = null,
) {
    var adapterId: String
        // An unavailable saved backend is disabled, never replaced by a different account/backend.
        get() = adapters.effectiveAdapterId(settings)
        set(value) {
            if (value != adapterId) _adapter = null
            settings.setString("sync_adapter", value)
        }
    /** Fixes the backend before the first category is enabled (see [pinAdapterOnEnable]). */
    fun pinAdapterOnEnable() = adapters.pinAdapterOnEnable(settings)
    val adapterAvailable: Boolean get() = adapters.isAvailable(settings)
    val syncEnabled: Boolean get() = adapterAvailable && SyncableDatabaseDefinition.ALL.any { it.isSyncEnabled(settings) }
    val adapterChoices get() = adapters.available.map { SettingsItem.Choice(it, adapters.displayName(it)) }

    private var runningSource = EventSource<Boolean>()
    private val runningStateMutable = kotlinx.coroutines.flow.MutableStateFlow(false)
    val runningState: kotlinx.coroutines.flow.StateFlow<Boolean> get() = runningStateMutable

    /** `true` when a cloud sync starts, `false` when it finishes (replaces `CloudSyncEvent`). */
    val runningChanged: Events<Boolean> get() = runningSource

    fun notifySyncRunning(running: Boolean) {
        runningStateMutable.value = running
        runningSource.emit(running)
    }

    private var refreshSource = EventSource<Unit>()

    /** The last concurrent database replace ended; the reading host reloads its workspace (replaces the retired WorkspaceRefreshRequired bus event). */
    val workspaceRefreshRequired: Events<Unit> get() = refreshSource

    @VisibleForTesting fun notifyWorkspaceRefreshRequired() {
        refreshSource.emit(Unit)
    }

    @VisibleForTesting
    fun resetSubscribersForTest() { runningSource = EventSource(); refreshSource = EventSource() }


    private var _adapter: CloudAdapter? = null
    private val adapter: CloudAdapter get() = _adapter!!

    // adapterAvailable reads settings, so it goes last: callers such as BackupControl.beforeRestore ask while the
    // databases are being replaced, and an adapter that never signed in must answer without touching them.
    val signedIn get() = !signInMutex.isLocked && _adapter?.signedIn == true && adapterAvailable

    /**
     * The adapter document sync may use: waits out a sign-in that is running, then returns the adapter only when it is
     * signed in (null otherwise, so document sync stays a quiet no-op as when never signed in).
     *
     * [signIn] publishes `_adapter` before the adapter's own sign-in has finished, so reading `_adapter` directly hands
     * out a half-built adapter. At startup the background pass signs in while [net.bible.android.control.versification.BookInstallWatcher]
     * queues auto-upload ops for every MyBible/EPUB book it registers; those ops failed with "WebDAV adapter not signed in",
     * one sync error per document (#4031). Waiting shares the running sign-in: no second attempt, no prompt.
     */
    internal suspend fun signedInAdapter(): CloudAdapter? {
        while (signInMutex.isLocked) {
            val running = signInDone
            // No running deferred: the holder has not assigned it yet (a gap of one statement), or a `waitForExisting`
            // caller holds the lock for a single read. Poll briefly instead of returning early or spinning hot.
            if (running != null && !running.isCompleted) running.await() else kotlinx.coroutines.delay(1)
        }
        return _adapter?.takeIf { it.signedIn && adapterAvailable }
    }

    val DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX = "documents"

    /** Test helper over the current adapter, signed in or not; document sync uses [signedInAdapter] (#4031). */
    suspend fun documentsSyncFolderId(): String? = documentsSyncFolderId(_adapter ?: return null)

    internal suspend fun documentsSyncFolderId(adapter: CloudAdapter): String? {
        val name = syncFolderName(DOCUMENTS_SYNC_FOLDER_NAME_SUFFIX)
        val existing = adapter.listFiles(name = name).firstOrNull()
        return existing?.id ?: adapter.createNewFolder(name).id
    }

    private val signInMutex = Mutex()
    /** Completed when the sign-in holding [signInMutex] ends; [signedInAdapter] waits on it without taking the lock. */
    @kotlin.concurrent.Volatile private var signInDone: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    /** Whether the last sign-in that failed already told the user (a foreground dialog, or a cancel); false only for a genuine background failure. */
    @kotlin.concurrent.Volatile private var lastSignInFailureReported = true
    suspend fun signIn(background: Boolean = false, onForegroundFailure: (String) -> Unit = {}, waitForExisting: Boolean = false): Boolean? {
        if (!adapterAvailable) return false
        if (!signInMutex.tryLock()) {
            Log.i(TAG, "Already signing in!")
            if (!waitForExisting) return null
            val ok = signInMutex.withLock { _adapter?.signedIn == true }
            // The sign-in waited for may have been a background one, which reports nothing: tell the waiting caller.
            // A foreground one showed its own dialog and a user cancel is no failure: nothing more to say then.
            if (!ok && !background && !lastSignInFailureReported) UserMessages.errorNotification(strings.signInFailedLead, showReportButton = false, background = false, kind = ActivityKind.SYNC)
            return ok
        }
        val done = kotlinx.coroutines.CompletableDeferred<Unit>().also { signInDone = it }
        var errorMessage: String? = null
        var cancelledByUser = false
        var aborted = false
        val success = try {
            // #4102 one-time pin. Here and not in the constructor: Koin builds CloudSync on Main during startup
            // (BackupControl), where reading settings cold-opens the database (CoreModuleResolveTest). Every sync
            // signs in first, and this already reads the settings for adapterId; until it runs, effectiveAdapterId
            // gives the same answer.
            adapters.pinLegacyAdapterIfInUse(settings)
            _adapter = if (background) adapters.createForBackground(adapterId) else adapters.create(adapterId)
            try {
                adapter.signIn()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _adapter = null
                aborted = true
                throw e
            } catch (e: CloudSyncUserCancelledException) {
                cancelledByUser = true
                false
            } catch (e: Exception){
                // Logged always: a background pass shows nothing for a failed sign-in, so the log is its only trace (#4031).
                Log.e(TAG, "Sync sign-in failed (background=$background): ${e::class.simpleName}: ${e.message}", e)
                errorMessage = e.message
                false
            }.also { if (!it) _adapter = null }
        } finally {
            lastSignInFailureReported = !background || cancelledByUser || aborted
            signInMutex.unlock()
            done.complete(Unit)
        }
        if(!success) {
            if (errorMessage == null) Log.w(TAG, "Sync sign-in did not succeed (background=$background, cancelled by user=$cancelledByUser)")
            if (!background && !cancelledByUser) {
                val lead = strings.signInFailedLead
                onForegroundFailure(if (errorMessage.isNullOrBlank()) lead else "$lead $errorMessage")
                dialogs.await(AppDialogRequest.Message(null, lead, strings.okay, cancellable = false, detail = errorMessage?.takeIf { it.isNotBlank() }))
            }
        }
        return success
    }
    suspend fun signOut() {
        // A running sync still needs its adapter and folder IDs through the patch download.
        syncMutex.withLock {
            _adapter?.let { signOutOfProvider(it) }
            _adapter = null
            // Stop any in-flight document transfers before wiping their state: they belong to the
            // account just disconnected. store() already returns null now (_adapter == null), so a
            // running drain would no-op anyway, but cancelling it promptly avoids a needless drain.
            clearStoredJoins()
            documentSyncStarter.stop()
            documents.get().onSignOut()
            DatabaseContainer.databaseAccessorFactories.asyncMap {
                val dbDef = it.invoke()
                val category = dbDef.category
                category.setSyncEnabled(settings, false)
                dbDef.dao.clearSyncStatus()
                dbDef.dao.clearSyncConfiguration()
            }
        }
    }

    /**
     * The provider's own sign-out is best effort: the local reset must complete whatever the provider does (osTicket 3515).
     * Google's sign-out goes through Play services, which can fail (unavailable) or never answer (disabled, missing or
     * updating: it retries every few seconds). A failure escaped into the settings screen's lifecycle scope and crashed
     * the app; a hang kept "Sign Out" spinning with the sync lock held. Both now end in a logged, bounded no-op.
     */
    private suspend fun signOutOfProvider(adapter: CloudAdapter) {
        try {
            val finished = kotlinx.coroutines.withTimeoutOrNull(PROVIDER_SIGN_OUT_TIMEOUT_MS) { adapter.signOut(); true }
            if (finished == null) Log.w(TAG, "Provider sign-out did not finish in $PROVIDER_SIGN_OUT_TIMEOUT_MS ms; resetting locally")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Our own cancellation propagates; a provider task that was cancelled (Task.await) is just a failed sign-out.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            Log.w(TAG, "Provider sign-out was cancelled; resetting locally", e)
        } catch (e: Exception) {
            Log.e(TAG, "Provider sign-out failed; resetting locally: ${e::class.simpleName}: ${e.message}", e)
        }
    }

    enum class InitialOperation {MERGE, FETCH_INITIAL, CREATE_NEW}
    private val planMutex = Mutex()

    /**
     * The join questions of one sync start as one collected dialog: a decision per [JoinState.NeedsDecision] probe
     * and a note per [JoinState.NeedsCreate] one, confirmed once. Returns the operation per category (null for a
     * category the user left off), or null when the user cancelled: nothing may then be executed. With no
     * [JoinState.NeedsDecision] probe nothing is asked and every [JoinState.NeedsCreate] one gets
     * [InitialOperation.CREATE_NEW]. One plan dialog at a time ([planMutex]).
     */
    @VisibleForTesting suspend fun askJoinPlan(probes: List<JoinProbe>): Map<SyncableDatabaseDefinition, InitialOperation?>? {
        val decisions = probes.filter { it.state is JoinState.NeedsDecision }
        val creates = probes.filter { it.state is JoinState.NeedsCreate }
        val createOps: Map<SyncableDatabaseDefinition, InitialOperation?> = creates.associate { it.category to InitialOperation.CREATE_NEW }
        if (decisions.isEmpty()) return createOps
        val name = { c: SyncableDatabaseDefinition -> strings.syncCategoryContents(c.name) }
        val request = AppDialogRequest.SyncJoinPlan(
            title = strings.cloudJoinPlanTitle,
            lead = strings.cloudJoinPlanLead,
            rows = decisions.map { AppDialogRequest.SyncJoinPlan.Row(it.category.name, name(it.category)) },
            uploadNotes = creates.map { strings.cloudJoinUploadNote(name(it.category)) },
            choices = listOf(
                DialogAnswer(JoinChoice.MERGE.value, strings.cloudJoinMerge, strings.cloudJoinMergeDetail),
                DialogAnswer(JoinChoice.FETCH.value, strings.cloudJoinFetch, strings.cloudJoinFetchDetail, destructive = true),
                DialogAnswer(JoinChoice.CREATE.value, strings.cloudJoinCreate, strings.cloudJoinCreateDetail, destructive = true),
                DialogAnswer(JoinChoice.DISABLE.value, strings.cloudJoinDisable, strings.cloudJoinDisableDetail),
            ),
            defaultChoice = JoinChoice.MERGE.value,
            confirmTitle = strings.cloudJoinConfirmTitle,
            confirmLead = strings.cloudJoinConfirmLead,
            outcomeLine = { key, choice ->
                val label = strings.syncCategoryContents(key)
                when (JoinChoice.fromValue(choice)) {
                    JoinChoice.MERGE -> strings.cloudJoinOutcomeMerge(label)
                    JoinChoice.FETCH -> strings.cloudJoinOutcomeFetch(label)
                    JoinChoice.CREATE -> strings.cloudJoinOutcomeCreate(label)
                    JoinChoice.DISABLE, null -> strings.cloudJoinOutcomeDisable(label)
                }
            },
            createOutcomes = creates.map { strings.cloudJoinOutcomeUpload(name(it.category)) },
            warning = strings.cloudJoinWarning,
            nextText = strings.cloudJoinNext,
            backText = strings.cloudJoinBack,
            cancelText = strings.cancel,
            startText = strings.cloudJoinStart,
            replaceText = strings.cloudJoinReplace,
        )
        Log.i(TAG, "planMutex ahead ${decisions.map { it.category }}...")
        val result = planMutex.withLock {
            Log.i(TAG, "... got through planMutex!")
            withContext(Dispatchers.Main) { dialogs.await(request) }
        }
        val chosen = (result as? AppDialogResult.SyncJoinChoices)?.choices ?: return null // Cancel, back or scrim.
        return decisions.associate {
            val choice = JoinChoice.fromValue(chosen[it.category.name])
            if (choice == null) Log.w(TAG, "No valid join choice for ${it.category}: '${chosen[it.category.name]}', not syncing it")
            it.category to choice?.toOperation()
        } + createOps
    }

    /**
     * A join decision the user already confirmed ([planJoins]); [fromDecision] false for a `NeedsCreate` category.
     * [folderId] is the cloud folder a decision was made against (null for a create): a decision is only usable while
     * the cloud still shows that folder. A class, not a data class: instances are compared by identity (see [storedJoinToken]).
     */
    private class StoredJoin(val operation: InitialOperation, val fromDecision: Boolean, val folderId: String? = null)

    private val joinStateLock = SynchronizedObject()
    /** Categories whose plan dialog is open (a count per category, so overlapping [planJoins] calls do not clear each other). */
    private val awaitingJoins = HashMap<SyncableDatabaseDefinition, Int>()
    private val storedJoins = LinkedHashMap<SyncableDatabaseDefinition, StoredJoin>()
    private val queuedJoinsMutable = MutableStateFlow<Set<SyncableDatabaseDefinition>>(emptySet())

    /** Categories with a stored, not yet executed join decision: they wait for the next pass (and its [syncMutex]). */
    val queuedJoins: StateFlow<Set<SyncableDatabaseDefinition>> get() = queuedJoinsMutable

    private fun isAwaitingJoin(category: SyncableDatabaseDefinition) = synchronized(joinStateLock) { category in awaitingJoins } // L1-common-ok: atomicfu lock

    /** Removes and returns the stored decision of [category]; the caller is about to act on it (or drop it). */
    private fun takeStoredJoin(category: SyncableDatabaseDefinition): StoredJoin? = synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
        storedJoins.remove(category).also { queuedJoinsMutable.value = storedJoins.keys.toSet() }
    }

    /** Removes the decision of [category] only if it is still [expected]; returns it, or null when another decision (or none) is stored. */
    private fun takeStoredJoinIfSame(category: SyncableDatabaseDefinition, expected: Any): StoredJoin? = synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
        if (storedJoins[category] !== expected) null
        else storedJoins.remove(category).also { queuedJoinsMutable.value = storedJoins.keys.toSet() }
    }

    private fun peekStoredJoin(category: SyncableDatabaseDefinition): StoredJoin? = synchronized(joinStateLock) { storedJoins[category] } // L1-common-ok: atomicfu lock

    /** An opaque identity of the decision stored for [category] right now (null: none), for [dropStoredJoinIfSame]. */
    fun storedJoinToken(category: SyncableDatabaseDefinition): Any? = peekStoredJoin(category)

    /** Drops the decision of [category] only if it is still the one [token] names: a later decision of another caller survives. */
    fun dropStoredJoinIfSame(category: SyncableDatabaseDefinition, token: Any?) {
        if (token == null) return
        synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
            if (storedJoins[category] === token) { storedJoins.remove(category); queuedJoinsMutable.value = storedJoins.keys.toSet() }
        }
    }

    /** Drops the stored decision of [category], if any: a decision must not outlive the switch (or pass) that made it moot. */
    fun dropStoredJoin(category: SyncableDatabaseDefinition) {
        synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
            if (storedJoins.remove(category) != null) queuedJoinsMutable.value = storedJoins.keys.toSet()
        }
    }

    private fun clearStoredJoins() = synchronized(joinStateLock) { storedJoins.clear(); queuedJoinsMutable.value = emptySet() } // L1-common-ok: atomicfu lock

    /** Turns [categories] off and drops their stored decisions: a decision must not outlive the choice that disabled the category. */
    private fun disableJoining(categories: Collection<SyncableDatabaseDefinition>) {
        synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
            categories.forEach { storedJoins.remove(it); it.setSyncEnabled(settings, false) }
            queuedJoinsMutable.value = storedJoins.keys.toSet()
        }
    }

    /** Reports a failed [planJoins] probe directly (no pass runs it): the same three-way split as the pass's init step. */
    private fun reportPlanProbeFailure(dbDef: SyncableDatabaseAccessor<*>, e: Exception) {
        logSyncFailure(dbDef, "probe", e, if (e is IOException) "probably network down" else null)
        val text = when (e) {
            is CloudSyncUserFacingException -> e.message?.takeIf { it.isNotBlank() } ?: strings.syncError
            else -> strings.syncError
        }
        UserMessages.errorNotification(text, showReportButton = e !is CloudSyncUserFacingException, background = false, kind = ActivityKind.SYNC, cause = e)
    }

    /**
     * Plans the join of [categories] outside [syncMutex], so it completes while a pass is running: the categories are
     * marked awaiting (a concurrent pass skips them), earlier stored decisions for them are dropped, they are probed in
     * parallel (the probe is read-only), [onProbed] is called, then one [askJoinPlan] is asked. The confirmed decisions are
     * stored for the next pass ([queuedJoins]), which runs them under [syncMutex]; "Don't sync" disables its category at once.
     * A stored decision is consumed when its pass starts executing the category: if that execute fails the decision is
     * gone and the next pass asks again.
     *
     * With [enable] the categories are turned on after the awaiting mark is set, so no pass can see them enabled but not
     * awaiting; every failure path reverts that together with the cancel handling.
     *
     * Returns false when the user cancelled: exactly [categories] are then turned off. Any cancellation of the caller
     * (also during the probe or [onProbed]) turns them off too and is rethrown. The awaiting marks are always cleared.
     * A category whose probe fails is reported as a sync error and left as it is (the next pass joins it). The caller
     * signs in first. A [CloudSyncUserFacingException.requiresReconnect] is not acted on here (dropping the adapter
     * would break a running pass); the next pass's own handling does.
     */
    suspend fun planJoins(categories: Set<SyncableDatabaseDefinition>, onProbed: () -> Unit = {}, enable: Boolean = false): Boolean {
        synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
            categories.forEach { awaitingJoins[it] = (awaitingJoins[it] ?: 0) + 1; storedJoins.remove(it) }
            queuedJoinsMutable.value = storedJoins.keys.toSet()
        }
        try {
            if (enable) categories.forEach { it.setSyncEnabled(settings, true) }
            // A category disabled for this database version is skipped by every pass: no probe, no question.
            val accessors = categories.mapNotNull { DatabaseContainer.databaseAccessorsByCategory[it] }
                .filter { it.dao.getLong("disabledForVersion") != it.version().toLong() }
            val probes = accessors.asyncMap { dbDef ->
                try {
                    probeJoin(dbDef)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportPlanProbeFailure(dbDef, e)
                    null
                }
            }.filterNotNull()
            onProbed()
            val asked = probes.filter { it.state is JoinState.NeedsDecision || it.state is JoinState.NeedsCreate }
            if (asked.isEmpty()) return true
            val plan = askJoinPlan(asked)
            if (plan == null) {
                disableJoining(categories)
                return false
            }
            synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
                for (probe in asked) {
                    val operation = plan[probe.category]
                    val fromDecision = probe.state is JoinState.NeedsDecision
                    if (operation == null) { storedJoins.remove(probe.category); probe.category.setSyncEnabled(settings, false) }
                    else storedJoins[probe.category] = StoredJoin(operation, fromDecision, (probe.state as? JoinState.NeedsDecision)?.preliminaryFolderId)
                }
                queuedJoinsMutable.value = storedJoins.keys.toSet()
            }
            return true
        } catch (e: CancellationException) {
            disableJoining(categories)
            throw e
        } catch (e: Exception) {
            if (enable) disableJoining(categories)
            throw e
        } finally {
            synchronized(joinStateLock) { // L1-common-ok: atomicfu lock
                categories.forEach {
                    val left = (awaitingJoins[it] ?: 1) - 1
                    if (left <= 0) awaitingJoins.remove(it) else awaitingJoins[it] = left
                }
            }
        }
    }

    /** The folder-name prefix: the debug override, or the app id mapped through [SyncNamespace] so release distributions share folders. */
    internal val namespace: String get() = syncNamespace ?: SyncNamespace.forAppId(env.packageName)

    /**
     * The name this install uses for its device folder and its [SyncStatus] rows.
     *
     * [AppEnvironment.deviceIdentifier] is per (signing key, user, device) on Android 8+, so builds signed with
     * the same key (GitHub standard, GitHub discrete, Accrescent) report the same value when installed side by
     * side. Now that they share one sync folder ([SyncNamespace]) they would also share one device folder and
     * number their patches independently, so two installs could upload the same patch number and overwrite
     * each other. A package that [SyncNamespace] maps to the shared namespace therefore appends a short hash of
     * its package id. The hash (not a readable tag) keeps the discrete build's disguise: no package name shows
     * up in cloud folder names.
     *
     * The raw identifier is kept when the package is its own namespace (standard, suffixed and unknown ids), so
     * existing users' device folders do not change. Non-standard builds reset their category state and join
     * afresh on their first sync with the shared folder, so no migration of the old name is needed.
     */
    @VisibleForTesting val syncDeviceName: String get() =
        if (SyncNamespace.forAppId(env.packageName) == env.packageName) env.deviceIdentifier
        else "${env.deviceIdentifier}-${shortStableHash(env.packageName)}"

    @VisibleForTesting fun syncFolderName(category: String): String = "$namespace-sync-$category"

    /**
     * Whether the category's stored sync state belongs to another [namespace] and must be dropped (see
     * [applyNamespaceReset]). The namespace of the stored state is the [SYNC_NAMESPACE_KEY] config, or, for state that
     * predates the key, the name the old code derived from the raw app id. A standard build's old name equals its
     * namespace, so an upgrade keeps all state. Read-only.
     */
    private suspend fun namespaceChanged(dbDef: SyncableDatabaseAccessor<*>): Boolean {
        val stored = dbDef.dao.getString(SYNC_NAMESPACE_KEY)
        if (stored == namespace) return false
        val previous = stored ?: (syncNamespace ?: env.packageName)
        if (previous != namespace) {
            Log.i(TAG, "Sync namespace of ${dbDef.categoryName} changed $previous -> $namespace, starting a fresh join")
            return true
        }
        return false
    }

    /**
     * Drops the stored folder ids, the sync position and the adapter's "folder known" marker, which belong to a folder
     * this build no longer uses; the category joins the current folder afresh (first-join question included).
     *
     * The namespace key itself is stored by [executeJoin] once the folder is settled, every time: a first-join fetch
     * swaps the whole database file, config table included, and the uploader's file may lack the key.
     */
    private suspend fun applyNamespaceReset(dbDef: SyncableDatabaseAccessor<*>) {
        // The adapter knows which configs prove a folder known (its secret marker); removing them via
        // getConfigs keeps adapter-specific keys out of this common code.
        adapter.getConfigs(dbDef).forEach { dbDef.dao.removeConfig(it.keyName) }
        dbDef.dao.removeConfig(SYNC_FOLDER_FILE_ID_KEY)
        dbDef.dao.removeConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY)
        dbDef.dao.removeConfig(LAST_SYNCHRONIZED_KEY)
        dbDef.dao.removeConfig(LAST_PATCH_WRITTEN_KEY)
    }

    /**
     * Read-only look at what joining [dbDef] would take: no writes to the database, the adapter or the file system
     * (only `getString`, `isSyncFolderKnown`, `listFiles` and `exists` are used). [executeJoin] acts on the result.
     *
     * With [listCloud] false (a pass that may not ask the user) the cloud folder is not listed: an unjoined category
     * then reports [JoinState.NeedsCreate] as a placeholder, because the caller only cancels it.
     */
    internal suspend fun probeJoin(dbDef: SyncableDatabaseAccessor<*>, listCloud: Boolean = true): JoinProbe {
        val resetNamespace = namespaceChanged(dbDef)
        fun probe(state: JoinState, dropStaleState: Boolean = false, cloudListed: Boolean = true) =
            JoinProbe(dbDef.category, state, resetNamespace, dropStaleState, cloudListed)
        if (hasInterruptedMerge(dbDef)) return probe(JoinState.NeedsRecovery)
        var dropStaleState = false
        // After a namespace reset the folder id is gone, so the category is joined afresh.
        val syncFolderId = if (resetNamespace) null else dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY)
        if (syncFolderId != null) {
            val known = adapter.isSyncFolderKnown(dbDef, name = syncFolderName(dbDef.categoryName), id = syncFolderId)
            // A folder id without this device's folder id is a half-finished join: join afresh.
            if (known && dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY) != null) return probe(JoinState.Ready)
            dropStaleState = true
        }
        if (!listCloud) return probe(JoinState.NeedsCreate, dropStaleState, cloudListed = false)
        val cloudFolder = adapter.listFiles(name = syncFolderName(dbDef.categoryName)).firstOrNull()
        return probe(if (cloudFolder != null) JoinState.NeedsDecision(cloudFolder.id) else JoinState.NeedsCreate, dropStaleState)
    }

    /** Applies the probe's clean-ups (namespace reset, stale-state removal), the only writes before the join acts. */
    private suspend fun applyProbeCleanups(dbDef: SyncableDatabaseAccessor<*>, probe: JoinProbe) {
        if (probe.resetNamespace) applyNamespaceReset(dbDef)
        // Watermarks too: a stale LAST_SYNCHRONIZED_KEY would make the fresh join skip older patches.
        if (probe.dropStaleState) removeSyncState(dbDef)
    }

    /** Test-only: production passes and [planJoins] go through [probeJoin] and [executeJoin]; this runs both for one category. */
    @VisibleForTesting suspend fun initializeSync(dbDef: SyncableDatabaseAccessor<*>, allowInteraction: Boolean = true, backgroundErrors: Boolean = !allowInteraction) {
        val probe = probeJoin(dbDef, listCloud = allowInteraction)
        var operation: InitialOperation? = null
        when (probe.state) {
            JoinState.Ready, JoinState.NeedsRecovery -> {}
            JoinState.NeedsCreate, is JoinState.NeedsDecision -> {
                // An OS refresh must not decide whether to fetch, create, or disable a category.
                if (!allowInteraction) {
                    applyProbeCleanups(dbDef, probe)
                    throw CancelStartedSync()
                }
                operation = askJoinPlan(listOf(probe))?.get(dbDef.category)
            }
        }
        executeJoin(dbDef, probe, operation, backgroundErrors)
    }

    /**
     * Carries out the join [probe] described: the clean-ups, then recovery, the chosen [operation] and the folder
     * bookkeeping. [operation] is only read for [JoinState.NeedsDecision]; null there means the user disabled the
     * category.
     */
    internal suspend fun executeJoin(dbDef: SyncableDatabaseAccessor<*>, probe: JoinProbe, operation: InitialOperation?, backgroundErrors: Boolean) {
        val syncFolderName = syncFolderName(dbDef.categoryName)
        applyProbeCleanups(dbDef, probe)
        var initialOperation: InitialOperation? = null
        var syncFolderId: String? = null
        var preliminarySyncFolderId: String? = null
        when (val state = probe.state) {
            JoinState.Ready -> {}
            JoinState.NeedsRecovery -> recoverInterruptedMerge(dbDef, background = backgroundErrors)
            JoinState.NeedsCreate -> initialOperation = InitialOperation.CREATE_NEW
            is JoinState.NeedsDecision -> {
                if (operation == null) {
                    dbDef.category.setSyncEnabled(settings, false)
                    throw CancelStartedSync()
                }
                preliminarySyncFolderId = state.preliminaryFolderId
                initialOperation = operation
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, state.preliminaryFolderId)
                syncFolderId = state.preliminaryFolderId
                adapter.makeSyncFolderKnown(dbDef, syncFolderName, state.preliminaryFolderId)
            }
        }

        suspend fun createNewSyncFolder(): String {
            Log.i(TAG, "Creating new sync folder ${dbDef.categoryName} $syncFolderName")

            // If there is already sync folder, let's remove it (and its contents)
            if(preliminarySyncFolderId != null) {
                Log.i(TAG, "Deleting earlier sync folder $preliminarySyncFolderId")
                adapter.delete(preliminarySyncFolderId)
            }

            return adapter.createNewFolder(syncFolderName).id.also {
                Log.i(TAG, "Global sync folder id $it")
                adapter.makeSyncFolderKnown(dbDef, syncFolderName, it)
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, it)
                syncFolderId = it
            }
        }

        suspend fun createNewDeviceSyncFolder() {
            val deviceName = syncDeviceName
            Log.i(TAG, "Creating new device sync folder $syncFolderName/$deviceName")
            adapter.createNewFolder(
                name = deviceName,
                parentId = syncFolderId,
            )
                .id.also {
                    Log.i(TAG, "This device sync folder id $it")
                    dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, it)
                }
        }

        when(initialOperation) {
            InitialOperation.CREATE_NEW -> {
                createNewSyncFolder()
                createNewDeviceSyncFolder()
                createAndUploadInitial(dbDef)
            }
            InitialOperation.FETCH_INITIAL -> {
                createNewDeviceSyncFolder()
                fetchAndRestoreInitial(dbDef)
            }
            InitialOperation.MERGE -> mergeIntoCloudFolder(dbDef, ::createNewDeviceSyncFolder, backgroundErrors)
            null -> {}
        }
        // Only now, and always (a fetch swap may have dropped it): a cancelled first join leaves the key unset,
        // so the next attempt re-runs the reset harmlessly.
        dbDef.dao.setConfig(SYNC_NAMESPACE_KEY, namespace)
    }

    private suspend fun requireSyncFolderId(dbDef: SyncableDatabaseAccessor<*>): String =
        dbDef.dao.getString(SYNC_FOLDER_FILE_ID_KEY) ?: throw MissingSyncFolderIds(dbDef.categoryName)

    private suspend fun requireDeviceFolderId(dbDef: SyncableDatabaseAccessor<*>): String =
        dbDef.dao.getString(SYNC_DEVICE_FOLDER_FILE_ID_KEY) ?: throw MissingSyncFolderIds(dbDef.categoryName)

    private suspend fun createAndUploadInitial(dbDef: SyncableDatabaseAccessor<*>) {
        dbDef.dao.clearLog()
        dbDef.dao.clearSyncStatus()
        dbDef.localDb.useWriterConnectionMarked { it.exec("VACUUM;") }
        val tmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        val gzippedTmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        // The file is a copy of the whole local database, SyncConfiguration included: a device that joins from it inherits
        // idAliasResendPending, the idAliasResendDone flag and the stored id aliases of this one. Its first sync may then
        // re-send its log once more; that is bounded (once per device) and harmless (peers hold equal entries).
        FileSystem.SYSTEM.copy(dbDef.localDbPath.toPath(), tmpFile)
        // Add-on collection rows and the per-device add-on tables never leave the device (each device builds its
        // collections from its own modules).
        if (dbDef.category.hasAddonRows) stripAddonRowsFromFile(tmpFile)
        net.bible.service.common.ArchiveUtils.gzipFile(tmpFile, gzippedTmpFile)
        FileSystem.SYSTEM.delete(tmpFile)

        Log.i(TAG, "uploading initial db, ${dbDef.categoryName}, ${gzippedTmpFile.size()}")
        val result = adapter.upload(
            name = INITIAL_BACKUP_FILENAME,
            file = gzippedTmpFile,
            parentId = requireSyncFolderId(dbDef)
        )
        dbDef.dao.addStatus(SyncStatus(syncDeviceName, 0, result.size, result.createdTime))
        FileSystem.SYSTEM.delete(gzippedTmpFile)
    }

    private suspend fun fetchAndRestoreInitial(
        dbDef: SyncableDatabaseAccessor<*>,
        replacement: AiDatabaseLifetime.Replacement? = null,
        failWithoutMessage: Boolean = false,
    ) {
        val syncFolderId = requireSyncFolderId(dbDef)
        val deviceFolderId = requireDeviceFolderId(dbDef)
        val adapterConfigs = adapter.getConfigs(dbDef)
        val initialFile = adapter
            .listFiles(
                parentsIds = listOf(requireSyncFolderId(dbDef)),
                name = INITIAL_BACKUP_FILENAME
            )
            .first()
        val gzippedTmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        adapter.download(initialFile.id, FileSystem.SYSTEM.sink(gzippedTmpFile))
        Log.i(TAG, "Downloaded initial db for ${dbDef.categoryName}, ${gzippedTmpFile.size()}")
        val tmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
        net.bible.service.common.ArchiveUtils.gunzipFile(gzippedTmpFile, tmpFile)
        FileSystem.SYSTEM.delete(gzippedTmpFile)
        val initialDbVersion = openSqlite(tmpFile.toString()).use { it.queryLong("PRAGMA user_version")!!.toInt() }
        // Defensive: a cloud initial file never holds add-on rows; this device's own come back by reconciliation.
        if (dbDef.category.hasAddonRows && initialDbVersion <= dbDef.version()) stripAddonRowsFromFile(tmpFile)
        val localVersion = dbDef.version()
        if(initialDbVersion > localVersion) {
            runCatching { FileSystem.SYSTEM.delete(tmpFile) }
            // A merge holds the database replace: it shows the message itself once the replace has ended.
            if (failWithoutMessage) throw IncompatiblePatchVersion()
            dialogs.await(tooOldMessage(dbDef))
            dbDef.category.setSyncEnabled(settings, false)
            Log.e(TAG, "Initial db version is newer than this app version: $initialDbVersion > $localVersion")
            throw CancelStartedSync()
        } else {
            swapInInitialDb(dbDef, tmpFile, replacement) {
                dbDef.dao.setConfig(SYNC_FOLDER_FILE_ID_KEY, syncFolderId)
                dbDef.dao.setConfig(SYNC_DEVICE_FOLDER_FILE_ID_KEY, deviceFolderId)
                dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, Clock.System.now().toEpochMilliseconds())
                dbDef.dao.setConfig(adapterConfigs)
                dropTriggers(dbDef)
                createTriggers(dbDef)
                dbDef.dao.addStatus(
                    SyncStatus(
                        syncDeviceName,
                        0,
                        initialFile.size,
                        initialFile.createdTime
                    )
                )
            }
        }
    }

    /**
     * The initial-download swap: close, copy the downloaded file over the local one, reopen. A [downloaded] file that is
     * not an intact database ([requireIntactDatabase]) is refused with [net.bible.service.db.DamagedDatabaseFileException]
     * before the local database is touched (#4112: damage in a download must never replace the local data).
     */
    @VisibleForTesting suspend fun swapInInitialDb(
        dbDef: SyncableDatabaseAccessor<*>,
        downloaded: Path,
        replacement: AiDatabaseLifetime.Replacement? = null,
        afterReset: suspend () -> Unit,
    ) {
        try {
            withContext(Dispatchers.IO) { requireIntactDatabase(downloaded.toString()) }
        } catch (e: Exception) {
            // The file is left where it is: for a merge's restore it is the durable pristine copy.
            Log.e(TAG, "Refusing to swap in a damaged ${dbDef.categoryName} database; the local one is kept", e)
            throw e
        }
        try {
            // F113: the same race class as a backup restore. A save between the close and the live
            // repository's reload would write the pre-download windows over the downloaded file, and the
            // freshly created triggers would then log it for upload. The epoch bump freezes saving until
            // [workspaceRefreshRequired] -> `loadFromDb`.
            DatabaseContainer.replacingDatabases(replacement) { token ->
                currentCoroutineContext().ensureActive()
                // Once the live file is touched, reopening and sync-state initialization must finish too.
                withContext(NonCancellable) {
                    // This device's own add-on state (favourites, StudyPad expand state) is not in the file: carry it.
                    val addonState = readAddonLocalState(dbDef)
                    dbDef.localDb.close()
                    FileSystem.SYSTEM.delete(dbDef.localDbPath.toPath(), mustExist = false) // replace the file, not rewrite it in place
                    FileSystem.SYSTEM.copy(downloaded, dbDef.localDbPath.toPath())
                    runCatching { FileSystem.SYSTEM.delete(downloaded) } // best-effort: must not leave the local db closed
                    dbDef.resetLocalDbOwned(token)
                    restoreAddonLocalState(dbDef, addonState)
                    afterReset()
                }
            }
        } finally {
            // Categories swap concurrently (`asyncMap`), and a restore may hold its own replace around a
            // sync: release once, when the last replace has ended. Also posts when the swap throws: the
            // epoch is already bumped, so the repository must reload to end the save freeze.
            if (!DatabaseContainer.replacing) notifyWorkspaceRefreshRequired()
        }
    }

    private fun stripAddonRowsFromFile(file: Path) {
        openSqlite(file.toString()).use { conn ->
            stripAddonRows(conn, SyncableDatabaseDefinition.BOOKMARKS)
            conn.exec("VACUUM")
        }
    }

    /** Test seam: called with "afterPatches" and "afterMerge" during a merge; a throw there is a failed merge. */
    @VisibleForTesting
    var mergeStageHookForTest: (suspend (stage: String) -> Unit)? = null

    /**
     * The "Merge data" join: the fetch flow with the device's own data kept and replayed on top of the cloud's
     * (spec 2026-10-24-sync-merge-data-design, section 4.1).
     *
     * Everything runs inside one [DatabaseContainer.replacingDatabases] block, so no other replace can interleave and
     * workspace saves are frozen. Concurrent writers are NOT excluded for bookmark and reading-plan DAO writes (see the
     * trade-off below and [mergeSnapshotIntoLocal]): an own edit during the merge may be counted as a winner and
     * uploaded, which is harmless. Inside the block:
     * 1. a pristine copy of the local database is written next to it (`<db>.merge-original`, durable, see
     *    [recoverInterruptedMerge]) and the merge snapshot (every row logged) to the temp dir;
     * 2. [createDeviceFolder] and the fetch swap-in of the cloud's initial file ([fetchAndRestoreInitial]);
     * 3. every cloud patch is applied ([downloadAndApplyNewPatches]);
     * 4. [mergeSnapshotIntoLocal] with a merge time that is strictly above `LAST_PATCH_WRITTEN_KEY` (the fetch stored
     *    "now"; the larger of now and that value plus one is used, so a fast merge in the same millisecond still
     *    sits above it and the next patch carries the device's winning rows).
     * The snapshot is deleted at the end, also on failure; the durable copy after success and after every restore.
     *
     * **Trade-off (accepted).** The single replace is held through the network I/O (the initial file and the patches
     * are applied to the swapped-in database, so they cannot be fetched outside it). Meanwhile workspace saves are
     * frozen and edits made in that time are discarded by the reload; other database work (the AI store, other
     * categories' swaps, backups) waits; non-AI DAO writes (bookmarks, reading plans) are not blocked and are lost,
     * as in FETCH. The spec (step 10) accepted blocking for the swap only; this is a longer window. Possible
     * follow-up: download first, hold the replace only for swap and merge.
     *
     * **Before the pristine copy is complete** (VACUUM or the copy failed or was cancelled; its size must equal the
     * source's) the live database is untouched and is NOT replaced: only the sync state written so far is removed.
     * The adapter-config removal deletes all of the adapter's keys for the category, which is exact because this path
     * only runs when no sync folder was known.
     *
     * **Failure** (any [Throwable] in steps 1-4, [CancelStartedSync] and cancellation included): the pristine copy is
     * swapped back in through [swapInInitialDb], and in the same reset the stored folder ids, `LAST_SYNCHRONIZED_KEY`,
     * `LAST_PATCH_WRITTEN_KEY` and the adapter's own keys (`adapter.getConfigs`) are removed, so no sync state survives
     * and the device keeps exactly its earlier rows, change log and configuration. Category sync is switched off. The
     * device folder created on the server may remain (an empty folder, or with the secret file); it is harmless and
     * is reused or ignored by the next join. After the replace has ended the failure is reported: a newer schema
     * (the cloud's initial file or a cloud patch) shows the "update the app" message and ends in
     * [CancelStartedSync]; a network failure shows the sync error and ends in [CancelStartedSync]; other
     * exceptions propagate to [synchronize]'s own handling.
     */
    private suspend fun mergeIntoCloudFolder(dbDef: SyncableDatabaseAccessor<*>, createDeviceFolder: suspend () -> Unit, backgroundErrors: Boolean) {
        // The pristine copy is DURABLE, next to the database (the temp dir is wiped at every app start, and a process
        // death after the swap-in would otherwise lose the device's data). It is written as `.partial` and renamed
        // once complete, so `.merge-original` exists only if it is whole; see [recoverInterruptedMerge].
        val original = mergeOriginalPath(dbDef)
        val partial = "$original.partial".toPath()
        val snapshot = createTempFile(env.tmpDir.toPath(), "andbible-merge-snapshot-", ".tmp")
        var restoreFailed = false
        try {
            DatabaseContainer.replacingDatabases { token ->
                var pristineReady = false
                try {
                    mergeStageHookForTest?.invoke("beforePristine")
                    dbDef.localDb.useWriterConnectionMarked { it.exec("VACUUM;") }
                    mergeStageHookForTest?.invoke("afterVacuum")
                    FileSystem.SYSTEM.delete(partial, mustExist = false)
                    FileSystem.SYSTEM.copy(dbDef.localDbPath.toPath(), partial)
                    check(partial.size() == dbDef.localDbPath.toPath().size()) { "The pristine copy of ${dbDef.categoryName} is incomplete" }
                    FileSystem.SYSTEM.atomicMove(partial, original)
                    pristineReady = true
                    mergeStageHookForTest?.invoke("afterPristine")
                    prepareMergeSnapshot(dbDef, snapshot, source = original)
                    createDeviceFolder()
                    fetchAndRestoreInitial(dbDef, token, failWithoutMessage = true)
                    try {
                        downloadAndApplyNewPatches(dbDef)
                    } catch (e: PatchFilesSkipped) {
                        Log.i(TAG, "Patch files skipped during merge! Retrying download and apply!")
                        dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, 0)
                        downloadAndApplyNewPatches(dbDef)
                    }
                    mergeStageHookForTest?.invoke("afterPatches")
                    val watermark = dbDef.dao.getLong(LAST_PATCH_WRITTEN_KEY) ?: 0L
                    val mergeTime = maxOf(Clock.System.now().toEpochMilliseconds(), watermark + 1)
                    mergeSnapshotIntoLocal(dbDef, env, snapshot, mergeTime)
                    mergeStageHookForTest?.invoke("afterMerge")
                } catch (t: Throwable) {
                    Log.e(TAG, "Merge failed for ${dbDef.categoryName}, restoring the device's database", t)
                    try {
                        withContext(NonCancellable) {
                            if (pristineReady) swapInInitialDb(dbDef, original, token) { removeSyncState(dbDef) }
                            else removeSyncState(dbDef) // the live database was never touched: only forget the keys the join wrote
                        }
                    } catch (restoreFailure: Throwable) {
                        restoreFailed = true // keep the durable copy: the next start's recovery retries
                        t.addSuppressed(restoreFailure)
                    }
                    dbDef.category.setSyncEnabled(settings, false)
                    throw t
                }
            }
        } catch (e: IncompatiblePatchVersion) {
            dialogs.await(tooOldMessage(dbDef))
            throw CancelStartedSync()
        } catch (e: IOException) {
            logSyncFailure(dbDef, "merge", e, "probably network down")
            reportSyncError(strings.syncError, backgroundErrors, e)
            throw CancelStartedSync()
        } finally {
            // On success, and after every restore, the durable copy is no longer needed.
            runCatching { FileSystem.SYSTEM.delete(partial, mustExist = false) }
            if (!restoreFailed) runCatching { FileSystem.SYSTEM.delete(original, mustExist = false) }
            runCatching { FileSystem.SYSTEM.delete(snapshot) }
            if (!DatabaseContainer.replacing) notifyWorkspaceRefreshRequired()
        }
    }

    private fun mergeOriginalPath(dbDef: SyncableDatabaseAccessor<*>): Path = "${dbDef.localDbPath}.merge-original".toPath()

    /** Removes every sync key a join writes (folder ids, watermarks, the adapter's keys); exact because a join runs only when no folder is known. */
    private suspend fun removeSyncState(dbDef: SyncableDatabaseAccessor<*>) {
        for (key in listOf(SYNC_FOLDER_FILE_ID_KEY, SYNC_DEVICE_FOLDER_FILE_ID_KEY, LAST_SYNCHRONIZED_KEY, LAST_PATCH_WRITTEN_KEY)) {
            dbDef.dao.removeConfig(key)
        }
        adapter.getConfigs(dbDef).forEach { dbDef.dao.removeConfig(it.keyName) }
    }

    private fun hasInterruptedMerge(dbDef: SyncableDatabaseAccessor<*>): Boolean {
        val original = mergeOriginalPath(dbDef)
        return FileSystem.SYSTEM.exists(original) || FileSystem.SYSTEM.exists("$original.partial".toPath())
    }

    /**
     * Finishes a merge that a process death (or a failed restore) interrupted, before anything else touches the
     * category. `<db>.merge-original` exists only once the pristine copy is whole: the live database may then be the
     * cloud's data with stored folder ids, so the copy is swapped back in, the sync state removed, category sync
     * switched off and the sync error shown; the device has exactly its earlier data. A lone `.partial` means the
     * live database was never touched: it is deleted and only the stale sync state removed. Both end in
     * [CancelStartedSync]; the user can enable sync again. Runs inside [executeJoin], so a signed-in adapter is
     * needed first: until then the database stays as the interrupted merge left it.
     */
    private suspend fun recoverInterruptedMerge(dbDef: SyncableDatabaseAccessor<*>, background: Boolean) {
        val original = mergeOriginalPath(dbDef)
        val partial = "$original.partial".toPath()
        val hasOriginal = FileSystem.SYSTEM.exists(original)
        if (!hasOriginal && !FileSystem.SYSTEM.exists(partial)) return
        Log.e(TAG, "Recovering an interrupted merge of ${dbDef.categoryName} (original copy present: $hasOriginal)")
        try {
            if (hasOriginal) {
                try {
                    DatabaseContainer.replacingDatabases { token -> swapInInitialDb(dbDef, original, token) { removeSyncState(dbDef) } }
                } finally {
                    if (!DatabaseContainer.replacing) notifyWorkspaceRefreshRequired()
                }
            } else {
                removeSyncState(dbDef)
            }
        } finally {
            runCatching { FileSystem.SYSTEM.delete(partial, mustExist = false) }
        }
        dbDef.category.setSyncEnabled(settings, false)
        reportSyncError(strings.syncError, background, null)
        throw CancelStartedSync()
    }

    private val syncMutex = Mutex()
    private val startLock = SynchronizedObject()

    fun start(userInitiated: Boolean = false): kotlinx.coroutines.Deferred<Boolean> = synchronized(startLock) { // L1-common-ok: atomicfu lock
        if (!adapterAvailable) return@synchronized kotlinx.coroutines.CompletableDeferred(false)
        syncStarted?.takeUnless { it.isCompleted }?.let { return@synchronized it }
        (if (userInitiated) scheduler.startUserInitiatedDatabaseSync() else scheduler.startDatabaseSync()).also { syncStarted = it }
    }

    @kotlin.concurrent.Volatile private var syncStarted: kotlinx.coroutines.Deferred<Boolean>? = null

    suspend fun waitUntilFinished(fromService: Boolean = false) {
        if(!fromService) {
            syncStarted?.await()
        }
        syncMutex.withLock {  }
    }

    /** Drops the adapter so the next foreground sync signs in again (and can prompt), keeping all sync state. */
    fun dropAdapterForReconnect() { _adapter = null }

    /** Coalesces this object's sync errors: one per pass, quiet repeats (#4031). */
    private val errorReporter = SyncErrorReporter()

    /** The pass [synchronize] is running, so the per-category join steps report into its single error; null outside a pass. */
    @kotlin.concurrent.Volatile private var passRun: SyncErrorReporter.Run? = null

    /** Reports a sync error into the running pass, or directly when no pass runs (a direct [initializeSync]). */
    private fun reportSyncError(text: String, background: Boolean, cause: Throwable?) {
        passRun?.report(text, background = background, cause = cause)
            ?: UserMessages.errorNotification(text, background = background, kind = ActivityKind.SYNC, cause = cause)
    }

    /**
     * Every failed sync step leaves one ERROR line naming its category and step, with the exception, so the log a bug
     * report attaches always says what failed and why (#4031), also when the pass showed no message for it.
     */
    private fun logSyncFailure(dbDef: SyncableDatabaseAccessor<*>, step: String, e: Throwable, note: String? = null) {
        Log.e(TAG, "Sync failed: category=${dbDef.categoryName} step=$step${note?.let { " ($it)" } ?: ""}: ${e::class.simpleName}: ${e.message}", e)
    }

    /**
     * Interaction controls initialization prompts independently of error presentation.
     * [includeDocuments] false skips the document step (the desktop exit pass syncs databases only,
     * because a first document sync can be long).
     */
    suspend fun synchronize(
        allowInteraction: Boolean = true,
        backgroundErrors: Boolean = !allowInteraction,
        reportTransportErrors: Boolean = allowInteraction,
        includeDocuments: Boolean = true,
    ) = withContext(Dispatchers.IO) {
        if (!adapterAvailable) return@withContext
        documentSettings.ensureLoaded()
        if(syncMutex.isLocked) {
            Log.i(TAG, "Already synchronizing")
            return@withContext
        }
        syncMutex.withLock {
            if(!signedIn) {
                Log.i(TAG, "Not signed in")
                return@withLock
            }
            Log.i(TAG, "Synchronizing starts")
            val timerStart = Clock.System.now().toEpochMilliseconds()

            val needsReconnect = atomic(false)
            val background = backgroundErrors
            // One error per pass (#4031); an automatic pass that fails like the previous one is recorded quietly.
            val run = errorReporter.beginRun(quietRepeats = !allowInteraction)
            fun reportTransportFailure(e: Exception) {
                if (reportTransportErrors) run.report(strings.syncError, background = background, cause = e)
                else run.noteSilentFailure()
            }
            fun reportUserFacing(e: CloudSyncUserFacingException) {
                if (e.requiresReconnect) needsReconnect.value = true
                run.report(e.message?.takeIf { it.isNotBlank() } ?: strings.syncError, showReportButton = false, background = background, cause = e)
            }
            passRun = run
            var completed = false
            try {
                /** Runs an init-phase step of [dbDef] with the per-category error handling; false when the category must be skipped. */
                suspend fun guardInit(dbDef: SyncableDatabaseAccessor<*>, step: String, block: suspend () -> Unit): Boolean {
                    try {
                        block()
                        return true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: CancelStartedSync) {
                        Log.e(TAG, "Sync cancelled ${dbDef.categoryName}")
                    } catch (e: IOException) {
                        logSyncFailure(dbDef, step, e, "probably network down")
                        reportTransportFailure(e)
                    } catch (e: CloudSyncUserFacingException) {
                        logSyncFailure(dbDef, step, e)
                        reportUserFacing(e)
                    } catch (e: Exception) {
                        logSyncFailure(dbDef, step, e)
                        run.report(strings.syncError, background = background, cause = e)
                    }
                    return false
                }

                // Init phase: probe every enabled category (those whose plan dialog is open are left to planJoins), take the
                // decisions already stored by planJoins, and ask the rest once.
                val probed = DatabaseContainer.databaseAccessorFactories.asyncMap {
                    val dbDef = it.invoke()
                    if(!dbDef.category.isSyncEnabled(settings)) { takeStoredJoin(dbDef.category); return@asyncMap null }
                    if(dbDef.dao.getLong("disabledForVersion") == dbDef.version().toLong()) { takeStoredJoin(dbDef.category); return@asyncMap null }
                    if (isAwaitingJoin(dbDef.category)) return@asyncMap null
                    var probe: JoinProbe? = null
                    // A confirmed decision needs the cloud folder id, so its category is listed even in a background pass.
                    val listCloud = allowInteraction || peekStoredJoin(dbDef.category) != null
                    if (!guardInit(dbDef, "initialize") { probe = probeJoin(dbDef, listCloud) }) {
                        // A failed probe cannot confirm what the decision was made against: it must not linger and run later.
                        takeStoredJoin(dbDef.category)
                        return@asyncMap null
                    }
                    dbDef to probe!!
                }.filterNotNull()

                // category -> the operation to run (null: the user left it off)
                val operations = HashMap<SyncableDatabaseDefinition, InitialOperation?>()
                val skipped = HashSet<SyncableDatabaseDefinition>()
                val usedStored = HashMap<SyncableDatabaseDefinition, Any>() // the stored decisions the operations came from
                val toAsk = ArrayList<JoinProbe>()
                for ((dbDef, probe) in probed) {
                    val category = dbDef.category
                    when (probe.state) {
                        JoinState.Ready, JoinState.NeedsRecovery -> takeStoredJoin(category)
                        is JoinState.NeedsDecision, JoinState.NeedsCreate -> {
                            val stored = peekStoredJoin(category)
                            if (stored != null && !probe.cloudListed) {
                                // A placeholder says nothing about the cloud: the confirmed decision waits for a pass that lists it.
                                skipped += category
                                continue
                            }
                            // A decision only holds for the cloud it was made against: a create never replaces a folder another
                            // device made since, and a fetch/merge/create answer is bound to the folder the user was shown.
                            val state = probe.state
                            val usable0 = stored != null && (state is JoinState.NeedsCreate ||
                                (stored.fromDecision && state is JoinState.NeedsDecision && stored.folderId == state.preliminaryFolderId))
                            // A create answer holds when the folder is missing; a fetch/merge bound to folder X does not.
                            val createOk = state !is JoinState.NeedsCreate || stored == null || !stored.fromDecision || stored.folderId == null
                            val usable = usable0 && createOk
                            if (usable) { operations[category] = stored!!.operation; usedStored[category] = stored }
                            else if (allowInteraction) toAsk += probe
                            else {
                                // An OS refresh must not decide whether to fetch, create, or disable a category.
                                guardInit(dbDef, "initialize") { applyProbeCleanups(dbDef, probe); throw CancelStartedSync() }
                                skipped += category
                            }
                            if (stored != null && !usable) takeStoredJoin(category)
                        }
                    }
                }
                // A plan dialog opened meanwhile (planJoins) owns its categories: never ask twice.
                toAsk.removeAll { isAwaitingJoin(it.category) }
                if (toAsk.isNotEmpty()) {
                    val plan = try {
                        askJoinPlan(toAsk)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Join plan failed", e)
                        run.report(strings.syncError, background = background, cause = e)
                        toAsk.forEach { skipped += it.category }
                        null
                    }
                    if (plan == null) {
                        // Cancel: the categories being joined go off, the Ready ones keep syncing.
                        toAsk.forEach { if (it.category !in skipped) { disableJoining(listOf(it.category)); skipped += it.category } }
                    } else operations.putAll(plan)
                }

                probed.asyncMap { (dbDef, probe) ->
                    val category = dbDef.category
                    if (category in skipped || isAwaitingJoin(category)) return@asyncMap
                    val operation = operations[category]
                    if (probe.state is JoinState.NeedsDecision || probe.state is JoinState.NeedsCreate) {
                        // Non-ready and not decided (nothing in `operations`): the category was left out of the plan.
                        if (!operations.containsKey(category)) return@asyncMap
                    }
                    // Re-check what the init phase decided: the dialog above may have taken a while, and an Apply meanwhile may
                    // have turned the category off or replaced/dropped its decision.
                    val fromStored = usedStored[category]
                    // Compare-and-remove: a newer decision stored meanwhile by another Apply is not ours to take.
                    val taken = if (fromStored != null) takeStoredJoinIfSame(category, fromStored) else takeStoredJoin(category)
                    if (!category.isSyncEnabled(settings)) return@asyncMap
                    if (fromStored != null && taken !== fromStored) return@asyncMap
                    if (!guardInit(dbDef, "initialize") { executeJoin(dbDef, probe, operation, backgroundErrors) }) return@asyncMap
                    try {
                        createAndUploadNewPatch(dbDef)
                    } catch (e: IOException) {
                        logSyncFailure(dbDef, "upload", e, "probably network down")
                        reportTransportFailure(e)
                        return@asyncMap
                    } catch (e: MissingSyncFolderIds) {
                        Log.w(TAG, "Skipping upload, will rejoin on the next sync", e)
                        return@asyncMap
                    } catch (e: CloudSyncUserFacingException) {
                        logSyncFailure(dbDef, "upload", e)
                        reportUserFacing(e)
                        return@asyncMap
                    } catch (e: Exception) {
                        logSyncFailure(dbDef, "upload", e)
                        run.report(strings.syncError, background = background, cause = e)
                    }
                    try {
                        try {
                            downloadAndApplyNewPatches(dbDef)
                        } catch (e: PatchFilesSkipped) {
                            Log.i(TAG, "Patch files skipped! Retrying download and apply!")
                            dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, 0)
                            downloadAndApplyNewPatches(dbDef)
                        }
                    } catch (e: IOException) {
                        logSyncFailure(dbDef, "download", e, "probably network down")
                        reportTransportFailure(e)
                    } catch (e: MissingSyncFolderIds) {
                        Log.w(TAG, "Skipping download, will rejoin on the next sync", e)
                        return@asyncMap
                    } catch (e: IncompatiblePatchVersion) {
                        logSyncFailure(dbDef, "download", e, "patch from a newer app version; category disabled for this version")
                        run.report("${strings.syncTooOldLead(strings.syncCategoryContents(dbDef.category.name))} ${strings.syncTooOldDetail}", showReportButton = false, background = background, cause = e)
                        dbDef.dao.setConfig("disabledForVersion", dbDef.version().toLong())
                        return@asyncMap
                    } catch (e: CloudSyncUserFacingException) {
                        logSyncFailure(dbDef, "download", e)
                        reportUserFacing(e)
                        return@asyncMap
                    } catch (e: Exception) {
                        logSyncFailure(dbDef, "download", e)
                        run.report(strings.syncError, background = background, cause = e)
                    }
                }
                if (includeDocuments) try {
                    documents.get().runSync(
                        download = documentSettings.autoDownload,
                        upload = documentSettings.autoUpload,
                        delete = documentSettings.autoDelete,
                        manual = false,
                    )
                } catch (e: CloudSyncUserFacingException) {
                    Log.e(TAG, "Sync failed: documents (plan)", e)
                    reportUserFacing(e)
                } catch (e: IOException) {
                    Log.e(TAG, "Sync failed: documents (plan), probably network down", e)
                    reportTransportFailure(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed: documents (plan)", e)
                    run.noteSilentFailure()
                }
                completed = true
            } finally {
                passRun = null
                // The pass's one error is shown now; only a pass that ran to its end moves the failing streak.
                run.end(completed)
                if (needsReconnect.value) dropAdapterForReconnect()
            }
            Log.i(TAG, "Synchronization complete in ${(Clock.System.now().toEpochMilliseconds() - timerStart)/1000.0} seconds.")
        }
    }

    private fun tooOldMessage(dbDef: SyncableDatabaseAccessor<*>) = AppDialogRequest.Message(
        title = null, message = strings.syncTooOldLead(strings.syncCategoryContents(dbDef.category.name)),
        confirmText = strings.okay, cancellable = false, detail = strings.syncTooOldDetail,
    )

    private val patchFilePattern = Regex("""(\d+)\.((\d+)\.)?sqlite3\.gz""")
    private fun patchNumber(name: String): Long = patchFilePattern.find(name)!!.groups[1]!!.value.toLong()
    private fun versionNumber(name: String): Long = patchFilePattern.find(name)?.groups?.get(3)?.value?.toLong() ?: 1

    class PatchFilesSkipped: Exception()
    class IncompatiblePatchVersion: Exception()
    private suspend fun downloadAndApplyNewPatches(dbDef: SyncableDatabaseAccessor<*>) = withContext(Dispatchers.IO) {
        val lastSynchronized = dbDef.dao.getLong(LAST_SYNCHRONIZED_KEY)?: 0
        val syncFolder = requireSyncFolderId(dbDef)
        val deviceSyncFolder = requireDeviceFolderId(dbDef)

        Log.i(TAG, "Downloading new patches ${dbDef.categoryName}, last Synced: $lastSynchronized. ")
        Log.i(TAG, "This device $syncDeviceName (id: ${deviceSyncFolder})")

        // Only commit this watermark after processing the listing, so failures leave patches visible on retry.
        val synchronizedAt = Clock.System.now().toEpochMilliseconds()

        val devicePatchFolders = adapter.getFolders(syncFolder)

        if (devicePatchFolders.isEmpty()) {
            Log.i(TAG, "No patch folders yet")
            dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, synchronizedAt)
            return@withContext
        }
        Log.i(TAG, "Folders \n${devicePatchFolders.joinToString("\n") { "${it.id} ${it.name}" }}")

        val patchResults = adapter.listFiles(
            parentsIds = devicePatchFolders.map { it.id },
            createdTimeAtLeast = lastSynchronized
        ).sortedBy { it.createdTime }

        Log.i(TAG, "Number of patch files in result set: ${patchResults.size}")

        class FolderWithMeta(val folder: CloudFile, val loadedCount: Long)

        val folders = devicePatchFolders
            .map {
                FolderWithMeta(it, dbDef.dao.lastPatchNum(it.name) ?: 0)
            }.associateBy { it.folder.id }
        Log.i(TAG, "Folder counts: \n${folders.values.joinToString("\n") { "${it.folder.name}: ${it.loadedCount}" }}")
        Log.i(TAG, "Patches before filter: \n${patchResults.joinToString("\n") { it.name }}")

        class DriveFileWithMeta(val file: CloudFile, val parentFolderName: String)

        val localVersion = dbDef.version()
        val patches = patchResults.mapNotNull {
            val parentFolderId = it.parentId
            val folderWithMeta = folders[parentFolderId]!!
            val num = patchNumber(it.name)
            if(versionNumber(it.name) > localVersion) {
                throw IncompatiblePatchVersion()
            }
            val existing = dbDef.dao.syncStatus(folderWithMeta.folder.name, patchNumber(it.name))
            if (existing == null && num > folderWithMeta.loadedCount) {
                DriveFileWithMeta(it, folderWithMeta.folder.name)
            } else null
        }.sortedBy { it.file.createdTime }

        if(patches.isEmpty()) {
            Log.i(TAG, "No patches, returning")
            dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, synchronizedAt)
            return@withContext
        }

        for(f in folders.values) {
            val firstPatch = patches.firstOrNull { it.parentFolderName == f.folder.name } ?: continue
            if(patchNumber(firstPatch.file.name) > f.loadedCount + 1) {
                // We are not in sync; need to load older patches.
                throw PatchFilesSkipped()
            }
        }

        Log.i(TAG, "Patches after filter: \n${patches.joinToString("\n") { "${it.file.name} ${it.parentFolderName}" }}")
        Log.i(TAG, "Number of patch files after filtering: ${patches.size}")

        val downloadedFiles = patches.asyncMap(6) { f ->
            Log.i(TAG, "Downloading ${f.file.name}, ${f.file.size} bytes")
            val tmpFile = createTempFile(env.tmpDir.toPath(), "andbible-", ".tmp")
            adapter.download(f.file.id, FileSystem.SYSTEM.sink(tmpFile))
            tmpFile
        }

        val syncStatuses = patches.map {
            SyncStatus(it.parentFolderName, patchNumber(it.file.name), it.file.size, it.file.createdTime)
        }

        // Reported once for all patches, also those committed before a failing one: the retry re-applies them as no-ops
        // and would never report them (#4151). For the same reason a listener's failure must not fail the sync.
        applyPatchesForDatabase(dbDef, env, *downloadedFiles.toTypedArray(), onApplied = {
            try { dbDef.reactToUpdates(it) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Reacting to synced ${dbDef.categoryName} changes failed", e) }
        })
        downloadedFiles.forEach { runCatching { FileSystem.SYSTEM.delete(it) } }
        dbDef.dao.addStatuses(syncStatuses)
        dbDef.dao.setConfig(LAST_SYNCHRONIZED_KEY, synchronizedAt)
    }
    private suspend fun createAndUploadNewPatch(dbDef: SyncableDatabaseAccessor<*>) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Uploading new patches ${dbDef.categoryName}")
        startIdAliasResendOnce(dbDef)
        val file = createPatchForDatabase(dbDef, env, false)?: return@withContext
        val syncDeviceFolderId = requireDeviceFolderId(dbDef)
        val count = (dbDef.dao.lastPatchNum(syncDeviceName)?: 0) + 1
        val fileName = "$count.${dbDef.version()}.sqlite3.gz"
        val lastWritten = Clock.System.now().toEpochMilliseconds();

        val result = try {
            adapter.upload(fileName, file, syncDeviceFolderId)
        } catch (e: Exception) {
            Log.e(TAG, "Uploading failed due to error", e)
            runCatching { FileSystem.SYSTEM.delete(file) }
            throw e
        }
        Log.i(TAG, "Uploaded ${dbDef.categoryName} $fileName, ${file.size()} bytes, ${result.createdTime}")
        dbDef.dao.setConfig(LAST_PATCH_WRITTEN_KEY, lastWritten)
        dbDef.dao.addStatus(SyncStatus(syncDeviceName, count, file.size(), result.createdTime))
        runCatching { FileSystem.SYSTEM.delete(file) }
    }

    suspend fun hasChanges(): Boolean =
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.category.isSyncEnabled(settings) && dbDef.hasChanges()
        }.any { it }

    suspend fun bytesUsed(): Long =
        DatabaseContainer.databaseAccessorFactories.asyncMap {
            val dbDef = it.invoke()
            dbDef.bytesUsed()
        }.sum() + documents.get().cloudBytesUsed()

}

/** Size in bytes; 0 when the file is absent (as `File.length()`). */
private fun Path.size(): Long = FileSystem.SYSTEM.metadataOrNull(this)?.size ?: 0L

/** 8 hex chars of the 32-bit FNV-1a hash of the UTF-8 bytes of [text]: identical on every platform. */
internal fun shortStableHash(text: String): String {
    var h = 0x811c9dc5.toInt()
    for (b in text.encodeToByteArray()) {
        h = (h xor (b.toInt() and 0xff)) * 0x01000193
    }
    return h.toUInt().toString(16).padStart(8, '0')
}
