package net.bible.sharedcore.webdav

/** Input validation for the WebDAV server URL setting: HTTPS only, a host, no whitespace. */
object WebDavUrl {
    private val PATTERN = Regex("""^https://[^/\s:?#]+(:\d{1,5})?(/\S*)?$""", RegexOption.IGNORE_CASE)
    fun validate(url: String): Boolean = PATTERN.matches(url)
}
