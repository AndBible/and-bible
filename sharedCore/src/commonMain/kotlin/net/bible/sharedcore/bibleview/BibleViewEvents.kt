package net.bible.sharedcore.bibleview

/** Arguments of `setup_content` (sent once per document load by `BibleView.replaceDocument`). */
data class SetupContent(
    val jumpToOrdinal: Int?,
    val jumpToAnchor: Int?,
    val jumpToId: String?,
    val topOffset: Float,
    val bottomOffset: Float,
    val ordinalStart: Int?,
    val ordinalEnd: Int?,
    val highlight: Boolean,
    val bookInitials: String?,
    val osisRef: String?,
)

/**
 * Every native→JS BibleView event as a typed function (L2a spec §3.2 item 5). Key presence matches
 * what `BibleView.kt` sent before L2a (R3): keys omitted then are omitted here, keys sent as `null`
 * are sent as `null`. Names must exist in `app/bibleview-js/src/eventbus.ts` (`EventTypeNames`);
 * `:app`'s `BibleViewEventNamesParityTest` checks that.
 */
object BibleViewEvents {
    val names: Set<String> = setOf(
        "set_action_mode", "scroll_to_verse", "set_offsets", "bookmark_clicked", "update_labels",
        "update_chapter_read_status", "set_config", "set_active", "scroll_up", "scroll_down",
        "reset_loading_count", "remove_ranges", "reload_addons", "export_html", "delete_labels",
        "delete_bookmarks", "close_modals", "update_reading_progress_settings", "update_memorization_data",
        "setup_content", "set_title", "delete_study_pad_text_entry", "delete_ai_doc_markers", "clear_document",
        "bookmark_note_modified", "adjust_loading_count", "add_or_update_study_pad", "add_or_update_bookmarks",
        "add_or_update_bookmark_to_label", "add_or_update_ai_doc_markers", "add_documents",
    )

    private fun emit(event: String, vararg args: String): JsCall =
        JsCall((listOf(jsString(event)) + args).joinToString(", ", prefix = "bibleView.emit(", postfix = ")"))

    private fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(", ", prefix = "{", postfix = "}") { (k, v) -> "${jsString(k)}: $v" }

    private fun num(v: Number?): String = v?.toString() ?: "null"
    private fun ints(v: List<Int>): String = v.joinToString(",", prefix = "[", postfix = "]")
    private fun strings(v: List<String>): String = v.joinToString(",", prefix = "[", postfix = "]") { jsString(it) }

    fun response(callId: Long, value: JsExpr?): JsCall = JsCall("bibleView.response($callId, ${value?.js ?: "null"})")
    fun responseString(callId: Long, value: String): JsCall = JsCall("bibleView.response($callId, ${jsString(value)})")

    fun setActionMode(enabled: Boolean) = emit("set_action_mode", enabled.toString())

    fun scrollToVerse(jumpToId: String?, now: Boolean, highlight: Boolean, ordinalStart: Int?, ordinalEnd: Int?) =
        emit("scroll_to_verse", jsString(jumpToId), obj(
            "now" to now.toString(), "highlight" to highlight.toString(),
            "ordinalStart" to num(ordinalStart), "ordinalEnd" to num(ordinalEnd),
        ))

    /** `scroll_to_verse` for a non-Bible document or a highlight that names its document. */
    fun scrollToVerseIn(
        jumpToId: String?, now: Boolean, highlight: Boolean,
        bookInitials: String?, osisRef: String?, ordinalStart: Int?, ordinalEnd: Int?,
    ) = emit("scroll_to_verse", jsString(jumpToId), obj(
        "now" to now.toString(), "highlight" to highlight.toString(),
        "bookInitials" to jsString(bookInitials), "osisRef" to jsString(osisRef),
        "ordinalStart" to num(ordinalStart), "ordinalEnd" to num(ordinalEnd),
    ))

    fun setOffsets(top: Float, bottom: Float, immediate: Boolean, imeOpen: Boolean) =
        emit("set_offsets", num(top), num(bottom), obj("immediate" to immediate.toString(), "imeOpen" to imeOpen.toString()))

    fun setOffsetsWithoutScroll(top: Float, bottom: Float) =
        emit("set_offsets", num(top), num(bottom), obj("doNotScroll" to "true"))

    fun bookmarkClicked(bookmarkId: String, openNotes: Boolean?) = emit(
        "bookmark_clicked", jsString(bookmarkId),
        if (openNotes == null) obj("openLabels" to "true") else obj("openLabels" to "true", "openNotes" to openNotes.toString()),
    )

