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

/** Type of note editor entity for text transformation routing. */
enum class NoteEditorEntityType {
    BOOKMARK_NOTE,
    STUDYPAD_TEXT,
    MY_DOCUMENT_PAGE;
}

/** Context available during agent prompt execution (selected verses, active document, etc.). */
data class AgentContext(
    val promptId: IdType,
    val workspaceId: IdType? = null,
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
    /** Overrides global/prompt deny in computeExcludedTools — re-enables tools the prompt needs.
     *  Separate from promptAllowedTools which controls permission auto-allow in checkPermission. */
    val promptAvailableTools: Set<AgentTool>? = null,
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
    val createdPageIds: Set<IdType> = emptySet(),
    val noteEditorEntityType: NoteEditorEntityType? = null,
    /** Note editor entity ID (bookmark UUID, studypad entry UUID, or MyDocument page ID) */
    val noteEditorEntityId: String? = null,
    /** Current text content in the note editor */
    val noteEditorContent: String? = null,
    /** Content type of the editor: "MARKDOWN" or "HTML" */
    val noteEditorContentType: String? = null,
    /** Workspace context: summary of all windows for workspace-level prompts */
    val workspaceWindowsSummary: String? = null,
    /** Start ordinal of user's selection in non-Bible documents (for focus indication via §-anchors) */
    val selectionStartOrdinal: Int? = null,
    /** End ordinal of user's selection in non-Bible documents (for focus indication via §-anchors) */
    val selectionEndOrdinal: Int? = null,
    /** Book key (osisRef) of the source document — used for non-Bible AI doc marker matching */
    val sourceBookKey: String? = null
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
    val selectionStartOrdinal: Int? = null,
    val selectionEndOrdinal: Int? = null,
    val userSpecification: String? = null,
    /** Source book initials for non-Bible pages (commentary, etc.) — used for AI doc marker matching */
    val sourceBookKey: String? = null
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
                selectionStartOrdinal = ctx.selectionStartOrdinal,
                selectionEndOrdinal = ctx.selectionEndOrdinal,
                userSpecification = ctx.userSpecification,
                sourceBookKey = ctx.sourceBookKey ?: ctx.verseRefString
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
