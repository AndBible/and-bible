package net.bible.sharedcore.platform.files

/** A wildcard requests generic data; concrete MIME resolution belongs to the platform. */
internal fun pickerMimeType(mime: String): String? = when (mime) {
    "*/*", "application/*" -> null
    else -> mime
}
