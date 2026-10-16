package net.bible.sharedcore.bibleview

import kotlin.test.Test
import kotlin.test.assertEquals

class BibleViewEventsTest {
    private val e = BibleViewEvents

    @Test fun escapesQuotesBackslashNewlineAndLineSeparators() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u2028e\\u2029f\"", jsString("a\"b\\c\nd e f"))
        assertEquals("null", jsString(null))
        assertEquals(
            "bibleView.emit(\"delete_study_pad_text_entry\", \"it's \\\"x\\\"\")",
            e.deleteStudyPadTextEntry("it's \"x\"").js,
        )
    }

    @Test fun responses() {
        assertEquals("bibleView.response(4, null)", e.response(4, null).js)
        assertEquals("bibleView.response(4, {\"a\":1})", e.response(4, JsExpr("{\"a\":1}")).js)
        assertEquals("bibleView.response(5, \"Gen 1:1\")", e.responseString(5, "Gen 1:1").js)
    }

    @Test fun noArgumentEvents() {
        assertEquals("bibleView.emit(\"scroll_up\")", e.scrollUp().js)
        assertEquals("bibleView.emit(\"scroll_down\")", e.scrollDown().js)
        assertEquals("bibleView.emit(\"reset_loading_count\")", e.resetLoadingCount().js)
        assertEquals("bibleView.emit(\"remove_ranges\")", e.removeRanges().js)
        assertEquals("bibleView.emit(\"close_modals\")", e.closeModals().js)
        assertEquals("bibleView.emit(\"export_html\")", e.exportHtml().js)
        assertEquals("bibleView.emit(\"clear_document\")", e.clearDocument().js)
    }

    @Test fun scrollToVerseKeepsKeyPresence() {
        assertEquals(
            "bibleView.emit(\"scroll_to_verse\", \"o-12\", {\"now\": true, \"highlight\": false, \"ordinalStart\": 12, \"ordinalEnd\": null})",
            e.scrollToVerse("o-12", now = true, highlight = false, ordinalStart = 12, ordinalEnd = null).js,
        )
        assertEquals(
            "bibleView.emit(\"scroll_to_verse\", null, {\"now\": false, \"highlight\": true, \"bookInitials\": \"KJV\", \"osisRef\": null, \"ordinalStart\": 1, \"ordinalEnd\": 3})",
            e.scrollToVerseIn(null, now = false, highlight = true, bookInitials = "KJV", osisRef = null, ordinalStart = 1, ordinalEnd = 3).js,
        )
    }

    @Test fun offsetsAndActive() {
        assertEquals(
            "bibleView.emit(\"set_offsets\", 56.0, 0.0, {\"immediate\": false, \"imeOpen\": true})",
            e.setOffsets(56f, 0f, immediate = false, imeOpen = true).js,
        )
        assertEquals(
            "bibleView.emit(\"set_offsets\", 56.0, 0.0, {\"doNotScroll\": true})",
            e.setOffsetsWithoutScroll(56f, 0f).js,
        )
        assertEquals(
            "bibleView.emit(\"set_active\", {\"isActive\": true, \"hasActiveIndicator\": false})",
            e.setActive(isActive = true, hasActiveIndicator = false).js,
        )
        assertEquals("bibleView.emit(\"set_action_mode\", true)", e.setActionMode(true).js)
        assertEquals("bibleView.emit(\"adjust_loading_count\", -1)", e.adjustLoadingCount(-1).js)
    }

    @Test fun bookmarkClickedOmitsOpenNotesWhenNull() {
        assertEquals(
            "bibleView.emit(\"bookmark_clicked\", \"b1\", {\"openLabels\": true})",
            e.bookmarkClicked("b1", openNotes = null).js,
        )
        assertEquals(
            "bibleView.emit(\"bookmark_clicked\", \"b1\", {\"openLabels\": true, \"openNotes\": false})",
            e.bookmarkClicked("b1", openNotes = false).js,
        )
    }

    @Test fun progressEvents() {
        assertEquals(
            "bibleView.emit(\"update_chapter_read_status\", {\"chapter\": 3, \"count\": 2})",
            e.updateChapterReadStatus(3, 2).js,
        )
        assertEquals(
            "bibleView.emit(\"update_memorization_data\", {\"addedMemorized\": [1,2], \"removedMemorized\": [], \"addedTargets\": [3], \"removedTargets\": []})",
            e.updateMemorizationData(listOf(1, 2), emptyList(), listOf(3), emptyList()).js,
        )
        assertEquals(
            "bibleView.emit(\"update_reading_progress_settings\", {\"x\":1})",
            e.updateReadingProgressSettings(JsExpr("{\"x\":1}")).js,
        )
    }

    @Test fun bookmarkAndLabelEvents() {
        assertEquals(
            "bibleView.emit(\"bookmark_note_modified\", {\"id\": \"b1\", \"lastUpdatedOn\": 99, \"notes\": null})",
            e.bookmarkNoteModified("b1", 99L, null).js,
        )
        assertEquals("bibleView.emit(\"update_labels\", [{\"id\":\"l\"}])", e.updateLabels(JsExpr("[{\"id\":\"l\"}]")).js)
        assertEquals("bibleView.emit(\"delete_labels\", [\"l\"])", e.deleteLabels(JsExpr("[\"l\"]")).js)
        assertEquals("bibleView.emit(\"delete_bookmarks\", [\"b\"])", e.deleteBookmarks(JsExpr("[\"b\"]")).js)
        assertEquals("bibleView.emit(\"add_or_update_bookmarks\", [])", e.addOrUpdateBookmarks(JsExpr("[]")).js)
        assertEquals("bibleView.emit(\"add_or_update_bookmark_to_label\", {})", e.addOrUpdateBookmarkToLabel(JsExpr("{}")).js)
        assertEquals(
            "bibleView.emit(\"add_or_update_study_pad\", {\"studyPadTextEntry\": null, \"bookmarkToLabelsOrdered\": [], \"genericBookmarkToLabelsOrdered\": [], \"studyPadItemsOrdered\": []})",
            e.addOrUpdateStudyPad(JsExpr("null"), JsExpr("[]"), JsExpr("[]"), JsExpr("[]")).js,
        )
        assertEquals("bibleView.emit(\"add_or_update_ai_doc_markers\", [])", e.addOrUpdateAiDocMarkers(JsExpr("[]")).js)
        assertEquals("bibleView.emit(\"delete_ai_doc_markers\", [\"p1\"])", e.deleteAiDocMarkers(listOf("p1")).js)
    }

    @Test fun documentAndConfigEvents() {
        assertEquals("bibleView.emit(\"add_documents\", {d: 1})", e.addDocuments(JsExpr("{d: 1}")).js)
        assertEquals("bibleView.emit(\"set_config\", {c: 1})", e.setConfig(JsExpr("{c: 1}")).js)
        assertEquals("bibleView.emit(\"set_title\", \"BibleView-3\")", e.setTitle("BibleView-3").js)
        assertEquals(
            "bibleView.emit(\"reload_addons\", {\"fontModuleNames\": [\"f\"], \"featureModuleNames\": [], \"styleModuleNames\": [\"s\"]})",
            e.reloadAddons(listOf("f"), emptyList(), listOf("s")).js,
        )
        assertEquals(
            "bibleView.emit(\"setup_content\", {\"jumpToOrdinal\": 5, \"jumpToAnchor\": null, \"jumpToId\": \"o-5\", \"topOffset\": 10.0, \"bottomOffset\": 0.0, \"ordinalStart\": null, \"ordinalEnd\": null, \"highlight\": false, \"bookInitials\": \"KJV\", \"osisRef\": \"Gen.1.5\"})",
            e.setupContent(SetupContent(5, null, "o-5", 10f, 0f, null, null, false, "KJV", "Gen.1.5")).js,
        )
    }

    @Test fun joinedCallsRunInOrder() {
        assertEquals("bibleView.emit(\"clear_document\");\nbibleView.emit(\"scroll_up\")", (e.clearDocument() + e.scrollUp()).js)
    }

    @Test fun namesListsExactlyTheThirtyOneEvents() {
        assertEquals(31, BibleViewEvents.names.size)
        val emitted = listOf(
            e.setActionMode(true), e.scrollToVerse(null, true, true, null, null), e.setOffsets(0f, 0f, false, false),
            e.bookmarkClicked("b", null), e.updateLabels(JsExpr("[]")), e.updateChapterReadStatus(1, 1),
            e.setConfig(JsExpr("{}")), e.setActive(true, true), e.scrollUp(), e.scrollDown(), e.resetLoadingCount(),
            e.removeRanges(), e.reloadAddons(emptyList(), emptyList(), emptyList()), e.exportHtml(),
            e.deleteLabels(JsExpr("[]")), e.deleteBookmarks(JsExpr("[]")), e.closeModals(),
            e.updateReadingProgressSettings(JsExpr("{}")), e.updateMemorizationData(emptyList(), emptyList(), emptyList(), emptyList()),
            e.setupContent(SetupContent(null, null, null, 0f, 0f, null, null, false, null, null)), e.setTitle("t"),
            e.deleteStudyPadTextEntry("x"), e.deleteAiDocMarkers(emptyList()), e.clearDocument(),
            e.bookmarkNoteModified("b", 1, null), e.adjustLoadingCount(1),
            e.addOrUpdateStudyPad(JsExpr("null"), JsExpr("[]"), JsExpr("[]"), JsExpr("[]")),
            e.addOrUpdateBookmarks(JsExpr("[]")), e.addOrUpdateBookmarkToLabel(JsExpr("{}")),
            e.addOrUpdateAiDocMarkers(JsExpr("[]")), e.addDocuments(JsExpr("{}")),
        ).map { Regex("^bibleView\\.emit\\(\"([a-z_]+)\"").find(it.js)!!.groupValues[1] }.toSet()
        assertEquals(BibleViewEvents.names, emitted)
    }
}
