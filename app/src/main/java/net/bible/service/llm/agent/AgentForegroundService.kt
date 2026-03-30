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

package net.bible.service.llm.agent

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.bible.android.AI_AGENT_NOTIFICATION_CHANNEL
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils
import net.bible.service.llm.PromptRepository

private const val TAG = "AgentForegroundService"
private const val NOTIFICATION_ID = 5
private const val WAKELOCK_TAG = "andbible:agent-wakelock"
private const val WAKELOCK_TIMEOUT_MS = 15L * 60 * 1000 // 15 minutes

/**
 * Foreground service that hosts AI agent execution, allowing it to continue
 * when the user leaves the app. Shows an ongoing notification with progress
 * and a cancel button.
 */
class AgentForegroundService : Service() {

    companion object {
        const val START_AGENT = "action_start_agent"
        const val CANCEL_AGENT = "action_cancel_agent"
        const val START_REGENERATE = "action_start_regenerate"

        private val json = Json { ignoreUnknownKeys = true }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startAgent(
            context: Context,
            promptId: IdType,
            selection: Selection,
            workspaceId: IdType,
            userSpecification: String? = null,
            modelOverrideId: IdType? = null
        ) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = START_AGENT
                putExtra("promptId", promptId.toString())
                putExtra("selectionJson", json.encodeToString(Selection.serializer(), selection))
                putExtra("workspaceId", workspaceId.toString())
                userSpecification?.let { putExtra("userSpecification", it) }
                modelOverrideId?.let { putExtra("modelOverrideId", it.toString()) }
            }
            startServiceCompat(context, intent)
        }

        fun startRegenerate(
            context: Context,
            pageId: IdType,
            workspaceId: IdType,
            targetWindowId: IdType? = null,
            additionalInstructions: String? = null,
            keepPrevious: Boolean = false,
            freshRun: Boolean = false,
            modelOverrideId: IdType? = null
        ) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = START_REGENERATE
                putExtra("pageId", pageId.toString())
                putExtra("workspaceId", workspaceId.toString())
                targetWindowId?.let { putExtra("targetWindowId", it.toString()) }
                additionalInstructions?.let { putExtra("additionalInstructions", it) }
                putExtra("keepPrevious", keepPrevious)
                putExtra("freshRun", freshRun)
                modelOverrideId?.let { putExtra("modelOverrideId", it.toString()) }
            }
            startServiceCompat(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                action = CANCEL_AGENT
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val powerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentWorkspaceId: IdType? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ABEventBus.register(this)
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_AGENT -> handleStartAgent(intent)
            START_REGENERATE -> handleStartRegenerate(intent)
            CANCEL_AGENT -> handleCancel()
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelfSafe()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartAgent(intent: Intent) {
        val promptId = IdType(intent.getStringExtra("promptId") ?: run {
            Log.e(TAG, "Missing promptId")
            stopSelfSafe()
            return
        })
        val selectionJson = intent.getStringExtra("selectionJson") ?: run {
            Log.e(TAG, "Missing selectionJson")
            stopSelfSafe()
            return
        }
        val workspaceId = IdType(intent.getStringExtra("workspaceId") ?: run {
            Log.e(TAG, "Missing workspaceId")
            stopSelfSafe()
            return
        })
        val userSpecification = intent.getStringExtra("userSpecification")
        val modelOverrideId = intent.getStringExtra("modelOverrideId")?.let { IdType(it) }

        val selection = try {
            json.decodeFromString(Selection.serializer(), selectionJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize selection", e)
            stopSelfSafe()
            return
        }

        currentWorkspaceId = workspaceId
        startForegroundWithNotification()
        acquireWakeLock()

        scope.launch {
            try {
                val prompt = PromptRepository.promptById(promptId)
                if (prompt == null) {
                    Log.e(TAG, "Prompt not found: $promptId")
                    stopSelfSafe()
                    return@launch
                }
                AgentSessionManager.executePrompt(
                    prompt, selection,
                    userSpecification = userSpecification,
                    modelOverrideId = modelOverrideId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Agent execution failed", e)
            } finally {
                showCompletionNotification()
                stopSelfSafe()
            }
        }.also { job ->
            AgentSessionManager.getOrCreateSession(workspaceId).job = job
        }
    }

    private fun handleStartRegenerate(intent: Intent) {
        val pageId = IdType(intent.getStringExtra("pageId") ?: run {
            stopSelfSafe(); return
        })
        val workspaceId = IdType(intent.getStringExtra("workspaceId") ?: run {
            stopSelfSafe(); return
        })
        val targetWindowId = intent.getStringExtra("targetWindowId")?.let { IdType(it) }
        val additionalInstructions = intent.getStringExtra("additionalInstructions")
        val keepPrevious = intent.getBooleanExtra("keepPrevious", false)
        val freshRun = intent.getBooleanExtra("freshRun", false)
        val modelOverrideId = intent.getStringExtra("modelOverrideId")?.let { IdType(it) }

        currentWorkspaceId = workspaceId
        startForegroundWithNotification()
        acquireWakeLock()

        scope.launch {
            try {
                AgentSessionManager.regenerateAIDocument(
                    pageId,
                    targetWindowId = targetWindowId,
                    additionalInstructions = additionalInstructions,
                    keepPrevious = keepPrevious,
                    freshRun = freshRun,
                    modelOverrideId = modelOverrideId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Regeneration failed", e)
            } finally {
                showCompletionNotification()
                stopSelfSafe()
            }
        }.also { job ->
            AgentSessionManager.getOrCreateSession(workspaceId).job = job
        }
    }

    private fun handleCancel() {
        currentWorkspaceId?.let { AgentSessionManager.stopAgent(it) }
        stopSelfSafe()
    }

    // --- Notification management ---

    private fun notificationChannel(): String =
        if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else AI_AGENT_NOTIFICATION_CHANNEL

    private fun notificationIcon(): Int =
        if (CommonUtils.isDiscrete) R.drawable.ic_calc_24 else R.drawable.ic_ichtys

    private fun buildMainActivityIntent(): PendingIntent {
        val intent = Intent(this, MainBibleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCancelIntent(): PendingIntent {
        val intent = Intent(this, AgentForegroundService::class.java).apply {
            action = CANCEL_AGENT
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, notificationChannel())
            .setSmallIcon(notificationIcon())
            .setContentTitle(getString(R.string.ai_agent_notification_running))
            .setContentIntent(buildMainActivityIntent())
            .addAction(0, getString(R.string.ai_agent_notification_cancel), buildCancelIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateProgressNotification(text: String) {
        val notification = NotificationCompat.Builder(this, notificationChannel())
            .setSmallIcon(notificationIcon())
            .setContentTitle(getString(R.string.ai_agent_notification_running))
            .setContentText(text.take(100))
            .setContentIntent(buildMainActivityIntent())
            .addAction(0, getString(R.string.ai_agent_notification_cancel), buildCancelIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** Channel for permission notifications — needs IMPORTANCE_HIGH for sound. */
    private fun permissionNotificationChannel(): String =
        if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else "generic-notifications"

    private fun showPermissionNeededNotification(toolName: String?) {
        val text = toolName?.let { "$it — ${getString(R.string.ai_agent_notification_permission_tap)}" }
            ?: getString(R.string.ai_agent_notification_permission_tap)

        val notification = NotificationCompat.Builder(this, permissionNotificationChannel())
            .setSmallIcon(notificationIcon())
            .setContentTitle(getString(R.string.ai_agent_notification_permission_needed))
            .setContentText(text)
            .setContentIntent(buildMainActivityIntent())
            .addAction(0, getString(R.string.ai_agent_notification_cancel), buildCancelIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        // Replaces the ongoing progress notification
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun restoreProgressNotification() {
        updateProgressNotification(getString(R.string.ai_agent_notification_running))
    }

    private fun showCompletionNotification() {
        // No notification needed when user is already in the app
        if (CurrentActivityHolder.currentActivity != null) return

        val session = currentWorkspaceId?.let { AgentSessionManager.getSession(it) }
        val hasError = session?.logEntries?.any { it.type == LogEntryType.ERROR } == true

        val title = if (hasError) {
            getString(R.string.ai_agent_notification_error)
        } else {
            getString(R.string.ai_agent_notification_completed)
        }

        val notification = NotificationCompat.Builder(this, permissionNotificationChannel())
            .setSmallIcon(notificationIcon())
            .setContentTitle(title)
            .setContentText(getString(R.string.ai_agent_notification_completed_tap))
            .setContentIntent(buildMainActivityIntent())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // --- WakeLock management ---

    private fun acquireWakeLock() {
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    // --- EventBus subscribers ---

    fun onEvent(event: AgentLogUpdatedEvent) {
        if (event.workspaceId != currentWorkspaceId) return
        updateProgressNotification(event.entry.message)
        // Renew WakeLock on activity (progress means active processing)
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            it.acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    fun onEvent(event: AgentPermissionWaitingEvent) {
        if (event.workspaceId != currentWorkspaceId) return
        if (event.waiting) {
            // Release WakeLock while waiting — agent can sleep
            releaseWakeLock()
            showPermissionNeededNotification(event.toolName)
        } else {
            restoreProgressNotification()
            acquireWakeLock()
        }
    }

    fun onEvent(event: AgentSessionStatusChangedEvent) {
        if (event.workspaceId != currentWorkspaceId) return
        if (!event.isRunning) {
            // Agent finished — stop service
            stopSelfSafe()
        }
    }

    private fun stopSelfSafe() {
        releaseWakeLock()
        // When app is active, remove notification entirely. When backgrounded, detach it
        // so the completion notification (posted by showCompletionNotification) stays visible.
        val removeNotification = CurrentActivityHolder.currentActivity != null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
        // Explicitly cancel via NotificationManager to handle the race where
        // updateProgressNotification's notify() call is processed by the system
        // after stopForeground(STOP_FOREGROUND_REMOVE).
        if (removeNotification) {
            notificationManager.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }
}
