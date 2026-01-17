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

import net.bible.android.database.IdType
import org.crosswire.jsword.passage.VerseRange

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
 * @param selectedText Plain text of the user's selection (for free text selection)
 */
data class AgentContext(
    val promptId: IdType,
    val selectedVerseRange: VerseRange? = null,
    val selectedContent: String? = null,
    val activeDocumentInitials: String? = null,
    val activeLabelId: IdType? = null,
    val windowId: IdType? = null,
    val selectedText: String? = null,
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
