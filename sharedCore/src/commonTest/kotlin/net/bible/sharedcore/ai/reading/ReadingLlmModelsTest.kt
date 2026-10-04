package net.bible.sharedcore.ai.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingLlmModelsTest {
    @Test fun defaultState_isNone() {
        assertEquals(ReadingLlmDialog.None, ReadingLlmDialogState().dialog)
    }

    @Test fun favoritesConstant_isStable() {
        assertEquals("__favorites__", ReadingLlmService.FAVORITES_CATEGORY_ID)
    }

    @Test fun promptGroup_carriesCollapseAndFavorites() {
        val g = ReadingPromptGroupVd("Favorites", ReadingLlmService.FAVORITES_CATEGORY_ID, isFavorites = true, collapsed = false,
            prompts = listOf(ReadingPromptVd("p1", "Explain", "", isFavorite = true, specifyBeforeRun = false)))
        assertTrue(g.isFavorites)
        assertFalse(g.collapsed)
        assertEquals("p1", g.prompts.single().id)
    }

    @Test fun model_carriesDefaultAndSupported() {
        val m = ReadingModelVd("m1", "gpt-4o", "OpenAI", isDefault = true, supported = true)
        assertTrue(m.isDefault); assertTrue(m.supported); assertEquals("gpt-4o", m.modelId)
    }
}
