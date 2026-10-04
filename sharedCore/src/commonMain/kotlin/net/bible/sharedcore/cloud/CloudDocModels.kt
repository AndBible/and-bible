package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.DocSortable

/** Per-item action that can be triggered from a row's overflow menu (moved from the classic adapter). */
enum class CloudDocAction { DOWNLOAD, PUSH, REMOVE_CLOUD, BLOCK, UNBLOCK, RESTORE, PURGE }

/** The status filter of the cloud management view (moved from the classic activity). */
enum class CloudDocFilter { ALL, INSTALLED, CLOUD, UPDATES, BLOCKED, DEVICE_ONLY, CLOUD_ONLY, REMOVED }

/**
 * Framework-free view of one syncable document, the portable analogue of
 * `DocumentSync.DocumentStatusItem`. The host flattens the service item into this (formatting
 * [sizeLabel] and mapping `BookCategory` → [DocCategory]); the controller and pure functions
 * operate only on this shape.
 */
data class CloudDocItem(
    val initials: String,
    val name: String,
    val category: DocCategory?,
    val cloudVersion: String?,
    val localVersion: String?,
    val cloudOnly: Boolean,
    val localOnly: Boolean,
    val updateAvailable: Boolean,
    val localNewer: Boolean,
    val blocked: Boolean,
    val canDeleteLocal: Boolean,
    val cloudDeleted: Boolean,
    /** Pre-formatted install/cloud size (host uses Android's Formatter); null when unknown/zero. */
    val sizeLabel: String?,
    /**
     * The raw size behind [sizeLabel]. Both are carried because they answer different questions:
     * the label is what the row prints, and only the number can be SORTED — ordering "1.2 MB" and
     * "900 kB" as text puts the smaller file first. `DocumentSync.DocumentStatusItem.sizeBytes`
     * has always had this value; it was simply discarded into the label.
     */
    val sizeBytes: Long? = null,
) : DocSortable {
    // The cloud row's headline is the NAME alone (unlike the download row, which leads with the
    // abbreviation), so "sort by name" must lead with the name here or the visible order would
    // not match the chosen criterion.
    override val sortName: String get() = name
    override val sortSecondaryName: String get() = initials
    override val sortCategory: DocCategory? get() = category
    // A cloud listing carries neither: DocumentSync knows a document's sync state, not the
    // repository it once came from. The screen therefore does not offer these criteria at all.
    override val sortLanguage: String? get() = null
    override val sortRepository: String? get() = null
    override val sortSizeBytes: Long? get() = sizeBytes
    override val sortStatusRank: Int get() = cloudDocStatusRank(this)
    // "Recommended" is a download-repository concept; nothing marks a synced document as such.
    override val sortRecommended: Boolean get() = false
}

/** The displayed status of a row (icon + label). Pure port of the classic `statusText` when-order. */
enum class CloudDocStatus { SYNCED, LOCAL_ONLY, CLOUD_ONLY, UPDATE, BLOCKED, WONT_SYNC, REMOVED, REMOVED_STILL_INSTALLED }

fun cloudDocStatus(item: CloudDocItem): CloudDocStatus = when {
    item.cloudDeleted -> if (item.localOnly) CloudDocStatus.REMOVED_STILL_INSTALLED else CloudDocStatus.REMOVED
    item.blocked -> if (item.localOnly) CloudDocStatus.WONT_SYNC else CloudDocStatus.BLOCKED
    item.updateAvailable -> CloudDocStatus.UPDATE
    item.cloudOnly -> CloudDocStatus.CLOUD_ONLY
    item.localOnly -> CloudDocStatus.LOCAL_ONLY
    else -> CloudDocStatus.SYNCED
}

/** The label a per-item action shows, context-sensitive. Pure port of `actionLabelRes` → an enum. */
enum class CloudDocActionLabel { DOWNLOAD, PUSH, REMOVE_CLOUD, REMOVE_ALL_DEVICES, BLOCK, DONT_SYNC, UNBLOCK, ALLOW_SYNC, RESTORE, PURGE }

fun actionLabelKind(action: CloudDocAction, localOnly: Boolean, syncEnabled: Boolean): CloudDocActionLabel = when (action) {
    CloudDocAction.DOWNLOAD -> CloudDocActionLabel.DOWNLOAD
    CloudDocAction.PUSH -> CloudDocActionLabel.PUSH
    CloudDocAction.REMOVE_CLOUD -> if (syncEnabled) CloudDocActionLabel.REMOVE_ALL_DEVICES else CloudDocActionLabel.REMOVE_CLOUD
    CloudDocAction.BLOCK -> if (localOnly) CloudDocActionLabel.DONT_SYNC else CloudDocActionLabel.BLOCK
    CloudDocAction.UNBLOCK -> if (localOnly) CloudDocActionLabel.ALLOW_SYNC else CloudDocActionLabel.UNBLOCK
    CloudDocAction.RESTORE -> CloudDocActionLabel.RESTORE
    CloudDocAction.PURGE -> CloudDocActionLabel.PURGE
}

/**
 * The sync-status rank used for sorting and for status grouping. It is [CloudDocStatus]'s own
 * declaration order, which already reads worst-news-first (synced, local only, cloud only, update,
 * blocked, won't sync, removed) — the same order the status filter offers.
 */
fun cloudDocStatusRank(item: CloudDocItem): Int = cloudDocStatus(item).ordinal
