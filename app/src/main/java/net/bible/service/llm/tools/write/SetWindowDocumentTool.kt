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

package net.bible.service.llm.tools.write

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.versification.system.Versifications
import org.json.JSONObject

/**
 * Tool for changing the document and/or key displayed in a window.
 *
 * Can change the active window or a specific window by ID.
 */
object SetWindowDocumentTool : Tool {
    @Serializable
    data class Args(
        val windowId: String? = null,
        val documentInitials: String = "",
        val key: String? = null
    )

    @Serializable
    data class Result(
        val windowId: String,
        val documentInitials: String,
        val documentName: String,
        val currentKey: String?,
        val currentKeyName: String?
    )

    override val agentTool = AgentTool.SET_WINDOW_DOCUMENT
    override val category = ToolCategory.WINDOWS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_set_window_document

    override val description = """
        Change the document displayed in a window.
        If windowId is omitted, changes the active window.
        Use getInstalledDocuments to find available document initials.
        Use getWindows to find window IDs.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          windowId:
            type: string
            description: "Window ID to change. If omitted, uses the active window. Get IDs from getWindows."
          documentInitials:
            type: string
            description: "Document initials to display (e.g., 'KJV', 'ESV', 'MHC'). Use getInstalledDocuments to find available documents."
          key:
            type: string
            description: "OSIS reference to navigate to (e.g., 'Gen.1.1', 'Matt.5.3-5'). If omitted, keeps the window's current position."
        required: [documentInitials]
    """)

    private val windowControl get() = BibleApplication.application.applicationComponent.windowControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val app = BibleApplication.application
        val initials = arguments.optString("documentInitials", "").takeIf { it.isNotBlank() } ?: return null
        val windowId = arguments.optString("windowId", "").takeIf { it.isNotBlank() }
        val key = arguments.optString("key", "").takeIf { it.isNotBlank() }
        val target = if (windowId != null) shortId(windowId) else app.getString(R.string.action_active_window)
        return if (key != null) {
            app.getString(R.string.action_set_window_document_with_key, target, initials, key)
        } else {
            app.getString(R.string.action_set_window_document, target, initials)
        }
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val initials = arguments.optString("documentInitials", "").takeIf { it.isNotBlank() } ?: return null
        val key = arguments.optString("key", "").takeIf { it.isNotBlank() }
        val windowId = arguments.optString("windowId", "").takeIf { it.isNotBlank() }
        val parts = mutableListOf<String>()
        if (windowId != null) parts.add(shortId(windowId))
        parts.add(initials)
        if (key != null) parts.add(key)
        return parts.joinToString(", ")
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return "${data.documentInitials} @ ${data.currentKeyName ?: data.currentKey ?: "?"}"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.documentInitials.isBlank()) {
            return ToolResult.error("Missing required parameter: documentInitials")
        }

        return try {
            val window = if (args.windowId != null) {
                val windowId = IdType(args.windowId)
                windowControl.windowRepository.getWindow(windowId)
                    ?: return ToolResult.error("Window not found: ${args.windowId}", "WINDOW_NOT_FOUND")
            } else {
                windowControl.windowRepository.activeWindow
            }

            val book = Books.installed().getBook(args.documentInitials)
                ?: return ToolResult.error("Document not found: ${args.documentInitials}", "DOCUMENT_NOT_FOUND")

            if (!AiDocumentFilter.isAllowed(book.initials)) {
                return ToolResult.error("Document not allowed: ${args.documentInitials}", "DOCUMENT_NOT_ALLOWED")
            }

            val key = if (args.key != null) {
                try {
                    book.getKey(args.key)
                } catch (e: Exception) {
                    try {
                        val v11n = (book as? SwordBook)?.versification
                            ?: Versifications.instance().getVersification("KJV")
                        val passage = PassageKeyFactory.instance().getKey(v11n, args.key)
                        passage.getRangeAt(0, RestrictionType.NONE) ?: passage
                    } catch (e2: Exception) {
                        return ToolResult.error("Invalid key: ${args.key} - ${e2.message}", "INVALID_KEY")
                    }
                }
            } else null

            withContext(Dispatchers.Main.immediate) {
                if (key != null) {
                    window.pageManager.setCurrentDocumentAndKey(book, key)
                } else {
                    window.pageManager.setCurrentDocument(book)
                }
            }

            val page = window.pageManager.currentPage
            typedSuccess(Result(
                windowId = window.id.toString(),
                documentInitials = book.initials,
                documentName = book.name,
                currentKey = page.key?.osisRef,
                currentKeyName = page.key?.name
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to set window document: ${e.message}", "SET_ERROR")
        }
    }
}
