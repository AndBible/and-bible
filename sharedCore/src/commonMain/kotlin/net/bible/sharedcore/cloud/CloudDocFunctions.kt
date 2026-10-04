package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory
import net.bible.sharedcore.navigation.matchesDocumentQuery

/**
 * Keeps items matching the status, (when non-null) category, and [nameQuery] — matched as a
 * token-prefix query (via [matchesDocumentQuery], case-insensitive, Unicode-aware) over BOTH
 * [CloudDocItem.initials] and [CloudDocItem.name], not a plain name substring.
 */
fun filterCloudDocuments(
    items: List<CloudDocItem>,
    status: CloudDocFilter,
    nameQuery: String,
    category: DocCategory?,
): List<CloudDocItem> {
    return items.filter { item ->
        val statusOk = when (status) {
            CloudDocFilter.ALL -> true
            CloudDocFilter.INSTALLED -> !item.cloudOnly && !item.cloudDeleted
            CloudDocFilter.CLOUD -> !item.localOnly && !item.cloudDeleted
            CloudDocFilter.UPDATES -> item.updateAvailable && !item.cloudDeleted
            CloudDocFilter.BLOCKED -> item.blocked && !item.cloudDeleted
            CloudDocFilter.DEVICE_ONLY -> item.localOnly && !item.cloudDeleted
            CloudDocFilter.CLOUD_ONLY -> item.cloudOnly && !item.cloudDeleted
            CloudDocFilter.REMOVED -> item.cloudDeleted
        }
        // Round 17e-2: the SHARED matcher, over initials AND name. It used to be
        // `item.name.contains(query, ignoreCase = true)`, which could not find a document by the
        // abbreviation — the exact defect reported. Initials first because that is the shorter,
        // more distinctive field.
        val nameOk = matchesDocumentQuery(nameQuery, listOf(item.initials, item.name))
        val categoryOk = category == null || item.category == category
        statusOk && nameOk && categoryOk
    }
}

/** Per-item actions relevant to a document's current sync status, in display order. */
fun documentMenuActions(item: CloudDocItem, syncEnabled: Boolean): List<CloudDocAction> = buildList {
    if (item.cloudDeleted) {
        if (item.localOnly) add(CloudDocAction.RESTORE)
        add(CloudDocAction.PURGE)
        return@buildList
    }
    if (item.cloudOnly || item.updateAvailable) add(CloudDocAction.DOWNLOAD)
    // F74 (fix batch 3 §2.2.3): not for a device-only BLOCKED document -- its "Sync to cloud"
    // (UNBLOCK, see [pushOnUnblock]) already pushes, and a bare PUSH would upload it and leave it
    // blocked, with two indistinguishable labels side by side.
    if ((item.localOnly || item.localNewer) && !(item.localOnly && item.blocked)) add(CloudDocAction.PUSH)
    if (!item.localOnly && !(syncEnabled && !item.canDeleteLocal)) add(CloudDocAction.REMOVE_CLOUD)
    if (item.blocked) add(CloudDocAction.UNBLOCK) else add(CloudDocAction.BLOCK)
}

/** Union of [documentMenuActions] over [selected], in canonical declaration order. */
fun bulkMenuActions(selected: List<CloudDocItem>, syncEnabled: Boolean): List<CloudDocAction> {
    val supported = selected.flatMapTo(mutableSetOf()) { documentMenuActions(it, syncEnabled) }
    return CloudDocAction.entries.filter { it in supported }
}

/** Initials of the [selected] items that support [action] — the exact subset a bulk action runs on. */
fun applicableInitials(action: CloudDocAction, selected: List<CloudDocItem>, syncEnabled: Boolean): List<String> =
    selected.filter { action in documentMenuActions(it, syncEnabled) }.map { it.initials }

/**
 * F74 (fix batch 3 §2.2.3): which of [initials] an UNBLOCK must also PUSH. A device-only document's
 * UNBLOCK is labelled "Sync to cloud" (`actionLabelKind`'s `ALLOW_SYNC`), so it must upload the
 * document as well; a document already in the cloud is only unblocked (allowed to download here).
 */
fun pushOnUnblock(items: List<CloudDocItem>, initials: List<String>): List<String> =
    initials.filter { id -> items.firstOrNull { it.initials == id }?.localOnly == true }

/** Expected list state right after a remove, applied optimistically. */
fun applyOptimisticRemoval(items: List<CloudDocItem>, initials: String, syncEnabled: Boolean): List<CloudDocItem> {
    val item = items.firstOrNull { it.initials == initials } ?: return items
    return if (syncEnabled || item.cloudOnly) {
        items.filterNot { it.initials == initials }
    } else {
        items.map {
            if (it.initials == initials)
                it.copy(cloudOnly = false, localOnly = true, cloudVersion = null, updateAvailable = false, localNewer = false)
            else it
        }
    }
}

/** Expected list state right after purging a tombstone, applied optimistically. */
fun applyOptimisticPurge(items: List<CloudDocItem>, initials: String): List<CloudDocItem> {
    val item = items.firstOrNull { it.initials == initials } ?: return items
    return if (item.localOnly) {
        items.map { if (it.initials == initials) it.copy(cloudDeleted = false, cloudVersion = null) else it }
    } else {
        items.filterNot { it.initials == initials }
    }
}
