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

package net.bible.android.view.activity.ai

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.agent.AgentEvent
import net.bible.service.llm.agent.AgentExecutor
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.llm.tools.ToolResult
import org.json.JSONObject

private val ToolResult.isSuccess: Boolean
    get() = this is ToolResult.Success

private val ToolResult.data: JSONObject?
    get() = (this as? ToolResult.Success)?.data as? JSONObject

private val ToolResult.status: String
    get() = when (this) {
        is ToolResult.Success -> "success"
        is ToolResult.Error -> "error: $message"
    }

/**
 * Test activity for LLM tools and agent functionality.
 * This is a temporary development/testing tool.
 */
class LlmTestActivity : ActivityBase() {

    private lateinit var resultText: TextView
    private lateinit var customPromptInput: EditText
    private var testPromptId: IdType = IdType()
    private var testLabelId: IdType? = null
    private var testBookmarkId: IdType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.llm_test)

        buildActivityComponent().inject(this)

        title = "LLM Test Suite"

        resultText = findViewById(R.id.resultText)
        customPromptInput = findViewById(R.id.customPromptInput)

        setupReadToolButtons()
        setupWriteToolButtons()
        setupLlmDrivenTestButtons()
        setupAgentButtons()
        setupCleanupButtons()

        findViewById<Button>(R.id.btnClearResults).setOnClickListener {
            resultText.text = "Press a button to run a test..."
        }
    }

    private fun setupReadToolButtons() {
        findViewById<Button>(R.id.btnGetVerseContent).setOnClickListener {
            runTool("getVerseContent", JSONObject().apply {
                put("verseRef", "Matt.5.3")
            })
        }

        findViewById<Button>(R.id.btnGetAllLabels).setOnClickListener {
            runTool("getAllLabels", JSONObject())
        }

        findViewById<Button>(R.id.btnSearchBookmarks).setOnClickListener {
            runTool("getBookmarksWithLabel", JSONObject().apply {
                put("limit", 5)
            })
        }

        findViewById<Button>(R.id.btnGetBookmarkById).setOnClickListener {
            lifecycleScope.launch {
                val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
                val firstBookmark = withContext(Dispatchers.IO) {
                    dao.allBookmarks().firstOrNull()
                }
                if (firstBookmark != null) {
                    runTool("getBookmarksForVerse", JSONObject().apply {
                        put("verseRef", firstBookmark.verseRange.osisRef)
                    })
                } else {
                    appendResult("No bookmarks found")
                }
            }
        }
    }

    private fun setupWriteToolButtons() {
        findViewById<Button>(R.id.btnCreateBookmark).setOnClickListener {
            runTool("createBookmark", JSONObject().apply {
                put("verseRef", "John.3.16")
                put("note", "Test bookmark created by LLM test suite at ${System.currentTimeMillis()}")
                put("noteContentType", "MARKDOWN")
            }) { result ->
                if (result.isSuccess) {
                    testBookmarkId = IdType.fromString(result.data?.optString("id") ?: "")
                }
            }
        }

        findViewById<Button>(R.id.btnCreateLabel).setOnClickListener {
            val labelName = "LLM Test ${System.currentTimeMillis()}"
            runTool("createLabel", JSONObject().apply {
                put("name", labelName)
            }) { result ->
                if (result.isSuccess) {
                    testLabelId = IdType.fromString(result.data?.optString("id") ?: "")
                }
            }
        }

        findViewById<Button>(R.id.btnAddStudyPadEntry).setOnClickListener {
            if (testLabelId == null) {
                appendResult("ERROR: Create a test label first")
                return@setOnClickListener
            }
            runTool("addStudyPadEntry", JSONObject().apply {
                put("labelId", testLabelId.toString())
                put("text", "# Test Entry\n\nThis is a test StudyPad entry created at ${System.currentTimeMillis()}")
                put("contentType", "MARKDOWN")
            })
        }

        findViewById<Button>(R.id.btnAddBookmarkNote).setOnClickListener {
            if (testBookmarkId == null) {
                appendResult("ERROR: Create a test bookmark first (without note), or use an existing bookmark without a note")
                return@setOnClickListener
            }
            runTool("addBookmarkNote", JSONObject().apply {
                put("bookmarkId", testBookmarkId.toString())
                put("note", "Note added by LLM test at ${System.currentTimeMillis()}")
                put("contentType", "MARKDOWN")
            })
        }
    }

    private fun setupLlmDrivenTestButtons() {
        // Test: LLM creates a bookmark with a thoughtful note
        findViewById<Button>(R.id.btnLlmCreateBookmark).setOnClickListener {
            runLlmDrivenTest(
                testName = "Create Bookmark",
                promptTemplate = """
                    Create a bookmark for Romans 8:28 with a meaningful note about
                    how this verse provides comfort in difficult times.
                    Use the createBookmark tool.
                    Mark the note as created by "LLM test" so it can be cleaned up.
                """.trimIndent()
            )
        }

        // Test: LLM lists labels and describes them
        findViewById<Button>(R.id.btnLlmListLabels).setOnClickListener {
            runLlmDrivenTest(
                testName = "List Labels",
                promptTemplate = """
                    Use the getAllLabels tool to get a list of all labels/study pads.
                    Then provide a brief summary of what labels exist and how many there are.
                """.trimIndent()
            )
        }

        // Test: LLM reads a verse and provides a summary
        findViewById<Button>(R.id.btnLlmReadAndSummarize).setOnClickListener {
            runLlmDrivenTest(
                testName = "Read and Summarize",
                promptTemplate = """
                    Read Psalm 23:1-6 using the getVerseContent tool.
                    Then provide a brief theological summary of the psalm's meaning.
                """.trimIndent()
            )
        }

        // Test: LLM creates a label and adds a study pad entry (multi-tool)
        findViewById<Button>(R.id.btnLlmCreateLabelWithEntry).setOnClickListener {
            runLlmDrivenTest(
                testName = "Create Label + Entry",
                promptTemplate = """
                    Create a new label called "LLM Test Study" using the createLabel tool.
                    Then add a StudyPad entry to it with a brief introduction about
                    studying the Bible systematically. Use MARKDOWN format.
                    Make sure to use the label ID returned by createLabel when calling addStudyPadEntry.
                """.trimIndent()
            )
        }

        // Custom prompt input
        findViewById<Button>(R.id.btnRunCustomPrompt).setOnClickListener {
            val customPrompt = customPromptInput.text.toString().trim()
            if (customPrompt.isEmpty()) {
                appendResult("ERROR: Enter a prompt first")
                return@setOnClickListener
            }
            runLlmDrivenTest(
                testName = "Custom",
                promptTemplate = customPrompt
            )
        }
    }

    /**
     * Run an LLM-driven test with a dynamically created prompt.
     */
    private fun runLlmDrivenTest(testName: String, promptTemplate: String) {
        lifecycleScope.launch {
            appendResult("\n=== LLM Test: $testName ===")
            appendResult("Prompt: ${promptTemplate.take(100)}...")

            // Create a temporary prompt
            val promptId = IdType()
            val prompt = AgentPrompt(
                id = promptId,
                name = "Test: $testName",
                promptTemplate = promptTemplate
            )

            // Insert temporarily into DB (AgentExecutor reads from DB)
            val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
            withContext(Dispatchers.IO) {
                dao.insert(prompt)
            }

            val context = AgentContext(
                promptId = promptId,
                activeDocumentInitials = "KJV"
            )

            val executor = AgentExecutor()

            try {
                executor.execute(promptId, context).collect { event ->
                    when (event) {
                        is AgentEvent.Started -> appendResult("Agent started")
                        is AgentEvent.Iteration -> appendResult("Iteration ${event.number}")
                        is AgentEvent.ToolCalling -> appendResult("→ Calling: ${event.toolName}")
                        is AgentEvent.ToolCompleted -> {
                            val statusText = event.result.status
                            appendResult("← ${event.toolName}: $statusText")
                        }
                        is AgentEvent.TextResponse -> {
                            if (event.isFinal) {
                                appendResult("\n--- LLM Response ---")
                            }
                            appendResult(event.text.take(800))
                        }
                        is AgentEvent.Completed -> appendResult("\n✓ Completed in ${event.totalIterations} iterations")
                        is AgentEvent.Error -> appendResult("✗ ERROR: ${event.message}")
                        is AgentEvent.Cancelled -> appendResult("Cancelled")
                    }
                }
            } catch (e: Exception) {
                appendResult("Exception: ${e.message}")
            } finally {
                // Clean up the temporary prompt
                withContext(Dispatchers.IO) {
                    dao.deleteById(promptId)
                }
            }
        }
    }

    private fun setupAgentButtons() {
        findViewById<Button>(R.id.btnRunAgent).setOnClickListener {
            lifecycleScope.launch {
                val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
                val firstPrompt = withContext(Dispatchers.IO) {
                    dao.allPrompts().firstOrNull()
                }

                if (firstPrompt == null) {
                    appendResult("ERROR: No prompts found. Create a prompt first.")
                    return@launch
                }

                appendResult("\n--- Running Agent ---")
                appendResult("Prompt: ${firstPrompt.name}")
                appendResult("Template: ${firstPrompt.promptTemplate.take(100)}...")

                val context = AgentContext(
                    promptId = firstPrompt.id,
                    selectedVerseRange = null,
                    selectedContent = null,
                    activeDocumentInitials = "KJV"
                )

                val executor = AgentExecutor()

                try {
                    executor.execute(firstPrompt.id, context).collect { event ->
                        when (event) {
                            is AgentEvent.Started -> appendResult("Agent started")
                            is AgentEvent.Iteration -> appendResult("Iteration ${event.number}")
                            is AgentEvent.ToolCalling -> appendResult("Calling tool: ${event.toolName}")
                            is AgentEvent.ToolCompleted -> appendResult("Tool ${event.toolName} completed: ${event.result.status}")
                            is AgentEvent.TextResponse -> {
                                if (event.isFinal) {
                                    appendResult("\n--- Final Response ---")
                                }
                                appendResult(event.text.take(500))
                            }
                            is AgentEvent.Completed -> appendResult("\nAgent completed in ${event.totalIterations} iterations")
                            is AgentEvent.Error -> appendResult("ERROR: ${event.message}")
                            is AgentEvent.Cancelled -> appendResult("Agent cancelled")
                        }
                    }
                } catch (e: Exception) {
                    appendResult("Exception: ${e.message}")
                }
            }
        }
    }

    private fun setupCleanupButtons() {
        findViewById<Button>(R.id.btnCleanupTestData).setOnClickListener {
            lifecycleScope.launch {
                appendResult("\n--- Cleanup ---")

                val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

                withContext(Dispatchers.IO) {
                    // Delete test labels (those starting with "LLM Test")
                    val labels = bookmarkDao.allLabelsSortedByName()
                    var deletedLabels = 0
                    for (label in labels) {
                        if (label.name.startsWith("LLM Test")) {
                            bookmarkDao.delete(label)
                            deletedLabels++
                        }
                    }

                    // Delete bookmarks with test notes
                    val bookmarks = bookmarkDao.allBookmarks()
                    var deletedBookmarks = 0
                    for (bookmark in bookmarks) {
                        if (bookmark.notes?.contains("LLM test") == true ||
                            bookmark.notes?.contains("test suite") == true) {
                            bookmarkDao.delete(bookmark)
                            deletedBookmarks++
                        }
                    }

                    withContext(Dispatchers.Main) {
                        appendResult("Deleted $deletedLabels test labels")
                        appendResult("Deleted $deletedBookmarks test bookmarks")
                    }
                }

                testLabelId = null
                testBookmarkId = null
            }
        }
    }

    private fun runTool(
        toolName: String,
        arguments: JSONObject,
        onComplete: ((ToolResult) -> Unit)? = null
    ) {
        lifecycleScope.launch {
            appendResult("\n--- $toolName ---")
            appendResult("Args: $arguments")

            val tool = ToolRegistry.get(toolName)
            if (tool == null) {
                appendResult("ERROR: Tool not found: $toolName")
                return@launch
            }

            val context = AgentContext(promptId = testPromptId)

            try {
                val result = withContext(Dispatchers.IO) {
                    tool.execute(arguments, context)
                }

                appendResult("Status: ${result.status}")
                appendResult("Result: ${result.toJson().take(1000)}")

                onComplete?.invoke(result)
            } catch (e: Exception) {
                appendResult("Exception: ${e.message}")
            }
        }
    }

    private fun appendResult(text: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            val current = resultText.text.toString()
            resultText.text = if (current == "Press a button to run a test...") {
                text
            } else {
                "$current\n$text"
            }
        }
    }
}
