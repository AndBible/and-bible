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

package net.bible.service.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.bible.android.database.IdType
import net.bible.service.llm.agent.PermissionMode
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import java.util.Locale

/** Immediate snapshot reads with application-owned FIFO writes and replacement-aware observation. */
class AiSettingsStore(
    private val dao: () -> GlobalAiSettingsDao,
    private val launcher: OrderedLauncher,
    private val appScope: AppCoroutineScope,
    private val env: AppEnvironment,
    private val replacements: Events<Unit> = EventSource(),
) : AiSettings {
    private val state = MutableStateFlow<GlobalAiSettings?>(null)
    private val dbMutex = Mutex()
    private val stateLock = Any()
    private var pendingWrites = 0
    private var observing = false
    private val generation = MutableStateFlow(0L)
    private val defaultModelSource = EventSource<Unit>()
    private val configSource = EventSource<Unit>()
    override val defaultModelChanged: Events<Unit> get() = defaultModelSource
    override val configChanged: Events<Unit> get() = configSource
    override fun notifyConfigChanged() = configSource.emit(Unit)

    override suspend fun ensureLoaded() {
        dbMutex.withLock {
            if (!observing) {
                val subscription = replacements.subscribe { synchronized(stateLock) { generation.value++ } }
                val loaded = try { dao().get() ?: GlobalAiSettings() }
                catch (e: Exception) { subscription.cancel(); throw e }
                synchronized(stateLock) { if (state.value == null) state.value = loaded }
                observing = true
                val job = appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    generation.collectLatest {
                        try {
                            refresh()
                            dao().observe().collect { refresh() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // A closed pre-restore DB may fail before the replacement signal arrives.
                            Log.e("AiSettingsStore", "Observation ended; awaiting database replacement", e)
                        }
                    }
                }
                job.invokeOnCompletion { subscription.cancel() }
            }
        }
    }

    private fun getOrDefault(): GlobalAiSettings = state.value ?: run {
        val message = "AiSettings read before ensureLoaded()"
        if (env.isDebugMode) throw IllegalStateException(message)
        Log.e("AiSettingsStore", message)
        GlobalAiSettings()
    }

    private suspend fun refresh() = dbMutex.withLock {
        // Never publish an emission captured before a newer local setter or queued write.
        // Re-read under the writer mutex: Room can deliver intermediate own emissions late.
        val loaded = dao().get() ?: GlobalAiSettings()
        synchronized(stateLock) { if (pendingWrites == 0) state.value = loaded }
    }

    private fun update(transform: GlobalAiSettings.() -> GlobalAiSettings) {
        synchronized(stateLock) {
            val snapshot = getOrDefault().transform()
            state.value = snapshot
            pendingWrites++
            launcher.launch("ai-settings") {
                dbMutex.withLock {
                    try { dao().set(snapshot) }
                    finally { synchronized(stateLock) { pendingWrites-- } }
                }
            }
        }
    }

    override var agentPermissionMode: PermissionMode
        get() = getOrDefault().agentPermissionMode ?: PermissionMode.ALWAYS_ASK
        set(value) = update { copy(agentPermissionMode = value) }

    override var permanentlyAllowedTools: Set<AgentTool>
        get() = getOrDefault().permanentlyAllowedTools ?: emptySet()
        set(value) = update { copy(permanentlyAllowedTools = value) }

    override var permanentlyDeniedTools: Set<AgentTool>
        get() = getOrDefault().permanentlyDeniedTools ?: emptySet()
        set(value) = update { copy(permanentlyDeniedTools = value) }

    override var aiExcludedDocuments: Set<String>
        get() = getOrDefault().aiExcludedDocuments
        set(value) = update { copy(aiExcludedDocuments = value) }

    override var commentaryMaxResponseTokens: Int
        get() = getOrDefault().commentaryMaxResponseTokens
        set(value) = update { copy(commentaryMaxResponseTokens = value) }

    override var hiddenBuiltInPrompts: Set<IdType>
        get() = getOrDefault().hiddenBuiltInPrompts
        set(value) = update { copy(hiddenBuiltInPrompts = value) }

    override var maxIterations: Int
        get() = getOrDefault().maxIterations
        set(value) = update { copy(maxIterations = value) }

    override var commentaryDeselected: Set<String>
        get() = getOrDefault().commentaryDeselected
        set(value) = update { copy(commentaryDeselected = value) }

    override var defaultModelId: IdType?
        get() = getOrDefault().defaultModelId
        set(value) {
            update { copy(defaultModelId = value) }
            defaultModelSource.emit(Unit)
        }

    override var aiLanguage: String?
        get() = getOrDefault().aiLanguage
        set(value) = update { copy(aiLanguage = value) }

    override var askModelBeforeRun: Boolean
        get() = getOrDefault().askModelBeforeRun
        set(value) = update { copy(askModelBeforeRun = value) }

    override var aiDisclaimerAccepted: Boolean
        get() = getOrDefault().aiDisclaimerAccepted
        set(value) = update { copy(aiDisclaimerAccepted = value) }

    override var hiddenBuiltInCategories: Set<IdType>
        get() = getOrDefault().hiddenBuiltInCategories
        set(value) = update { copy(hiddenBuiltInCategories = value) }

    override var customAgentSystemPrompt: String?
        get() = getOrDefault().customAgentSystemPrompt
        set(value) = update { copy(customAgentSystemPrompt = value) }

    override var customTextTransformationSystemPrompt: String?
        get() = getOrDefault().customTextTransformationSystemPrompt
        set(value) = update { copy(customTextTransformationSystemPrompt = value) }

    override var favoritePrompts: Set<IdType>
        get() = getOrDefault().favoritePrompts
        set(value) = update { copy(favoritePrompts = value) }

    /** Auto-delete raw logs older than this many days. null = no auto-delete. */
    override var rawLogRetentionDays: Int?
        get() = getOrDefault().rawLogRetentionDays
        set(value) = update { copy(rawLogRetentionDays = value) }

    /** When true, auto-hide the agent log panel when a task finishes (unless it errored). */
    override var autoHideAgentLogOnCompletion: Boolean
        get() = getOrDefault().autoHideAgentLogOnCompletion
        set(value) = update { copy(autoHideAgentLogOnCompletion = value) }

    /**
     * Language name for AI prompts (e.g. "suomi", "English", "Tagalog").
     * If aiLanguage is null, returns the app's display language.
     * If aiLanguage is a BCP 47 code (from the preset list), resolves it to a display name.
     * If aiLanguage is a free-form name (from custom input), returns it as-is.
     */
    override val aiDisplayLanguage: String
        get() {
            val tag = aiLanguage ?: return Locale.getDefault().let { it.getDisplayLanguage(it) }
            val locale = Locale.forLanguageTag(tag)
            val displayName = locale.getDisplayLanguage(locale)
            return if (displayName.isNotEmpty() && displayName != tag) displayName else tag
        }
}
