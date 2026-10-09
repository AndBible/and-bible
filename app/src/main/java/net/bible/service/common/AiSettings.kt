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

package net.bible.service.common

import androidx.annotation.VisibleForTesting
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.blockingDb
import net.bible.service.llm.AgentTool
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.agent.PermissionMode
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import java.util.Locale

/**
 * Accessor for global AI settings stored in the syncable AiSettingsDatabase.
 * Each property reads/writes the [GlobalAiSettings] singleton row.
 */
object AiSettings {
    private var defaultModelSource = EventSource<Unit>()

    /** The global default model changed. Subscribers re-read it. */
    val defaultModelChanged: Events<Unit> get() = defaultModelSource

    /** Test teardown: process-global, so leaked subscribers would outlive their test. */
    @VisibleForTesting
    fun resetSubscribersForTest() {
        defaultModelSource = EventSource()
        configSource = EventSource()
    }

    private var configSource = EventSource<Unit>()

    /** LLM providers or models changed (create, delete, configure); subscribers re-read. Replaces the AI family of the retired AppSettingsUpdated bus event. */
    val configChanged: Events<Unit> get() = configSource

    fun notifyConfigChanged() {
        configSource.emit(Unit)
    }

    private val dao get() = DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()

    // Each property access is one bridge (a read, or a read-modify-write in a single bridge); callers are
    // property accessors on arbitrary threads, so they stay blocking until their callers are coroutines.
    private fun getOrDefault(): GlobalAiSettings = dao.let { d -> blockingDb { d.get() ?: GlobalAiSettings() } }

    private fun update(transform: GlobalAiSettings.() -> GlobalAiSettings) {
        val d = dao // resolved before the bridge: first access may construct the container (which bridges itself)
        blockingDb { d.set((d.get() ?: GlobalAiSettings()).transform()) }
    }

    var agentPermissionMode: PermissionMode
        get() = getOrDefault().agentPermissionMode ?: PermissionMode.ALWAYS_ASK
        set(value) = update { copy(agentPermissionMode = value) }

    var permanentlyAllowedTools: Set<AgentTool>
        get() = getOrDefault().permanentlyAllowedTools ?: emptySet()
        set(value) = update { copy(permanentlyAllowedTools = value) }

    var permanentlyDeniedTools: Set<AgentTool>
        get() = getOrDefault().permanentlyDeniedTools ?: emptySet()
        set(value) = update { copy(permanentlyDeniedTools = value) }

    var aiExcludedDocuments: Set<String>
        get() = getOrDefault().aiExcludedDocuments
        set(value) = update { copy(aiExcludedDocuments = value) }

    var commentaryMaxResponseTokens: Int
        get() = getOrDefault().commentaryMaxResponseTokens
        set(value) = update { copy(commentaryMaxResponseTokens = value) }

    var hiddenBuiltInPrompts: Set<IdType>
        get() = getOrDefault().hiddenBuiltInPrompts
        set(value) = update { copy(hiddenBuiltInPrompts = value) }

    var maxIterations: Int
        get() = getOrDefault().maxIterations
        set(value) = update { copy(maxIterations = value) }

    var commentaryDeselected: Set<String>
        get() = getOrDefault().commentaryDeselected
        set(value) = update { copy(commentaryDeselected = value) }

    var defaultModelId: IdType?
        get() = getOrDefault().defaultModelId
        set(value) {
            update { copy(defaultModelId = value) }
            defaultModelSource.emit(Unit)
        }

    var aiLanguage: String?
        get() = getOrDefault().aiLanguage
        set(value) = update { copy(aiLanguage = value) }

    var askModelBeforeRun: Boolean
        get() = getOrDefault().askModelBeforeRun
        set(value) = update { copy(askModelBeforeRun = value) }

    var aiDisclaimerAccepted: Boolean
        get() = getOrDefault().aiDisclaimerAccepted
        set(value) = update { copy(aiDisclaimerAccepted = value) }

    var hiddenBuiltInCategories: Set<IdType>
        get() = getOrDefault().hiddenBuiltInCategories
        set(value) = update { copy(hiddenBuiltInCategories = value) }

    var customAgentSystemPrompt: String?
        get() = getOrDefault().customAgentSystemPrompt
        set(value) = update { copy(customAgentSystemPrompt = value) }

    var customTextTransformationSystemPrompt: String?
        get() = getOrDefault().customTextTransformationSystemPrompt
        set(value) = update { copy(customTextTransformationSystemPrompt = value) }

    var favoritePrompts: Set<IdType>
        get() = getOrDefault().favoritePrompts
        set(value) = update { copy(favoritePrompts = value) }

    /** Auto-delete raw logs older than this many days. null = no auto-delete. */
    var rawLogRetentionDays: Int?
        get() = getOrDefault().rawLogRetentionDays
        set(value) = update { copy(rawLogRetentionDays = value) }

    /** When true, auto-hide the agent log panel when a task finishes (unless it errored). */
    var autoHideAgentLogOnCompletion: Boolean
        get() = getOrDefault().autoHideAgentLogOnCompletion
        set(value) = update { copy(autoHideAgentLogOnCompletion = value) }

    /**
     * Language name for AI prompts (e.g. "suomi", "English", "Tagalog").
     * If aiLanguage is null, returns the app's display language.
     * If aiLanguage is a BCP 47 code (from the preset list), resolves it to a display name.
     * If aiLanguage is a free-form name (from custom input), returns it as-is.
     */
    val aiDisplayLanguage: String
        get() {
            val tag = aiLanguage ?: return Locale.getDefault().let { it.getDisplayLanguage(it) }
            val locale = Locale.forLanguageTag(tag)
            val displayName = locale.getDisplayLanguage(locale)
            return if (displayName.isNotEmpty() && displayName != tag) displayName else tag
        }
}
