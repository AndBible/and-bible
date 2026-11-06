package net.bible.service.cloudsync

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.log.Log

interface CloudSyncPort {
    val signedIn: Boolean
    suspend fun signIn(): Boolean?
    fun start(): Deferred<Boolean>
    suspend fun waitUntilFinished()
    val runningChanged: Events<Boolean>

    companion object {
        fun of(cloudSync: CloudSync): CloudSyncPort = object : CloudSyncPort {
            override val signedIn get() = cloudSync.signedIn
            override suspend fun signIn() = cloudSync.signIn()
            override fun start() = cloudSync.start()
            override suspend fun waitUntilFinished() = cloudSync.waitUntilFinished()
            override val runningChanged get() = cloudSync.runningChanged
        }
    }
}

class SyncNowPorts(
    val saveWorkspace: suspend () -> Unit,
    val networkAvailable: () -> Boolean,
    val noNetwork: () -> Unit,
)

class SyncNow(
    private val cloudSync: CloudSyncPort,
    documentRunning: Events<Boolean>,
    private val syncSetUp: () -> Boolean,
    private val saveWorkspace: suspend () -> Unit,
    private val networkAvailable: () -> Boolean,
    private val replacing: () -> Boolean,
    private val noNetwork: () -> Unit,
    private val scope: CoroutineScope,
    private val cloudRunningState: StateFlow<Boolean>? = null,
    private val documentRunningState: StateFlow<Boolean>? = null,
) {
    private val lock = SynchronizedObject()
    private val subscriptions = Subscriptions()
    private var cloudRunning = false
    private var documentsRunning = false
    private var starting = false
    private val mutableRunning = MutableStateFlow(cloudRunningState?.value == true || documentRunningState?.value == true)
    private val stateJobs = mutableListOf<kotlinx.coroutines.Job>()
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    init {
        if (cloudRunningState == null) subscriptions.add(cloudSync.runningChanged.subscribe { value ->
            synchronized(lock) { cloudRunning = value; updateRunning() }
        }) else stateJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            cloudRunningState.collect { synchronized(lock) { updateRunning() } }
        }
        if (documentRunningState == null) subscriptions.add(documentRunning.subscribe { value ->
            synchronized(lock) { documentsRunning = value; updateRunning() }
        }) else stateJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            documentRunningState.collect { synchronized(lock) { updateRunning() } }
        }
    }

    private fun updateRunning() {
        mutableRunning.value = (cloudRunningState?.value ?: cloudRunning) ||
            (documentRunningState?.value ?: documentsRunning) || starting
    }

    fun available(): Boolean = syncSetUp()

    fun start() {
        synchronized(lock) {
            updateRunning()
            if (mutableRunning.value || replacing()) return
            if (!networkAvailable()) { noNetwork(); return }
            starting = true
            updateRunning()
        }
        val job = scope.launch {
            try {
                saveWorkspace()
                if (!cloudSync.signedIn && cloudSync.signIn() != true) return@launch
                cloudSync.start()
                cloudSync.waitUntilFinished()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SyncNow", "Synchronization failed", e)
            }
        }
        job.invokeOnCompletion { synchronized(lock) { starting = false; updateRunning() } }
    }

    fun close() {
        subscriptions.cancelAll()
        stateJobs.forEach { it.cancel() }
    }
}
