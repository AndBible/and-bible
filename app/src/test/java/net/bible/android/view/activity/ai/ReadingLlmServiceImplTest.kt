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
package net.bible.android.view.activity.ai

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.service.common.AiSettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.PromptCategory
import net.bible.service.llm.PromptContext
import net.bible.service.llm.PromptRepository
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingLlmServiceImplTest {

    private lateinit var service: ReadingLlmServiceImpl

    @Before
    fun setUp() {
        service = ReadingLlmServiceImpl()
        // Hide every built-in prompt so promptGroupsFor tests only see the prompts each test sets
        // up itself (built-ins would otherwise pollute VERSE_SELECTION/etc. groups).
        AiSettings.hiddenBuiltInPrompts = BuiltInPrompts.allBuiltInPrompts().map { it.id }.toSet()
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    // --- isCategoryCollapsed / setCategoryCollapsed: verbatim classic settings key ------------------

    @Test
    fun isCategoryCollapsed_defaultsToFalse() {
        assertFalse(service.isCategoryCollapsed("VERSE_SELECTION", "some-category-id"))
    }

    @Test
    fun setCategoryCollapsed_roundTrips_viaExactClassicKey() {
        service.setCategoryCollapsed("VERSE_SELECTION", "cat-1", true)
        assertTrue(service.isCategoryCollapsed("VERSE_SELECTION", "cat-1"))
        // Exact classic key from LlmDialogHelper.collapsedPrefKey (context.name + categoryId).
        assertTrue(net.bible.service.common.CommonUtils.settings.getBoolean("llm_cat_collapsed_VERSE_SELECTION_cat-1", false))

        service.setCategoryCollapsed("VERSE_SELECTION", "cat-1", false)
        assertFalse(service.isCategoryCollapsed("VERSE_SELECTION", "cat-1"))
    }

    @Test
    fun categoryCollapsed_nullCategoryId_usesUncategorizedKey() {
        service.setCategoryCollapsed("NOTE_EDITOR", null, true)
        assertTrue(net.bible.service.common.CommonUtils.settings.getBoolean("llm_cat_collapsed_NOTE_EDITOR_uncategorized", false))
        assertTrue(service.isCategoryCollapsed("NOTE_EDITOR", null))
    }

    // --- askModelBeforeRun: reflects CommonUtils.aiSettings -----------------------------------------

    @Test
    fun askModelBeforeRun_reflectsAiSettings() {
        AiSettings.askModelBeforeRun = false
        assertFalse(service.askModelBeforeRun())
        AiSettings.askModelBeforeRun = true
        assertTrue(service.askModelBeforeRun())
    }

    // --- promptGroupsFor: empty catalog --------------------------------------------------------------

    @Test
    fun promptGroupsFor_emptyCatalog_returnsEmptyList() = runBlocking {
        // All built-ins hidden (setUp) and no user prompts inserted -> nothing to show.
        val result = service.promptGroupsFor(PromptContext.VERSE_SELECTION.name, null)
        assertEquals(emptyList<Any>(), result)
    }

    // --- promptGroupsFor: grouping/order/favourites/collapse-seeding, classic parity ----------------

    @Test
    fun promptGroupsFor_ordersFavoritesThenUncategorizedThenCategorized() = runBlocking {
        val category = PromptCategory(name = "Cat A")
        PromptRepository.insertCategory(category)

        val uncategorized = AgentPrompt(name = "Uncategorized prompt", promptTemplate = "x", showIn = setOf(PromptContext.VERSE_SELECTION))
        val categorized = AgentPrompt(
            name = "Categorized prompt", promptTemplate = "x",
            showIn = setOf(PromptContext.VERSE_SELECTION), categoryId = category.id,
        )
        PromptRepository.insertPrompt(uncategorized)
        PromptRepository.insertPrompt(categorized)
        PromptRepository.toggleFavorite(categorized.id)

        val groups = service.promptGroupsFor(PromptContext.VERSE_SELECTION.name, null)

        assertEquals(3, groups.size)

        val favGroup = groups[0]
        assertTrue(favGroup.isFavorites)
        assertEquals(ReadingLlmService.FAVORITES_CATEGORY_ID, favGroup.categoryId)
        assertFalse(favGroup.collapsed) // favourites are always expanded
        assertEquals(1, favGroup.prompts.size)
        assertEquals(categorized.id.toString(), favGroup.prompts[0].id)
        assertTrue(favGroup.prompts[0].isFavorite)

        val uncategorizedGroup = groups[1]
        assertFalse(uncategorizedGroup.isFavorites)
        assertNull(uncategorizedGroup.categoryId)
        assertEquals(1, uncategorizedGroup.prompts.size)
        assertEquals(uncategorized.id.toString(), uncategorizedGroup.prompts[0].id)
        assertFalse(uncategorizedGroup.prompts[0].isFavorite)

        val categorizedGroup = groups[2]
        assertFalse(categorizedGroup.isFavorites)
        assertEquals(category.id.toString(), categorizedGroup.categoryId)
        assertEquals("Cat A", categorizedGroup.categoryName)
        assertEquals(1, categorizedGroup.prompts.size)
        assertEquals(categorized.id.toString(), categorizedGroup.prompts[0].id)
    }

    @Test
    fun promptGroupsFor_seedsCollapsedFromPersistedState_whenAnotherGroupIsExpanded() = runBlocking {
        val category = PromptCategory(name = "Cat B")
        PromptRepository.insertCategory(category)
        val uncategorized = AgentPrompt(name = "U", promptTemplate = "x", showIn = setOf(PromptContext.VERSE_SELECTION))
        val categorizedPrompt = AgentPrompt(
            name = "C", promptTemplate = "x",
            showIn = setOf(PromptContext.VERSE_SELECTION), categoryId = category.id,
        )
        PromptRepository.insertPrompt(uncategorized)
        PromptRepository.insertPrompt(categorizedPrompt)

        // Uncategorized marked collapsed; categorized left at its (expanded) default.
        service.setCategoryCollapsed(PromptContext.VERSE_SELECTION.name, null, true)

        val groups = service.promptGroupsFor(PromptContext.VERSE_SELECTION.name, null)
        assertEquals(2, groups.size)
        assertTrue(groups[0].collapsed) // uncategorized: persisted collapsed, NOT forced open
        assertFalse(groups[1].collapsed) // categorized: already expanded by default
    }

    @Test
    fun promptGroupsFor_expandsFirstGroup_whenNoneWouldOtherwiseBeExpanded() = runBlocking {
        val category = PromptCategory(name = "Cat C")
        PromptRepository.insertCategory(category)
        val uncategorized = AgentPrompt(name = "U", promptTemplate = "x", showIn = setOf(PromptContext.VERSE_SELECTION))
        val categorizedPrompt = AgentPrompt(
            name = "C", promptTemplate = "x",
            showIn = setOf(PromptContext.VERSE_SELECTION), categoryId = category.id,
        )
        PromptRepository.insertPrompt(uncategorized)
        PromptRepository.insertPrompt(categorizedPrompt)

        // Both groups explicitly marked collapsed -> classic "if none expanded, expand first".
        service.setCategoryCollapsed(PromptContext.VERSE_SELECTION.name, null, true)
        service.setCategoryCollapsed(PromptContext.VERSE_SELECTION.name, category.id.toString(), true)

        val groups = service.promptGroupsFor(PromptContext.VERSE_SELECTION.name, null)
        assertEquals(2, groups.size)
        assertFalse(groups[0].collapsed) // forced open (first group)
        assertTrue(groups[1].collapsed) // untouched
    }

    // --- toggleFavorite --------------------------------------------------------------------------

    @Test
    fun toggleFavorite_flipsPromptRepositoryFavoriteState() = runBlocking {
        val prompt = AgentPrompt(name = "Fav test", promptTemplate = "x")
        PromptRepository.insertPrompt(prompt)
        assertFalse(PromptRepository.isFavorite(prompt.id))

        service.toggleFavorite(prompt.id.toString())
        assertTrue(PromptRepository.isFavorite(prompt.id))

        service.toggleFavorite(prompt.id.toString())
        assertFalse(PromptRepository.isFavorite(prompt.id))
    }

    // --- configuredModels: default-first sort + provider join + supported flag ---------------------

    @Test
    fun configuredModels_sortsDefaultFirst_joinsProvider_andFlagsSupported() = runBlocking {
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

        val provider = LlmProviderConfig(providerType = "GEMINI", displayName = "Test Gemini")
        providerDao.insert(provider)

        // "gpt-4o-mini" has no `supported=true` pricing entry; "gemini-2.5-flash" does.
        val unsupportedModel = LlmConfiguredModel(providerConfigId = provider.id, modelId = "gpt-4o-mini")
        val supportedModel = LlmConfiguredModel(providerConfigId = provider.id, modelId = "gemini-2.5-flash")
        modelDao.insert(unsupportedModel)
        modelDao.insert(supportedModel)
        AiSettings.defaultModelId = supportedModel.id

        val result = service.configuredModels()

        assertEquals(2, result.size)
        assertEquals(supportedModel.id.toString(), result[0].id)
        assertTrue(result[0].isDefault)
        assertTrue(result[0].supported)
        assertEquals("Test Gemini", result[0].providerName)

        assertEquals(unsupportedModel.id.toString(), result[1].id)
        assertFalse(result[1].isDefault)
        assertFalse(result[1].supported)
    }

    // --- setPromptModelDefault: built-in -> override table, user -> configuredModelId --------------

    @Test
    fun setPromptModelDefault_builtinPrompt_setsBuiltinOverride() = runBlocking {
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val provider = LlmProviderConfig(providerType = "GEMINI", displayName = "Test Gemini")
        providerDao.insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "gemini-2.5-flash")
        modelDao.insert(model)

        val builtin = BuiltInPrompts.allBuiltInPrompts().first()
        assertTrue(PromptRepository.isBuiltIn(builtin.id))

        service.setPromptModelDefault(builtin.id.toString(), model.id.toString())

        val resolved = PromptRepository.promptById(builtin.id)
        assertEquals(model.id, resolved?.configuredModelId)
    }

    @Test
    fun setPromptModelDefault_userPrompt_updatesConfiguredModelId() = runBlocking {
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val provider = LlmProviderConfig(providerType = "GEMINI", displayName = "Test Gemini")
        providerDao.insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "gemini-2.5-flash")
        modelDao.insert(model)

        val userPrompt = AgentPrompt(name = "User prompt", promptTemplate = "x")
        PromptRepository.insertPrompt(userPrompt)

        service.setPromptModelDefault(userPrompt.id.toString(), model.id.toString())

        val resolved = PromptRepository.promptById(userPrompt.id)
        assertEquals(model.id, resolved?.configuredModelId)
    }

    // --- promptRequiresModelChoice -------------------------------------------------------------------

    @Test
    fun promptRequiresModelChoice_trueOnlyWhenAskEnabledAndNoOverride() = runBlocking {
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val provider = LlmProviderConfig(providerType = "GEMINI", displayName = "Test Gemini")
        providerDao.insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "gemini-2.5-flash")
        modelDao.insert(model)

        val prompt = AgentPrompt(name = "P", promptTemplate = "x")
        PromptRepository.insertPrompt(prompt)

        AiSettings.askModelBeforeRun = false
        assertFalse(service.promptRequiresModelChoice(prompt.id.toString()))

        AiSettings.askModelBeforeRun = true
        assertTrue(service.promptRequiresModelChoice(prompt.id.toString()))

        service.setPromptModelDefault(prompt.id.toString(), model.id.toString())
        assertFalse(service.promptRequiresModelChoice(prompt.id.toString()))
    }

    // --- regenerateRequiresModelChoice: mirrors startRegenerateWithModelCheck ----------------------

    @Test
    fun regenerateRequiresModelChoice_looksUpPageSourcePromptOverride() = runBlocking {
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val provider = LlmProviderConfig(providerType = "GEMINI", displayName = "Test Gemini")
        providerDao.insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "gemini-2.5-flash")
        modelDao.insert(model)

        val sourcePrompt = AgentPrompt(name = "Source prompt", promptTemplate = "x")
        PromptRepository.insertPrompt(sourcePrompt)

        val aiDocument = MyDocumentBookManager.getOrCreateAIDocument()
        val myDocumentDao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val page = MyDocumentPage(
            documentId = aiDocument.id,
            title = "Test page",
            pageKey = "page_${IdType()}",
            orderNumber = 0,
            sourcePromptId = sourcePrompt.id,
        )
        myDocumentDao.insertPageWithContent(page, "content")

        AiSettings.askModelBeforeRun = true
        assertTrue(service.regenerateRequiresModelChoice(page.id.toString()))

        service.setPromptModelDefault(sourcePrompt.id.toString(), model.id.toString())
        assertFalse(service.regenerateRequiresModelChoice(page.id.toString()))

        AiSettings.askModelBeforeRun = false
        assertFalse(service.regenerateRequiresModelChoice(page.id.toString()))
    }

    @Test
    fun regenerateRequiresModelChoice_unknownPage_stillTrueWhenAskEnabled() = runBlocking {
        // Classic `startRegenerateWithModelCheck`: an unresolvable page/prompt has no override
        // either, so `prompt?.configuredModelId != null` is false -> the model picker is shown.
        AiSettings.askModelBeforeRun = true
        assertTrue(service.regenerateRequiresModelChoice(IdType().toString()))
    }

    @Test
    fun regenerateRequiresModelChoice_unknownPage_falseWhenAskDisabled() = runBlocking {
        AiSettings.askModelBeforeRun = false
        assertFalse(service.regenerateRequiresModelChoice(IdType().toString()))
    }
}
