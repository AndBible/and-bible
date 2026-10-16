package net.bible.android.view.activity.bookmark

import kotlinx.serialization.Serializable
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.LabelFilter
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsSnapshot
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedcore.bookmark.SearchMode

@Serializable
internal data class MyDocumentsSearchValue(val query: String, val searchOpen: Boolean, val selectedInitials: Set<String>) {
    fun toSnapshot() = net.bible.sharedcore.mydocuments.MyDocumentsSearchSnapshot(query, searchOpen, selectedInitials)
}

/** Pages screen search + selection; [selectedPageIds] are persistent page ids, not load-order tokens. */
@Serializable
internal data class MyDocumentPagesValue(
    val documentId: String,
    val query: String,
    val searchOpen: Boolean,
    val selectedPageIds: Set<String>,
)

/** CloudDocuments type/status filters. */
@Serializable
internal data class CloudFiltersValue(
    val status: net.bible.sharedcore.cloud.CloudDocFilter,
    val category: net.bible.sharedcore.navigation.DocCategory?,
) {
    fun toSnapshot() = net.bible.sharedcore.cloud.CloudDocumentsFilterSnapshot(status, category)
}

@Serializable
internal data class BookmarksValue(
    val filter: Int,
    val query: String,
    val searchOpen: Boolean,
    val selection: Set<String>,
    val expanded: Set<String>,
) {
    fun toSnapshot() = net.bible.sharedcore.bookmark.BookmarksSnapshot(filter, query, searchOpen, selection, expanded)
    companion object {
        fun from(snapshot: net.bible.sharedcore.bookmark.BookmarksSnapshot) = with(snapshot) {
            BookmarksValue(filter, query, searchOpen, selection, expanded)
        }
    }
}

/** Android saved-state wire values. Shared controllers do not depend on generated serializers. */
@Serializable
internal data class LabelDraftValue(
    val labelId: String,
    val name: String,
    val color: Int,
    val customIcon: String?,
    val selectionStyle: BookmarkDisplayStyle,
    val wholeVerseStyle: BookmarkDisplayStyle?,
    val favourite: Boolean,
    val isAssigning: Boolean,
    val thisBookmarkSelected: Boolean,
    val thisBookmarkPrimary: Boolean,
    val hasWorkspaceContext: Boolean,
    val autoAssign: Boolean,
    val autoAssignPrimary: Boolean,
    val overrideMode: OverrideMode,
    val isSpecialLabel: Boolean,
    val isSpeakLabel: Boolean,
) {
    fun toState() = LabelEditState(
        labelId, name, color, customIcon, selectionStyle, wholeVerseStyle, favourite, isAssigning,
        thisBookmarkSelected, thisBookmarkPrimary, hasWorkspaceContext, autoAssign, autoAssignPrimary,
        overrideMode, isSpecialLabel, isSpeakLabel,
    )

    companion object {
        fun from(state: LabelEditState) = with(state) {
            LabelDraftValue(
                labelId, name, color, customIcon, selectionStyle, wholeVerseStyle, favourite, isAssigning,
                thisBookmarkSelected, thisBookmarkPrimary, hasWorkspaceContext, autoAssign, autoAssignPrimary,
                overrideMode, isSpecialLabel, isSpeakLabel,
            )
        }
    }
}

@Serializable
internal data class LabelItemValue(
    val id: String,
    val name: String,
    val color: Int,
    val favourite: Boolean,
    val isUnlabeled: Boolean,
    val isSpecial: Boolean,
    val customIcon: String?,
    val selectionStyle: BookmarkDisplayStyle,
    val wholeVerseStyle: BookmarkDisplayStyle?,
    val overrideStyle: BookmarkDisplayStyle?,
) {
    fun toItem() = LabelItem(id, name, color, favourite, isUnlabeled, isSpecial, customIcon,
        selectionStyle, wholeVerseStyle, overrideStyle)

    companion object {
        fun from(item: LabelItem) = with(item) {
            LabelItemValue(id, name, color, favourite, isUnlabeled, isSpecial, customIcon,
                selectionStyle, wholeVerseStyle, overrideStyle)
        }
    }
}

@Serializable
internal data class ManageLabelsValue(
    val selected: Set<String>,
    val autoAssign: Set<String>,
    val changed: Set<String>,
    val deleted: Set<String>,
    val deletedWithOrphaned: Set<String>,
    val autoAssignPrimary: String?,
    val bookmarkPrimary: String?,
    val labels: List<LabelItemValue>,
    val search: String,
    val searchMode: SearchMode,
    val filters: Set<LabelFilter>,
    val searchOpen: Boolean,
) {
    fun toSnapshot() = ManageLabelsSnapshot(
        selected, autoAssign, changed, deleted, deletedWithOrphaned, autoAssignPrimary, bookmarkPrimary,
        labels.map { it.toItem() }, search, searchMode, filters, searchOpen,
    )

    companion object {
        fun from(snapshot: ManageLabelsSnapshot) = with(snapshot) {
            ManageLabelsValue(
                selected, autoAssign, changed, deleted, deletedWithOrphaned, autoAssignPrimary, bookmarkPrimary,
                labels.map(LabelItemValue::from), search, searchMode, filters, searchOpen,
            )
        }
    }
}
