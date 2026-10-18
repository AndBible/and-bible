package net.bible.sharedui.app.ports

/** Plurals and locale file-size formatting, which [net.bible.sharedui.strings.Strings] does not support. */
interface PlatformText {
    fun formatFileSize(bytes: Long): String
    fun plural(key: PluralKey, count: Int, vararg args: Any): String
}

enum class PluralKey {
    CLOUD_DOC_SYNC_NOW_COUNT,
    CLOUD_DOC_SYNC_NOW_COUNT_SIZE,
    CLOUD_DOC_BULK_BLOCK_CONFIRM,
    /** `cloud_doc_bulk_remove_all_confirm`: removal from the cloud and every device (document sync on). */
    CLOUD_DOC_BULK_REMOVE_CONFIRM,
    /** `cloud_doc_bulk_remove_cloud_confirm`: removal from the cloud only (document sync off). */
    CLOUD_DOC_BULK_REMOVE_CLOUD_CONFIRM,
    CLOUD_DOC_BULK_PURGE_CONFIRM,
}
