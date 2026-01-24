/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.passage.VerseRange
import java.security.MessageDigest

/**
 * Context information available during agent prompt execution.
 *
 * Contains information about the current state when the prompt was invoked,
 * such as selected verses, active document, etc. Tools can use this context
 * to access relevant data without requiring explicit parameters.
 *
 * @param promptId ID of the AgentPrompt being executed
 * @param selectedVerseRange Currently selected verse range (if any)
 * @param selectedContent OSIS XML content of the selection (if any)
 * @param activeDocumentInitials Initials of the active document (e.g., "KJV")
 * @param activeLabelId ID of the active label/StudyPad (if in StudyPad context)
 * @param windowId ID of the window where the prompt was invoked
 * @param selectedText Plain text of the verse/page context
 * @param highlightedText Specific text highlighted/selected by user (words, phrases)
 */
data class AgentContext(
    val promptId: IdType,
    val selectedVerseRange: VerseRange? = null,
    val selectedContent: String? = null,
    val activeDocumentInitials: String? = null,
    val activeLabelId: IdType? = null,
    val windowId: IdType? = null,
    val selectedText: String? = null,
    val highlightedText: String? = null,
    /** Session-level write permission (for ASK_ONCE_PER_RUN mode) */
    val grantedWritePermission: Boolean = false
) {
    /**
     * Get the verse reference as a string, or null if not available.
     */
    val verseRefString: String?
        get() = selectedVerseRange?.osisRef

    /**
     * Returns a copy of this context with write permission granted.
     */
    fun withWritePermissionGranted() = copy(grantedWritePermission = true)
}

/**
 * Context data for cache key computation.
 *
 * Contains all fields that affect the LLM's output, used for:
 * 1. Computing a SHA-256 hash for strict context matching
 * 2. Extracting KJVA ordinals for loose matching (verse-only)
 * 3. JSON serialization for debugging/display
 */
@Serializable
data class CacheableContext(
    val kjvOrdinalStart: Int?,
    val kjvOrdinalEnd: Int?,
    val activeDocumentInitials: String?,
    val selectedContent: String?,
    val selectedText: String?,
    val highlightedText: String?
) {
    companion object {
        private val json = Json { prettyPrint = false }

        /**
         * Create CacheableContext from AgentContext.
         * Converts verse range to KJVA versification for cross-version caching.
         */
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
                highlightedText = ctx.highlightedText
            )
        }
    }

    /**
     * Compute SHA-256 hash of the full context.
     * Used for cache lookup when strictContextMatching=true.
     *
     * @return Hex string of first 16 bytes of SHA-256 hash (32 chars)
     */
    fun computeHash(): String {
        val jsonStr = json.encodeToString(this)
        val bytes = MessageDigest.getInstance("SHA-256").digest(jsonStr.toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Serialize context to JSON string.
     * Stored in sourceContext field for debugging/display.
     */
    fun toJson(): String = json.encodeToString(this)
}
