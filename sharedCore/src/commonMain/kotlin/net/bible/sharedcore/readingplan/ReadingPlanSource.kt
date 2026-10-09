package net.bible.sharedcore.readingplan

/**
 * Where the reading plans bundled with the app come from (Android: the `readingplan` assets folder).
 * User-installed plans are files and add-on modules, which the domain reads itself.
 */
interface ReadingPlanSource {
    /** Codes (file name without `.properties`) of every bundled plan. */
    fun builtInPlanCodes(): List<String>

    /**
     * The bundled plan file for [code], or null if there is none.
     *
     * The text is the file's bytes decoded as ISO-8859-1 (one char per byte, lossless). That is the
     * encoding `java.util.Properties.load(InputStream)` assumes, and it lets the reader recover the
     * exact bytes to parse the same file as a properties file and as UTF-8 header comments.
     */
    fun openBuiltInPlan(code: String): String?
}
