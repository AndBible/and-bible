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
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.AiDocumentFilter
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.RestrictionType
import org.crosswire.jsword.versification.system.Versifications
import org.json.JSONObject

/**
 * Tool for creating a new window in the current workspace.
 *
 * Can optionally open a specific document and key in the new window,
 * or create it minimized.
 */
object CreateWindowTool : Tool {
    @Serializable
    data class Args(
        val documentInitials: String? = null,
        val key: String? = null,
        val minimized: Boolean = false
    )

    @Serializable
    data class Result(
        val windowId: String,
        val state: String,
        val documentInitials: String?,
        val currentKey: String?
    )

    override val agentTool = AgentTool.CREATE_WINDOW
    override val category = ToolCategory.WINDOWS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_create_window

    override val description = """
        Create a new window in the current workspace.
        Optionally specify a document and verse/key to display.
        If no document is specified, the new window copies the active window's document and position.
        Use getInstalledDocuments to find available documents and getWindows to see existing windows.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          documentInitials:
            type: string
            description: "Document initials (e.g., 'KJV', 'ESV', 'MHC'). If omitted, copies the active window's document."
          key:
            type: string
            description: "OSIS reference to navigate to (e.g., 'Gen.1.1', 'Matt.5'). If omitted, uses the active window's current position."
          minimized:
            type: boolean
            description: "If true, create the window minimized (hidden). Default: false."
    """)

    private val windowControl get() = BibleApplication.application.applicationComponent.windowControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val app = BibleApplication.application
        val initials = arguments.optString("documentInitials", "").takeIf { it.isNotBlank() }
        val key = arguments.optString("key", "").takeIf { it.isNotBlank() }
        return if (initials != null && key != null) {
            app.getString(R.string.action_create_window_with_doc_and_key, initials, key)
        } else if (initials != null) {
            app.getString(R.string.action_create_window_with_doc, initials)
        } else {
            app.getString(R.string.action_create_window)
        }
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val app = BibleApplication.application
        val initials = arguments.optString("documentInitials", "").takeIf { it.isNotBlank() }
        val key = arguments.optString("key", "").takeIf { it.isNotBlank() }
        val minimized = arguments.optBoolean("minimized", false)
        val parts = mutableListOf<String>()
        if (initials != null) parts.add(initials)
        if (key != null) parts.add(key)
        if (minimized) parts.add(app.getString(R.string.tool_log_window_minimized))
        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val app = BibleApplication.application
        val data = result.data as Result
        return "${data.documentInitials ?: app.getString(R.string.tool_log_window_copy)} (${data.state})"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        return try {
            val window = if (args.documentInitials != null) {
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
                        windowControl.addNewWindow(book, key)
                    } else {
                        val newWindow = windowControl.addNewWindow(windowControl.activeWindow)
                        newWindow.pageManager.setCurrentDocument(book)
                        newWindow
                    }
                }
            } else {
                withContext(Dispatchers.Main.immediate) {
                    windowControl.addNewWindow(windowControl.activeWindow)
                }
            }

            if (args.minimized) {
                withContext(Dispatchers.Main.immediate) {
                    windowControl.minimiseWindow(window, force = true)
                }
            }

            val page = window.pageManager.currentPage
            typedSuccess(Result(
                windowId = window.id.toString(),
                state = window.windowState.toString(),
                documentInitials = page.currentDocument?.initials,
                currentKey = page.key?.osisRef
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to create window: ${e.message}", "CREATE_ERROR")
        }
    }
}
