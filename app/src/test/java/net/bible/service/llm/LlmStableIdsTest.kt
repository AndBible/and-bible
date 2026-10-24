package net.bible.service.llm

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.service.llm.agent.CacheableContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.UUID

/** Pins stored ids and cache hashes against the original java.util / java.security implementations. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LlmStableIdsTest {
    private fun oracleId(key: String) =
        IdType.fromString(UUID.nameUUIDFromBytes("andbible-builtin:$key".toByteArray()).toString())

    @Test fun builtInIdsUnchanged() {
        val expected = mapOf(
            "translate-ui-language" to BuiltInPrompts.TRANSLATE_UI_LANGUAGE_ID,
            "summary" to BuiltInPrompts.SUMMARY_ID,
            "explain-verses" to BuiltInPrompts.EXPLAIN_VERSES_ID,
            "explain-verses-studypad" to BuiltInPrompts.EXPLAIN_VERSES_STUDYPAD_ID,
            "strongs-annotation" to BuiltInPrompts.STRONGS_ANNOTATION_ID,
            "word-study" to BuiltInPrompts.WORD_STUDY_ID,
            "cross-references" to BuiltInPrompts.CROSS_REFERENCES_ID,
            "compare-translations" to BuiltInPrompts.COMPARE_TRANSLATIONS_ID,
            "thematic-study" to BuiltInPrompts.THEMATIC_STUDY_ID,
            "bookmark-annotate" to BuiltInPrompts.BOOKMARK_ANNOTATE_ID,
            "study-layout" to BuiltInPrompts.STUDY_LAYOUT_ID,
            "workspace-assistant" to BuiltInPrompts.WORKSPACE_ASSISTANT_ID,
            "enhance-note" to BuiltInPrompts.ENHANCE_NOTE_ID,
            "ask-question" to BuiltInPrompts.ASK_QUESTION_ID,
            "custom-prompt" to BuiltInPrompts.CUSTOM_PROMPT_ID,
            "test-tool-calling" to BuiltInPrompts.TEST_TOOL_CALLING_ID,
            "test-cross-references" to BuiltInPrompts.TEST_CROSS_REFERENCES_ID,
            "test-create-bookmark" to BuiltInPrompts.TEST_CREATE_BOOKMARK_ID,
            "test-search-bible" to BuiltInPrompts.TEST_SEARCH_BIBLE_ID,
            "test-commentary" to BuiltInPrompts.TEST_COMMENTARY_ID,
            "test-dictionary" to BuiltInPrompts.TEST_DICTIONARY_ID,
            "test-read-bookmarks" to BuiltInPrompts.TEST_READ_BOOKMARKS_ID,
            "test-labels" to BuiltInPrompts.TEST_LABELS_ID,
            "test-studypad" to BuiltInPrompts.TEST_STUDYPAD_ID,
            "test-finish-studypad" to BuiltInPrompts.TEST_FINISH_STUDYPAD_ID,
            "test-update-note" to BuiltInPrompts.TEST_UPDATE_NOTE_ID,
            "test-studypad-read-modes" to BuiltInPrompts.TEST_STUDYPAD_READ_MODES_ID,
            "test-regenerate" to BuiltInPrompts.TEST_REGENERATE_ID,
            "test-capitalize" to BuiltInPrompts.TEST_CAPITALIZE_ID,
            "test-my-documents" to BuiltInPrompts.TEST_MY_DOCUMENTS_ID,
            "category-study" to BuiltInPrompts.CATEGORY_STUDY_ID,
            "category-notes" to BuiltInPrompts.CATEGORY_NOTES_ID,
            "category-general" to BuiltInPrompts.CATEGORY_GENERAL_ID,
            "category-test" to BuiltInPrompts.CATEGORY_TEST_ID,
        )
        assertEquals(34, expected.size)
        for ((key, id) in expected) assertEquals(key, oracleId(key), id)
    }

    @Test fun computeHashUnchanged() {
        val texts = listOf("In the beginning God created", "Alussa Jumala loi taivaan ja maan: äö Å", "emoji 😀 中文 שלום")
        for (t in texts) {
            val ctx = CacheableContext(
                kjvOrdinalStart = 1, kjvOrdinalEnd = 2, activeDocumentInitials = "KJV",
                selectedContent = t, selectedText = t, highlightedText = null,
                selectionStartOffset = null, selectionEndOffset = 3, userSpecification = t,
            )
            val json = kotlinx.serialization.json.Json { prettyPrint = false }.encodeToString(CacheableContext.serializer(), ctx)
            val bytes = MessageDigest.getInstance("SHA-256").digest(json.toByteArray())
            val old = bytes.take(16).joinToString("") { "%02x".format(it) }
            assertEquals(t, old, ctx.computeHash())
        }
    }
}
