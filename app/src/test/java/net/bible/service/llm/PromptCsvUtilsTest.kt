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

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentTool
import net.bible.service.llm.agent.PermissionMode
import net.bible.test.DatabaseResetter.resetDatabase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PromptCsvUtilsTest {

    private lateinit var dao: AgentPromptDao

    @Before
    fun setUp() {
        dao = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
    }

    @After
    fun tearDown() {
        dao.allPrompts().forEach { dao.delete(it) }
        resetDatabase()
    }

    // --- Export tests ---

    @Test
    fun testExportBasicPrompt(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Test Prompt",
            description = "A test description",
            promptTemplate = "Translate {{text}} to Finnish",
            showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.TEXT_SELECTION),
            orderNumber = 5,
            strictContextMatching = false,
            createdAt = 1640995200000L, // 2022-01-01T00:00:00Z
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")
        val lines = csv.split("\n")

        // Header line
        assertTrue(lines[0].startsWith("name;description;promptTemplate;"))
        assertTrue(lines[0].contains("id;createdAt"))

        // Data line
        val dataLine = lines[1]
        assertTrue("Name should be present", dataLine.startsWith("Test Prompt;"))
        assertTrue("Description should be present", dataLine.contains("A test description"))
        assertTrue("Template should be present", dataLine.contains("Translate {{text}} to Finnish"))
        assertTrue("showIn should contain both contexts", dataLine.contains("VERSE_SELECTION") && dataLine.contains("TEXT_SELECTION"))
        assertTrue("orderNumber should be 5", dataLine.contains(";5;"))
        assertTrue("strictContextMatching should be false", dataLine.contains(";false;"))
        assertTrue("createdAt should be formatted", dataLine.contains("2022-01-01T00:00:00Z"))
    }

    @Test
    fun testExportEmptyList(): Unit = runBlocking {
        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, emptyList())

        val csv = outputStream.toString("UTF-8")
        val lines = csv.trim().split("\n")

        // Should only have header
        assertThat(lines.size, equalTo(1))
        assertTrue(lines[0].startsWith("name;"))
    }

    @Test
    fun testExportWithNullOptionalFields(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Minimal Prompt",
            promptTemplate = "Do something",
            description = null,
            permissionMode = null,
            allowedTools = null,
            deniedTools = null,
            configuredModelId = null,
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")
        val lines = csv.split("\n")
        val dataLine = lines[1]

        // Null fields should be empty strings in CSV
        assertTrue("Name present", dataLine.contains("Minimal Prompt"))
        assertTrue("Template present", dataLine.contains("Do something"))
    }

    @Test
    fun testExportWithPermissionModeAndTools(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Advanced Prompt",
            promptTemplate = "Complex task",
            permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK, AgentTool.SEARCH_BIBLE),
            deniedTools = setOf(AgentTool.SET_DOCUMENT_TITLE),
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")
        val dataLine = csv.split("\n")[1]

        assertTrue("permissionMode present", dataLine.contains("ALLOW_ALL"))
        assertTrue("allowedTools present", dataLine.contains("CREATE_BOOKMARK") && dataLine.contains("SEARCH_BIBLE"))
        assertTrue("deniedTools present", dataLine.contains("SET_DOCUMENT_TITLE"))
    }

    @Test
    fun testExportEscapesSemicolonInFields(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Prompt; with semicolon",
            promptTemplate = "Template; with; semicolons",
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")

        // Fields with semicolons should be quoted
        assertTrue("Name should be quoted", csv.contains("\"Prompt; with semicolon\""))
        assertTrue("Template should be quoted", csv.contains("\"Template; with; semicolons\""))
    }

    @Test
    fun testExportEscapesQuotesInFields(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Prompt with \"quotes\"",
            promptTemplate = "Say \"hello\"",
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")

        // Quotes should be escaped as double-quotes inside quoted fields
        assertTrue("Name quotes escaped", csv.contains("\"Prompt with \"\"quotes\"\"\""))
        assertTrue("Template quotes escaped", csv.contains("\"Say \"\"hello\"\"\""))
    }

    @Test
    fun testExportEscapesNewlinesInFields(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Test",
            promptTemplate = "Line 1\nLine 2\nLine 3",
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")

        // Multiline fields should be quoted
        assertTrue("Template should be quoted", csv.contains("\"Line 1\nLine 2\nLine 3\""))
    }

    // --- Import tests ---

    @Test
    fun testImportBasicPrompt(): Unit = runBlocking {
        val csv = "name;description;promptTemplate;showIn;orderNumber;strictContextMatching;permissionMode;allowedTools;deniedTools;configuredModelId;id;createdAt\n" +
            "My Prompt;A description;Do something;VERSE_SELECTION,TEXT_SELECTION;3;false;;;;;\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.updated, equalTo(0))
        assertThat(result.errors, equalTo(0))

        val prompts = dao.allPrompts()
        assertThat(prompts.size, equalTo(1))
        assertThat(prompts[0].name, equalTo("My Prompt"))
        assertThat(prompts[0].description, equalTo("A description"))
        assertThat(prompts[0].promptTemplate, equalTo("Do something"))
        assertTrue(prompts[0].showIn.contains(PromptContext.VERSE_SELECTION))
        assertTrue(prompts[0].showIn.contains(PromptContext.TEXT_SELECTION))
        assertThat(prompts[0].orderNumber, equalTo(3))
        assertFalse(prompts[0].strictContextMatching)
    }

    @Test
    fun testImportWithExistingIdUpdates(): Unit = runBlocking {
        // Insert a prompt first
        val existingId = IdType()
        val existing = AgentPrompt(
            id = existingId,
            name = "Original",
            promptTemplate = "Original template",
        )
        dao.insert(existing)

        // Import CSV with same ID
        val csv = "name;promptTemplate;id\n" +
            "Updated;Updated template;${existingId}\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(0))
        assertThat(result.updated, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val prompts = dao.allPrompts()
        assertThat(prompts.size, equalTo(1))
        assertThat(prompts[0].name, equalTo("Updated"))
        assertThat(prompts[0].promptTemplate, equalTo("Updated template"))
    }

    @Test
    fun testImportWithoutIdCreatesNew(): Unit = runBlocking {
        val csv = "name;promptTemplate\n" +
            "New Prompt;New template\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.updated, equalTo(0))

        val prompts = dao.allPrompts()
        assertThat(prompts.size, equalTo(1))
        assertThat(prompts[0].name, equalTo("New Prompt"))
    }

    @Test
    fun testImportRejectsBuiltInPromptId(): Unit = runBlocking {
        val builtInId = BuiltInPrompts.TRANSLATE_UI_LANGUAGE_ID

        val csv = "name;promptTemplate;id\n" +
            "Hacked Built-in;Evil template;${builtInId}\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(0))
        assertThat(result.updated, equalTo(0))
        assertThat(result.errors, equalTo(1))
        assertTrue(result.errorMessages[0].contains("built-in"))

        // DB should be empty
        assertThat(dao.allPrompts().size, equalTo(0))
    }

    @Test
    fun testImportMissingRequiredName(): Unit = runBlocking {
        val csv = "name;promptTemplate\n" +
            ";Some template\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(0))
        assertThat(result.errors, equalTo(1))
        assertTrue(result.errorMessages[0].contains("name"))
    }

    @Test
    fun testImportMissingRequiredPromptTemplate(): Unit = runBlocking {
        val csv = "name;promptTemplate\n" +
            "Good Name;\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(0))
        assertThat(result.errors, equalTo(1))
        assertTrue(result.errorMessages[0].contains("promptTemplate"))
    }

    @Test
    fun testImportEmptyFile(): Unit = runBlocking {
        val csv = ""
        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))

        try {
            PromptCsvUtils.importPromptsFromCsv(inputStream)
            fail("Should throw IOException for empty file")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Empty CSV file") == true)
        }
    }

    @Test
    fun testImportSkipsBlankRows(): Unit = runBlocking {
        val csv = "name;promptTemplate\n" +
            "Prompt 1;Template 1\n" +
            ";\n" +
            "Prompt 2;Template 2\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        // The blank row has an empty name, so it should error
        // Prompt 1 and 2 should succeed
        assertThat(result.created, equalTo(2))
    }

    @Test
    fun testImportWithPermissionModeAndTools(): Unit = runBlocking {
        val csv = "name;promptTemplate;permissionMode;allowedTools;deniedTools;configuredModelId\n" +
            "Advanced;Do it;ALLOW_ALL;CREATE_BOOKMARK,SEARCH_BIBLE;SET_DOCUMENT_TITLE;\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val prompt = dao.allPrompts()[0]
        assertThat(prompt.permissionMode, equalTo(PermissionMode.ALLOW_ALL))
        assertTrue(prompt.allowedTools!!.contains(AgentTool.CREATE_BOOKMARK))
        assertTrue(prompt.allowedTools!!.contains(AgentTool.SEARCH_BIBLE))
        assertTrue(prompt.deniedTools!!.contains(AgentTool.SET_DOCUMENT_TITLE))
        assertNull(prompt.configuredModelId)
    }

    @Test
    fun testImportWithInvalidPermissionModeIgnored(): Unit = runBlocking {
        val csv = "name;promptTemplate;permissionMode\n" +
            "Test;Template;INVALID_MODE\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertNull(dao.allPrompts()[0].permissionMode)
    }

    @Test
    fun testImportWithInvalidShowInValuesIgnored(): Unit = runBlocking {
        val csv = "name;promptTemplate;showIn\n" +
            "Test;Template;VERSE_SELECTION,INVALID_CONTEXT,TEXT_SELECTION\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        val prompt = dao.allPrompts()[0]
        assertThat(prompt.showIn.size, equalTo(2))
        assertTrue(prompt.showIn.contains(PromptContext.VERSE_SELECTION))
        assertTrue(prompt.showIn.contains(PromptContext.TEXT_SELECTION))
    }

    @Test
    fun testImportWithCreatedAtTimestamp(): Unit = runBlocking {
        val csv = "name;promptTemplate;createdAt\n" +
            "Test;Template;2022-01-01T00:00:00Z\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(dao.allPrompts()[0].createdAt, equalTo(1640995200000L))
    }

    @Test
    fun testImportMultipleRowsMixedResults(): Unit = runBlocking {
        val builtInId = BuiltInPrompts.SUMMARY_ID

        val csv = "name;promptTemplate;id\n" +
            "Good 1;Template 1;\n" +
            ";Missing name;\n" +
            "Built-in;Evil;${builtInId}\n" +
            "Good 2;Template 2;\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(2))
        assertThat(result.updated, equalTo(0))
        assertThat(result.errors, equalTo(2)) // missing name + built-in
    }

    // --- Round-trip tests ---

    @Test
    fun testExportAndImportRoundTrip(): Unit = runBlocking {
        val id = IdType()
        val providerConfigId = IdType()
        // Insert a provider config so the foreign key is satisfied
        val providerConfigDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        providerConfigDao.insert(LlmProviderConfig(
            id = providerConfigId,
            providerType = "GEMINI",
            displayName = "Test Provider"
        ))
        val original = AgentPrompt(
            id = id,
            name = "Round-Trip Prompt",
            description = "Test description",
            promptTemplate = "Translate {{text}} to {{language}}",
            showIn = setOf(PromptContext.VERSE_SELECTION),
            orderNumber = 7,
            strictContextMatching = false,
            permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK, AgentTool.SEARCH_BIBLE),
            deniedTools = setOf(AgentTool.CREATE_LABEL),
            createdAt = 1640995200000L,
        )

        // Export
        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(original))

        // Import
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val imported = dao.allPrompts()[0]
        assertThat(imported.id, equalTo(id))
        assertThat(imported.name, equalTo("Round-Trip Prompt"))
        assertThat(imported.description, equalTo("Test description"))
        assertThat(imported.promptTemplate, equalTo("Translate {{text}} to {{language}}"))
        assertThat(imported.showIn, equalTo(original.showIn))
        assertThat(imported.orderNumber, equalTo(7))
        assertFalse(imported.strictContextMatching)
        assertThat(imported.permissionMode, equalTo(PermissionMode.ASK_ONCE_PER_RUN))
        assertThat(imported.allowedTools, equalTo(setOf(AgentTool.CREATE_BOOKMARK, AgentTool.SEARCH_BIBLE)))
        assertThat(imported.deniedTools, equalTo(setOf(AgentTool.CREATE_LABEL)))
        // configuredModelId is not tested here as it requires a configured model in DB
        assertThat(imported.createdAt, equalTo(1640995200000L))
        assertFalse(imported.bibleOnly)
    }

    @Test
    fun testExportAndImportBibleOnlyRoundTrip(): Unit = runBlocking {
        val original = AgentPrompt(
            name = "Bible Only Prompt",
            promptTemplate = "Explain verses",
            bibleOnly = true,
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(original))

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val imported = dao.allPrompts()[0]
        assertTrue(imported.bibleOnly)
    }

    @Test
    fun testExportAndImportWithMultilineTemplate(): Unit = runBlocking {
        val original = AgentPrompt(
            name = "Multiline",
            promptTemplate = "Line 1\nLine 2\nLine 3\n\nLine 5 after blank",
            createdAt = 1640995200000L,
        )

        // Export
        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(original))

        // Import
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val imported = dao.allPrompts()[0]
        assertThat(imported.promptTemplate, equalTo("Line 1\nLine 2\nLine 3\n\nLine 5 after blank"))
    }

    @Test
    fun testExportAndImportWithSpecialCharacters(): Unit = runBlocking {
        val original = AgentPrompt(
            name = "Special; \"chars\"",
            description = "Description with\nnewline",
            promptTemplate = "Template with; semicolons and \"quotes\" and\nnewlines",
            createdAt = 1640995200000L,
        )

        // Export
        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(original))

        // Import
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val imported = dao.allPrompts()[0]
        assertThat(imported.name, equalTo("Special; \"chars\""))
        assertThat(imported.description, equalTo("Description with\nnewline"))
        assertThat(imported.promptTemplate, equalTo("Template with; semicolons and \"quotes\" and\nnewlines"))
    }

    @Test
    fun testExportAndImportMultiplePrompts(): Unit = runBlocking {
        val prompts = listOf(
            AgentPrompt(name = "Prompt 1", promptTemplate = "Template 1", orderNumber = 1, createdAt = 1640995200000L),
            AgentPrompt(name = "Prompt 2", promptTemplate = "Template 2", orderNumber = 2, createdAt = 1640995200000L),
            AgentPrompt(name = "Prompt 3", promptTemplate = "Template 3", orderNumber = 3, createdAt = 1640995200000L),
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, prompts)

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(3))
        assertThat(result.errors, equalTo(0))
        assertThat(dao.allPrompts().size, equalTo(3))
    }

    @Test
    fun testExportAndImportWithNullOptionalFields(): Unit = runBlocking {
        val original = AgentPrompt(
            name = "Minimal",
            promptTemplate = "Just a template",
            description = null,
            permissionMode = null,
            allowedTools = null,
            deniedTools = null,
            configuredModelId = null,
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(original))

        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))

        val imported = dao.allPrompts()[0]
        assertNull(imported.description)
        assertNull(imported.permissionMode)
        // Note: Room's TypeConverter converts null Set<String>? to empty set on read,
        // so after DB round-trip these are empty sets rather than null.
        assertTrue(imported.allowedTools.isNullOrEmpty())
        assertTrue(imported.deniedTools.isNullOrEmpty())
        assertNull(imported.configuredModelId)
    }

    @Test
    fun testImportSubsetOfColumns(): Unit = runBlocking {
        // CSV with only required columns + one optional
        val csv = "name;promptTemplate;orderNumber\n" +
            "Subset Test;My template;42\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val prompt = dao.allPrompts()[0]
        assertThat(prompt.name, equalTo("Subset Test"))
        assertThat(prompt.promptTemplate, equalTo("My template"))
        assertThat(prompt.orderNumber, equalTo(42))
        // Missing optional fields should get defaults
        assertNull(prompt.description)
        assertTrue(prompt.showIn.isEmpty())
        assertTrue(prompt.strictContextMatching)
        assertNull(prompt.permissionMode)
    }

    // --- Category tests ---

    @Test
    fun testExportIncludesCategoryColumn(): Unit = runBlocking {
        val prompt = AgentPrompt(
            name = "Categorized",
            promptTemplate = "Template",
            createdAt = 1640995200000L,
        )

        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(prompt))

        val csv = outputStream.toString("UTF-8")
        val header = csv.split("\n")[0]
        assertTrue("Header should contain category column", header.contains("category"))
    }

    @Test
    fun testImportWithoutCategoryColumnIsBackwardCompatible(): Unit = runBlocking {
        // Old CSV without category column
        val csv = "name;promptTemplate\n" +
            "Old Prompt;Old template\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val imported = dao.allPrompts()[0]
        assertNull("categoryId should be null for old CSV without category", imported.categoryId)
    }

    @Test
    fun testImportWithEmptyCategoryField(): Unit = runBlocking {
        val csv = "name;promptTemplate;category\n" +
            "No Cat;Template;\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertNull("categoryId should be null for empty category", dao.allPrompts()[0].categoryId)
    }

    @Test
    fun testImportWithCategoryCreatesCategory(): Unit = runBlocking {
        val csv = "name;promptTemplate;category\n" +
            "Cat Prompt;Template;My Category\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val imported = dao.allPrompts()[0]
        assertNotNull("categoryId should be set", imported.categoryId)

        // Verify the category was created
        val categoryDao = DatabaseContainer.instance.aiSettingsDb.promptCategoryDao()
        val category = categoryDao.getById(imported.categoryId!!)
        assertNotNull("Category should exist in DB", category)
        assertThat(category!!.name, equalTo("My Category"))
    }

    @Test
    fun testImportTwoPromptsWithSameCategoryReusesSameCategory(): Unit = runBlocking {
        val csv = "name;promptTemplate;category\n" +
            "Prompt A;Template A;Shared Category\n" +
            "Prompt B;Template B;Shared Category\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(2))

        val prompts = dao.allPrompts()
        assertThat(prompts[0].categoryId, equalTo(prompts[1].categoryId))
    }

    // --- parsePromptsFromCsv tests (Session 2) ---

    @Test
    fun testParsePromptsFromCsvReturnsListWithoutDbOps() {
        val csv = "name;promptTemplate;showIn\n" +
            "Prompt 1;Template 1;VERSE_SELECTION\n" +
            "Prompt 2;Template 2;WINDOW_MENU,TEXT_SELECTION\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val prompts = PromptCsvUtils.parsePromptsFromCsv(inputStream)

        assertThat(prompts.size, equalTo(2))
        assertThat(prompts[0].name, equalTo("Prompt 1"))
        assertThat(prompts[0].promptTemplate, equalTo("Template 1"))
        assertTrue(prompts[0].showIn.contains(PromptContext.VERSE_SELECTION))
        assertThat(prompts[1].name, equalTo("Prompt 2"))
        assertTrue(prompts[1].showIn.contains(PromptContext.WINDOW_MENU))
        assertTrue(prompts[1].showIn.contains(PromptContext.TEXT_SELECTION))

        // Verify no DB side effects
        assertThat(dao.allPrompts().size, equalTo(0))
    }

    @Test
    fun testParsePromptsFromCsvSkipsBadRows() {
        val csv = "name;promptTemplate\n" +
            "Good;Template\n" +
            ";Missing name\n" +
            "Also Good;Another template\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val prompts = PromptCsvUtils.parsePromptsFromCsv(inputStream)

        // Bad row is skipped, 2 good rows parsed
        assertThat(prompts.size, equalTo(2))
        assertThat(prompts[0].name, equalTo("Good"))
        assertThat(prompts[1].name, equalTo("Also Good"))
    }

    @Test
    fun testParsePromptsFromCsvDoesNotResolveCategoriesByDefault() {
        val csv = "name;promptTemplate;category\n" +
            "Test;Template;Some Category\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val prompts = PromptCsvUtils.parsePromptsFromCsv(inputStream)

        assertThat(prompts.size, equalTo(1))
        assertNull("categoryId should be null when resolveCategories=false", prompts[0].categoryId)

        // No category should have been created in DB
        val categoryDao = DatabaseContainer.instance.aiSettingsDb.promptCategoryDao()
        assertThat(categoryDao.all().size, equalTo(0))
    }

    @Test
    fun testParsePromptsFromCsvWithMultilineTemplate() {
        val csv = "name;promptTemplate\n" +
            "Test;\"Line 1\nLine 2\nLine 3\"\n"

        val inputStream = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))
        val prompts = PromptCsvUtils.parsePromptsFromCsv(inputStream)

        assertThat(prompts.size, equalTo(1))
        assertThat(prompts[0].promptTemplate, equalTo("Line 1\nLine 2\nLine 3"))
    }

    // --- BuiltInPrompts category tests ---

    @Test
    fun testDefaultCategoryForBuiltInPrompts() {
        // Study prompts
        assertThat(
            BuiltInPrompts.defaultCategoryForPrompt(BuiltInPrompts.EXPLAIN_VERSES_ID),
            equalTo(BuiltInPrompts.CATEGORY_STUDY_ID)
        )
        assertThat(
            BuiltInPrompts.defaultCategoryForPrompt(BuiltInPrompts.WORD_STUDY_ID),
            equalTo(BuiltInPrompts.CATEGORY_STUDY_ID)
        )

        // Notes prompts
        assertThat(
            BuiltInPrompts.defaultCategoryForPrompt(BuiltInPrompts.ENHANCE_NOTE_ID),
            equalTo(BuiltInPrompts.CATEGORY_NOTES_ID)
        )

        // General prompts
        assertThat(
            BuiltInPrompts.defaultCategoryForPrompt(BuiltInPrompts.TRANSLATE_UI_LANGUAGE_ID),
            equalTo(BuiltInPrompts.CATEGORY_GENERAL_ID)
        )

        // Test prompts
        assertThat(
            BuiltInPrompts.defaultCategoryForPrompt(BuiltInPrompts.TEST_TOOL_CALLING_ID),
            equalTo(BuiltInPrompts.CATEGORY_TEST_ID)
        )
    }

    @Test
    fun testDefaultCategoryForUnknownPromptReturnsNull() {
        val unknownId = IdType()
        assertNull(BuiltInPrompts.defaultCategoryForPrompt(unknownId))
    }

    @Test
    fun testAllBuiltInPromptsHaveDefaultCategory() {
        for (prompt in BuiltInPrompts.allBuiltInPrompts()) {
            val category = BuiltInPrompts.defaultCategoryForPrompt(prompt.id)
            assertNotNull("Built-in prompt '${prompt.name}' should have a default category", category)
        }
    }

    @Test
    fun testRoundTripUpdateExistingPrompt(): Unit = runBlocking {
        // Insert original
        val id = IdType()
        val original = AgentPrompt(
            id = id,
            name = "Original Name",
            promptTemplate = "Original template",
            createdAt = 1640995200000L,
        )
        dao.insert(original)

        // Export a modified version with same ID
        val modified = original.copy(
            name = "Modified Name",
            promptTemplate = "Modified template",
        )
        val outputStream = ByteArrayOutputStream()
        PromptCsvUtils.exportPromptsToCsv(outputStream, listOf(modified))

        // Import should update, not create
        val inputStream = ByteArrayInputStream(outputStream.toByteArray())
        val result = PromptCsvUtils.importPromptsFromCsv(inputStream)

        assertThat(result.created, equalTo(0))
        assertThat(result.updated, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val prompts = dao.allPrompts()
        assertThat(prompts.size, equalTo(1))
        assertThat(prompts[0].name, equalTo("Modified Name"))
        assertThat(prompts[0].promptTemplate, equalTo("Modified template"))
    }
}
