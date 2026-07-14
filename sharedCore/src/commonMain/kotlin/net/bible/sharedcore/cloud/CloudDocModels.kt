package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory

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
)

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
