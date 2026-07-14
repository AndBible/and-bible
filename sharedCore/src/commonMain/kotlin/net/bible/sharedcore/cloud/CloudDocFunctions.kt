package net.bible.sharedcore.cloud

import net.bible.sharedcore.navigation.DocCategory

/** Keeps items matching the status, a case-insensitive name substring, and (when non-null) category. */
fun filterCloudDocuments(
    items: List<CloudDocItem>,
    status: CloudDocFilter,
    nameQuery: String,
    category: DocCategory?,
): List<CloudDocItem> {
    val query = nameQuery.trim()
    return items.filter { item ->
        val statusOk = when (status) {
            CloudDocFilter.ALL -> !item.cloudDeleted
            CloudDocFilter.INSTALLED -> !item.cloudOnly && !item.cloudDeleted
            CloudDocFilter.CLOUD -> !item.localOnly && !item.cloudDeleted
            CloudDocFilter.UPDATES -> item.updateAvailable && !item.cloudDeleted
            CloudDocFilter.BLOCKED -> item.blocked && !item.cloudDeleted
            CloudDocFilter.DEVICE_ONLY -> item.localOnly && !item.cloudDeleted
            CloudDocFilter.CLOUD_ONLY -> item.cloudOnly && !item.cloudDeleted
            CloudDocFilter.REMOVED -> item.cloudDeleted
        }
        val nameOk = query.isEmpty() || item.name.contains(query, ignoreCase = true)
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
    if (item.localOnly || item.localNewer) add(CloudDocAction.PUSH)
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