    fun updateLabels(labelsArray: JsExpr) = emit("update_labels", labelsArray.js)
    fun updateChapterReadStatus(chapter: Int, count: Int) =
        emit("update_chapter_read_status", obj("chapter" to num(chapter), "count" to num(count)))
    fun setConfig(payload: JsExpr) = emit("set_config", payload.js)
    fun setActive(isActive: Boolean, hasActiveIndicator: Boolean) =
        emit("set_active", obj("isActive" to isActive.toString(), "hasActiveIndicator" to hasActiveIndicator.toString()))
    fun scrollUp() = emit("scroll_up")
    fun scrollDown() = emit("scroll_down")
    fun resetLoadingCount() = emit("reset_loading_count")
    fun removeRanges() = emit("remove_ranges")
    fun closeModals() = emit("close_modals")
    fun exportHtml() = emit("export_html")
    fun clearDocument() = emit("clear_document")

    fun reloadAddons(fontModuleNames: List<String>, featureModuleNames: List<String>, styleModuleNames: List<String>) =
        emit("reload_addons", obj(
            "fontModuleNames" to strings(fontModuleNames),
            "featureModuleNames" to strings(featureModuleNames),
            "styleModuleNames" to strings(styleModuleNames),
        ))

    fun deleteLabels(ids: JsExpr) = emit("delete_labels", ids.js)
    fun deleteBookmarks(ids: JsExpr) = emit("delete_bookmarks", ids.js)
    fun updateReadingProgressSettings(settings: JsExpr) = emit("update_reading_progress_settings", settings.js)

    fun updateMemorizationData(addedMemorized: List<Int>, removedMemorized: List<Int>, addedTargets: List<Int>, removedTargets: List<Int>) =
        emit("update_memorization_data", obj(
            "addedMemorized" to ints(addedMemorized), "removedMemorized" to ints(removedMemorized),
            "addedTargets" to ints(addedTargets), "removedTargets" to ints(removedTargets),
        ))

    fun setupContent(c: SetupContent) = emit("setup_content", obj(
        "jumpToOrdinal" to num(c.jumpToOrdinal), "jumpToAnchor" to num(c.jumpToAnchor), "jumpToId" to jsString(c.jumpToId),
        "topOffset" to num(c.topOffset), "bottomOffset" to num(c.bottomOffset),
        "ordinalStart" to num(c.ordinalStart), "ordinalEnd" to num(c.ordinalEnd), "highlight" to c.highlight.toString(),
        "bookInitials" to jsString(c.bookInitials), "osisRef" to jsString(c.osisRef),
    ))

    fun setTitle(title: String) = emit("set_title", jsString(title))
    fun deleteStudyPadTextEntry(id: String) = emit("delete_study_pad_text_entry", jsString(id))
    fun deleteAiDocMarkers(pageIds: List<String>) = emit("delete_ai_doc_markers", strings(pageIds))

    fun bookmarkNoteModified(id: String, lastUpdatedOn: Long, notes: String?) = emit("bookmark_note_modified", obj(
        "id" to jsString(id), "lastUpdatedOn" to lastUpdatedOn.toString(), "notes" to jsString(notes),
    ))

    fun adjustLoadingCount(adj: Int) = emit("adjust_loading_count", adj.toString())

    fun addOrUpdateStudyPad(
        studyPadTextEntry: JsExpr, bookmarkToLabelsOrdered: JsExpr,
        genericBookmarkToLabelsOrdered: JsExpr, studyPadItemsOrdered: JsExpr,
    ) = emit("add_or_update_study_pad", obj(
        "studyPadTextEntry" to studyPadTextEntry.js,
        "bookmarkToLabelsOrdered" to bookmarkToLabelsOrdered.js,
        "genericBookmarkToLabelsOrdered" to genericBookmarkToLabelsOrdered.js,
        "studyPadItemsOrdered" to studyPadItemsOrdered.js,
    ))

    fun addOrUpdateBookmarks(bookmarks: JsExpr) = emit("add_or_update_bookmarks", bookmarks.js)
    fun addOrUpdateBookmarkToLabel(bookmarkToLabel: JsExpr) = emit("add_or_update_bookmark_to_label", bookmarkToLabel.js)
    fun addOrUpdateAiDocMarkers(markers: JsExpr) = emit("add_or_update_ai_doc_markers", markers.js)
    fun addDocuments(documents: JsExpr) = emit("add_documents", documents.js)
}
