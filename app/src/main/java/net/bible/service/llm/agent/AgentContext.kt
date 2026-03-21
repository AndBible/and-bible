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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.bible.android.common.toV11n
import net.bible.android.database.IdType
import net.bible.service.llm.AgentTool
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.passage.VerseRange
import java.security.MessageDigest

/** Context available during agent prompt execution (selected verses, active document, etc.). */
data class AgentContext(
    val promptId: IdType,
    val selectedVerseRange: VerseRange? = null,
    val selectedContent: String? = null,
    val activeDocumentInitials: String? = null,
    val activeLabelId: IdType? = null,
    val windowId: IdType? = null,
    val selectedText: String? = null,
    val highlightedText: String? = null,
    /** Character offset within start verse for sub-verse selection (null = whole verse) */
    val selectionStartOffset: Int? = null,
    /** Character offset within end verse for sub-verse selection (null = whole verse) */
    val selectionEndOffset: Int? = null,
    /** Session-level write permission for a single tool (for ASK_ONCE_PER_RUN mode) */
    val grantedWritePermission: Boolean = false,
    /** Session-level write permission for ALL tools */
    val grantedAllToolsPermission: Boolean = false,
    /** Per-prompt permission mode override (null = use global default) */
    val promptPermissionMode: PermissionMode? = null,
    /** Per-prompt tool permission overrides (null = no override, use global defaults) */
    val promptAllowedTools: Set<AgentTool>? = null,
    val promptDeniedTools: Set<AgentTool>? = null,
    /** Previous LLM response shown during regeneration, so the LLM can refine its output. */
    val previousResponse: String? = null,
    /** User-provided additional instructions for regeneration (e.g., "make it shorter"). */
    val additionalInstructions: String? = null,
    /** User-provided task specification from the "Specify before run" dialog. */
    val userSpecification: String? = null,
    /** When true, setDocumentTitle is blocked and content is only shown in the log. */
    val noDocumentCreation: Boolean = false,
    /** Page IDs created during this agent session (for permission-free editing of own pages). */
    val createdPageIds: MutableSet<IdType> = mutableSetOf()
) {
    val verseRefString: String?
        get() = selectedVerseRange?.osisRef

    fun withWritePermissionGranted() = copy(grantedWritePermission = true)
    fun withAllToolsPermissionGranted() = copy(grantedAllToolsPermission = true, grantedWritePermission = true)
}

/**
 * Fields that affect LLM output, used for cache key computation.
 * Supports strict matching (SHA-256 hash) and loose matching (KJVA ordinals only).
 */
@Serializable
data class CacheableContext(
    val kjvOrdinalStart: Int?,
    val kjvOrdinalEnd: Int?,
    val activeDocumentInitials: String?,
    val selectedContent: String?,
    val selectedText: String?,
    val highlightedText: String?,
    val selectionStartOffset: Int?,
    val selectionEndOffset: Int?,
    val userSpecification: String? = null
) {
    companion object {
        private val json = Json { prettyPrint = false }

        /** Converts verse range to KJVA versification for cross-version caching. */
        fun fromAgentContext(ctx: AgentContext): CacheableContext {
            val kjvRange = ctx.selectedVerseRange?.let {
                try {
                    it.toV11n(KJVA)
                } catch (e: Exception) {
                    null
                }
            }
            return CacheableContext(
                kjvOrdinalStart = kjvRange?.start?.ordinal,
                kjvOrdinalEnd = kjvRange?.end?.ordinal,
                activeDocumentInitials = ctx.activeDocumentInitials,
                selectedContent = ctx.selectedContent,
                selectedText = ctx.selectedText,
                highlightedText = ctx.highlightedText,
                selectionStartOffset = ctx.selectionStartOffset,
                selectionEndOffset = ctx.selectionEndOffset,
                userSpecification = ctx.userSpecification
            )
        }
    }

    /** SHA-256 hash (first 16 bytes, hex) for strict cache matching. */
    fun computeHash(): String {
        val jsonStr = json.encodeToString(this)
        val bytes = MessageDigest.getInstance("SHA-256").digest(jsonStr.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    fun toJson(): String = json.encodeToString(this)
}
