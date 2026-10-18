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
    CLOUD_DOC_BULK_REMOVE_CONFIRM,
    CLOUD_DOC_BULK_PURGE_CONFIRM,
}
