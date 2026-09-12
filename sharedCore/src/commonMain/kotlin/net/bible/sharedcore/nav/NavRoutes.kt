package net.bible.sharedcore.nav

/**
 * Routes for the Compose navigation graph (`:sharedUi`'s `NavHost`), and the typed builders that
 * are the ONLY sanctioned way to construct one.
 *
 * **Strings, deliberately (plan D1).** Type-safe `@Serializable` routes would require the
 * serialization plugin in `:sharedCore`, and this module is deliberately dependency-light — see
 * `download/CustomRepositoryModel.kt`, which declines `@Serializable` for the same reason. Every
 * live argument in the first migrated cluster is an id or a boolean, so the wire format costs
 * nothing. The typing is not lost, only moved: nothing should hand-concatenate a route, and
 * [encodeArg]/[decodeArg] exist so a free-text argument cannot corrupt one.
 *
 * Lives in `:sharedCore` rather than `:sharedUi` because it is pure data and this module already
 * has a `jvm()` target and a `commonTest` source set, so routes are testable on Linux in seconds.
 */
object NavRoutes {

    // Argument names. Each appears twice — in a PATTERN and in the read site — so both use these.
    const val ARG_PROMPT_ID: String = "promptId"
    const val ARG_PROMPT_TEMPLATE: String = "template"
    const val ARG_DEFAULT_CONTEXT: String = "defaultContext"
    const val ARG_EXECUTE_AFTER_SAVE: String = "executeAfterSave"
    const val ARG_START_EASY_SETUP: String = "startEasySetup"
    const val ARG_LOG_RECORD_ID: String = "logRecordId"
    const val ARG_WORKSPACE_ID: String = "workspaceId"

    // Argument-free routes: pattern and instance are the same string.
    const val AI_PROMPTS: String = "ai/prompts"
    const val AI_CONNECTION_SETTINGS: String = "ai/connectionSettings"
    const val AI_MODELS: String = "ai/models"
    const val AI_DOCUMENT_FILTER: String = "ai/documentFilter"
    const val AI_GLOBAL_TOOL_PERMISSIONS: String = "ai/globalToolPermissions"
    const val AI_TOOL_INFO: String = "ai/toolInfo"
    const val AI_RAW_LOG_HISTORY: String = "ai/rawLogHistory"

    // Parameterised routes: the PATTERN is what `composable(route = …)` registers; the builder
    // below is what a caller navigates to.
    const val AI_PROVIDERS_PATTERN: String =
        "ai/providers?$ARG_START_EASY_SETUP={$ARG_START_EASY_SETUP}"
    const val PROMPT_EDIT_PATTERN: String =
        "ai/promptEdit?$ARG_PROMPT_ID={$ARG_PROMPT_ID}" +
            "&$ARG_PROMPT_TEMPLATE={$ARG_PROMPT_TEMPLATE}" +
            "&$ARG_DEFAULT_CONTEXT={$ARG_DEFAULT_CONTEXT}" +
            "&$ARG_EXECUTE_AFTER_SAVE={$ARG_EXECUTE_AFTER_SAVE}"
    const val RAW_LLM_LOG_PATTERN: String =
        "ai/rawLlmLog?$ARG_LOG_RECORD_ID={$ARG_LOG_RECORD_ID}&$ARG_WORKSPACE_ID={$ARG_WORKSPACE_ID}"

    fun aiProviders(startEasySetup: Boolean = false): String =
        "ai/providers?$ARG_START_EASY_SETUP=$startEasySetup"

    fun promptEdit(
        promptId: String? = null,
        template: String? = null,
        defaultContext: String? = null,
        executeAfterSave: Boolean = false,
    ): String = buildRoute("ai/promptEdit") {
        optional(ARG_PROMPT_ID, promptId)
        optional(ARG_PROMPT_TEMPLATE, template)
        optional(ARG_DEFAULT_CONTEXT, defaultContext)
        required(ARG_EXECUTE_AFTER_SAVE, executeAfterSave.toString())
    }

    fun rawLlmLog(logRecordId: String? = null, workspaceId: String? = null): String =
        buildRoute("ai/rawLlmLog") {
            optional(ARG_LOG_RECORD_ID, logRecordId)
            optional(ARG_WORKSPACE_ID, workspaceId)
        }

    // ——— slice 3: Reading plan ———
    const val ARG_PLAN: String = "plan"
    const val ARG_DAY: String = "day"

    const val READING_PLAN_SELECTOR: String = "readingPlan/selector"
    const val READING_PLAN_DAY_LIST: String = "readingPlan/dayList"
    const val DAILY_READING_PATTERN: String =
        "readingPlan/day?$ARG_PLAN={$ARG_PLAN}&$ARG_DAY={$ARG_DAY}"

    /**
     * Both arguments are OPTIONAL and their ABSENCE is meaningful: the classic host branched on
     * `extras.containsKey(...)`, not on null, and fell through to `readingPlanControl.currentPlanDay`
     * when neither key was present. Emitting `plan=` for a null plan would make the destination call
     * `setReadingPlan("")`, so a null argument is omitted from the route entirely.
     */
    fun dailyReading(plan: String? = null, day: Int? = null): String =
        buildRoute("readingPlan/day") {
            optional(ARG_PLAN, plan)
            optional(ARG_DAY, day?.toString())
        }

    /**
     * The INVERSE of [dailyReading]: [ARG_PLAN] and [ARG_DAY] read back off a route string this
     * object built. Its one caller is `NavHostComposeActivity.onNewIntent`, which has to reach
     * those values BEFORE the destination that would normally read them off its `NavBackStackEntry`
     * exists (or, when the re-navigation is a no-op, without it ever re-reading them).
     *
     * It lives HERE, next to the builder, rather than as a private helper on that Activity, because
     * emit and parse are one contract and only a pair that sits together can be tested as one — the
     * whole-branch review's M5. The hand-written parse produced a defect that needed four fix rounds
     * before it matched what the navigation library does; both of the things it got wrong are
     * asserted by `NavRoutesSlices356Test`'s round-trip cases now:
     *
     * - **[ARG_PLAN] must be [decodeArg]-ed**, and that is not a redundant belt on top of the
     *   destination's own read — it is the step this parser is missing and the destination gets for
     *   free. [dailyReading] percent-encodes every value (`RouteBuilder.optional` -> [encodeArg]),
     *   and the destination reaches its copy through the navigation library, whose
     *   `NavDeepLink.getMatchingQueryArguments` reads query values with `Uri.getQueryParameters` —
     *   which returns them ALREADY `Uri.decode`-ed. So the destination sees `My Plan` where a plain
     *   string split sees `My%20Plan`. Feeding the encoded form to `ReadingPlanControl.setReadingPlan`
     *   would write a non-existent plan code into the `READING_PLAN` preference and then throw — and
     *   plan codes are filenames (`ReadingPlanTextFileDao.userPlanCodes`,
     *   `AndBibleAddons.providedReadingPlans`), so a space or a non-ASCII character in one is
     *   ordinary, not exotic. [decodeArg]'s malformed-escape `require` cannot fire for a route this
     *   object built.
     * - **An EMPTY value counts as ABSENT**, matching the library: its query-parameter regex is
     *   `(.+?)`, so `plan=` does not match and the argument falls back to its `null` default —
     *   whereas `""` here would reach `setReadingPlan("")` and wipe the preference.
     *
     * [ARG_DAY] is digits, so it needs no decode; a non-numeric value reads as absent rather than
     * throwing, which is the same thing the library's `NavType.IntType` parse failure ends up doing
     * for a route nothing in this app can emit.
     */
    fun readDailyReading(route: String): Pair<String?, Int?> {
        val query = route.substringAfter('?', "")
        if (query.isEmpty()) return null to null
        val arguments = query.split("&")
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val plan = arguments[ARG_PLAN]?.takeIf { it.isNotEmpty() }?.let(::decodeArg)
        return plan to arguments[ARG_DAY]?.toIntOrNull()
    }

    // ——— slice 5: Search ———
    const val ARG_SEARCH_TEXT: String = "searchText"
    const val ARG_SEARCH_HIGHLIGHT_TEXT: String = "highlightText"
    const val ARG_SEARCH_DOCUMENT: String = "searchDocument"
    const val ARG_SELECTED_TRANSLATIONS: String = "selectedTranslations"
    const val ARG_IS_STRONGS_SEARCH: String = "isStrongsSearch"
    const val ARG_SEARCH_TYPE: String = "searchType"
    const val ARG_SEARCH_SECTION: String = "searchSection"
    const val ARG_BIBLE_BOOK: String = "bibleBook"
    const val ARG_EPUB_SEARCH_MODE: String = "epubMode"

    const val EPUB_SEARCH: String = "search/epub"
    const val SEARCH_FORM_PATTERN: String =
        "search/form?$ARG_SEARCH_TEXT={$ARG_SEARCH_TEXT}" +
            "&$ARG_SEARCH_TYPE={$ARG_SEARCH_TYPE}" +
            "&$ARG_SEARCH_SECTION={$ARG_SEARCH_SECTION}" +
            "&$ARG_BIBLE_BOOK={$ARG_BIBLE_BOOK}"
    const val SEARCH_RESULTS_PATTERN: String =
        "search/results?$ARG_SEARCH_TEXT={$ARG_SEARCH_TEXT}" +
            "&$ARG_SEARCH_HIGHLIGHT_TEXT={$ARG_SEARCH_HIGHLIGHT_TEXT}" +
            "&$ARG_SEARCH_DOCUMENT={$ARG_SEARCH_DOCUMENT}" +
            "&$ARG_SELECTED_TRANSLATIONS={$ARG_SELECTED_TRANSLATIONS}" +
            "&$ARG_IS_STRONGS_SEARCH={$ARG_IS_STRONGS_SEARCH}"
    const val SEARCH_INDEX_PATTERN: String =
        "search/index?$ARG_SEARCH_TEXT={$ARG_SEARCH_TEXT}" +
            "&$ARG_SEARCH_HIGHLIGHT_TEXT={$ARG_SEARCH_HIGHLIGHT_TEXT}" +
            "&$ARG_SEARCH_DOCUMENT={$ARG_SEARCH_DOCUMENT}" +
            "&$ARG_SELECTED_TRANSLATIONS={$ARG_SELECTED_TRANSLATIONS}" +
            "&$ARG_IS_STRONGS_SEARCH={$ARG_IS_STRONGS_SEARCH}"
    const val SEARCH_INDEX_PROGRESS_PATTERN: String =
        "search/indexProgress?$ARG_SEARCH_TEXT={$ARG_SEARCH_TEXT}" +
            "&$ARG_SEARCH_HIGHLIGHT_TEXT={$ARG_SEARCH_HIGHLIGHT_TEXT}" +
            "&$ARG_SEARCH_DOCUMENT={$ARG_SEARCH_DOCUMENT}" +
            "&$ARG_SELECTED_TRANSLATIONS={$ARG_SELECTED_TRANSLATIONS}" +
            "&$ARG_IS_STRONGS_SEARCH={$ARG_IS_STRONGS_SEARCH}"
    const val EPUB_SEARCH_RESULTS_PATTERN: String =
        "search/epubResults?$ARG_SEARCH_TEXT={$ARG_SEARCH_TEXT}" +
            "&$ARG_EPUB_SEARCH_MODE={$ARG_EPUB_SEARCH_MODE}" +
            "&$ARG_SEARCH_DOCUMENT={$ARG_SEARCH_DOCUMENT}"

    fun searchForm(
        searchText: String? = null,
        searchType: String? = null,
        searchSection: String? = null,
        bibleBook: String? = null,
    ): String = buildRoute("search/form") {
        optional(ARG_SEARCH_TEXT, searchText)
        optional(ARG_SEARCH_TYPE, searchType)
        optional(ARG_SEARCH_SECTION, searchSection)
        optional(ARG_BIBLE_BOOK, bibleBook)
    }

    fun searchResults(
        searchText: String,
        highlightText: String? = null,
        searchDocument: String? = null,
        selectedTranslations: List<String> = emptyList(),
        isStrongsSearch: Boolean = false,
    ): String = searchChainRoute("search/results", searchText, highlightText, searchDocument, selectedTranslations, isStrongsSearch)

    fun searchIndex(
        searchText: String? = null,
        highlightText: String? = null,
        searchDocument: String? = null,
        selectedTranslations: List<String> = emptyList(),
        isStrongsSearch: Boolean = false,
    ): String = searchChainRoute("search/index", searchText, highlightText, searchDocument, selectedTranslations, isStrongsSearch)

    fun searchIndexProgress(
        searchText: String? = null,
        highlightText: String? = null,
        searchDocument: String? = null,
        selectedTranslations: List<String> = emptyList(),
        isStrongsSearch: Boolean = false,
    ): String = searchChainRoute("search/indexProgress", searchText, highlightText, searchDocument, selectedTranslations, isStrongsSearch)

    fun epubSearchResults(searchText: String, epubMode: String? = null, searchDocument: String? = null): String =
        buildRoute("search/epubResults") {
            required(ARG_SEARCH_TEXT, searchText)
            optional(ARG_EPUB_SEARCH_MODE, epubMode)
            optional(ARG_SEARCH_DOCUMENT, searchDocument)
        }

    /**
     * The five arguments the classic `SearchIndex -> SearchIndexProgress -> SearchResults` chain
     * forwarded as an opaque `putExtras(intent)` bundle (plan D3). Naming them here is what makes
     * the chain's third hop keep the scope the first hop was given: a route has no bundle, so an
     * argument nobody names is an argument silently lost.
     */
    private fun searchChainRoute(
        base: String,
        searchText: String?,
        highlightText: String?,
        searchDocument: String?,
        selectedTranslations: List<String>,
        isStrongsSearch: Boolean,
    ): String = buildRoute(base) {
        optional(ARG_SEARCH_TEXT, searchText)
        optional(ARG_SEARCH_HIGHLIGHT_TEXT, highlightText)
        optional(ARG_SEARCH_DOCUMENT, searchDocument)
        optional(ARG_SELECTED_TRANSLATIONS, selectedTranslations.takeIf { it.isNotEmpty() }?.let(::encodeList))
        required(ARG_IS_STRONGS_SEARCH, isStrongsSearch.toString())
    }

    // ——— slice 2: Bookmarks + labels ———
    const val ARG_LABEL_NO: String = "labelNo"
    const val ARG_MANAGE_LABELS_DATA: String = "data"
    const val ARG_LABEL_DATA: String = "data"

    const val BOOKMARKS_PATTERN: String = "bookmarks/list?$ARG_LABEL_NO={$ARG_LABEL_NO}"
    const val MANAGE_LABELS_PATTERN: String =
        "bookmarks/manageLabels?$ARG_MANAGE_LABELS_DATA={$ARG_MANAGE_LABELS_DATA}"
    const val LABEL_EDIT_PATTERN: String = "bookmarks/labelEdit?$ARG_LABEL_DATA={$ARG_LABEL_DATA}"

    /**
     * [labelNo] is OPTIONAL and its ABSENCE is meaningful, same discipline as [dailyReading]:
     * classic `BookmarksComposeActivity.initialFilterIndex` (`BookmarksComposeActivity.kt:73-77`)
     * branches on `intent.extras?.containsKey(BookmarkControl.LABEL_NO_EXTRA)`, not on a default
     * value, so "no filter argument at all" must stay distinguishable from "filter present but
     * empty" — a null [labelNo] is omitted from the route rather than emitted as `labelNo=`.
     *
     * The host also CLAMPS a negative [labelNo] to 0 before using it as a filter index. That clamp
     * is deliberately NOT applied here: it is destination BEHAVIOUR, not route DATA, and belongs in
     * the arm that reads this route back (a later task), not in the builder that constructs it.
     */
    fun bookmarks(labelNo: Int? = null): String =
        buildRoute("bookmarks/list") { optional(ARG_LABEL_NO, labelNo?.toString()) }

    /**
     * [data] is the `ManageLabelsData` JSON string (`ManageLabelsContract.kt:43`) — that contract
     * type embeds Room entities (`BookmarkEntities.Label`) and cannot cross into this `commonMain`
     * module, so the route carries its JSON as an opaque, percent-encoded string instead.
     */
    fun manageLabels(data: String): String =
        buildRoute("bookmarks/manageLabels") { required(ARG_MANAGE_LABELS_DATA, data) }

    /**
     * [data] is the `LabelData` JSON string (`LabelEditContract.kt:31`), same shape and same reason
     * as [manageLabels].
     */
    fun labelEdit(data: String): String =
        buildRoute("bookmarks/labelEdit") { required(ARG_LABEL_DATA, data) }

    // ——— slice 6: Settings ———
    const val ARG_TAB: String = "tab"

    const val SETTINGS: String = "settings/app"
    const val SYNC_SETTINGS: String = "settings/sync"
    const val READING_PROGRESS_SETTINGS: String = "settings/readingProgress"
    const val READING_PROGRESS_PATTERN: String = "progress/reading?$ARG_TAB={$ARG_TAB}"

    fun readingProgress(tab: Int? = null): String =
        buildRoute("progress/reading") { optional(ARG_TAB, tab?.toString()) }

    // ——— slice 4: Documents + downloads ———
    const val ARG_DOCUMENT_ID: String = "documentId"
    const val ARG_DOCUMENT_INITIALS: String = "documentInitials"
    const val ARG_DOCUMENT_NAME: String = "documentName"
    const val ARG_FIRST_DOWNLOAD: String = "firstDownload"
    const val ARG_DOWNLOAD_RECOMMENDED: String = "downloadRecommended"
    const val ARG_DOWNLOAD_SEARCH: String = "search"
    const val ARG_DOWNLOAD_ADDONS: String = "addons"
    const val ARG_DOCUMENT_IDS: String = "documentIds"

    /**
     * NOT `"data"`. Slice 2 already has two `"data"`-valued constants — [ARG_MANAGE_LABELS_DATA]
     * and [ARG_LABEL_DATA] — because each carries its own destination's whole payload blob. This
     * one carries an id (plan D9), so it gets its own name. Do not "de-duplicate" the two above:
     * they are separate arguments of separate routes that happen to share a wire name.
     */
    const val ARG_REPOSITORY_ID: String = "repositoryId"

    const val MY_DOCUMENTS_PATTERN: String = "documents/list"
    const val MY_DOCUMENT_PAGES_PATTERN: String =
        "documents/pages?$ARG_DOCUMENT_ID={$ARG_DOCUMENT_ID}" +
            "&$ARG_DOCUMENT_INITIALS={$ARG_DOCUMENT_INITIALS}" +
            "&$ARG_DOCUMENT_NAME={$ARG_DOCUMENT_NAME}"
    const val DOWNLOAD_PATTERN: String =
        "documents/download?$ARG_FIRST_DOWNLOAD={$ARG_FIRST_DOWNLOAD}" +
            "&$ARG_DOWNLOAD_RECOMMENDED={$ARG_DOWNLOAD_RECOMMENDED}" +
            "&$ARG_DOWNLOAD_SEARCH={$ARG_DOWNLOAD_SEARCH}" +
            "&$ARG_DOWNLOAD_ADDONS={$ARG_DOWNLOAD_ADDONS}" +
            "&$ARG_DOCUMENT_IDS={$ARG_DOCUMENT_IDS}"
    const val CUSTOM_REPOSITORIES_PATTERN: String = "documents/repositories"
    const val CUSTOM_REPOSITORY_EDITOR_PATTERN: String =
        "documents/repositories/edit?$ARG_REPOSITORY_ID={$ARG_REPOSITORY_ID}"
    const val PROGRESS_STATUS_PATTERN: String = "documents/progress"
    const val CLOUD_DOCUMENTS_PATTERN: String = "documents/cloud"

    fun myDocuments(): String = MY_DOCUMENTS_PATTERN

    fun myDocumentPages(documentId: String, documentInitials: String, documentName: String): String =
        buildRoute("documents/pages") {
            required(ARG_DOCUMENT_ID, documentId)
            required(ARG_DOCUMENT_INITIALS, documentInitials)
            required(ARG_DOCUMENT_NAME, documentName)
        }

    /**
     * The download screen, with every argument its eight classic launch sites used to attach as an
     * Intent extra. A flag is emitted ONLY when true (plan D2): `download()` is the bare route, and
     * an arm reads a flag as `getStringOrNull(ARG_X) == "true"`.
     *
     * [firstDownload] is what `Screen.FirstDownload` used to mean — the enum value is gone, because
     * a route with arguments has no use for an alias (design §5).
     */
    fun download(
        firstDownload: Boolean = false,
        downloadRecommended: Boolean = false,
        search: String? = null,
        addons: Boolean = false,
        documentIds: String? = null,
    ): String = buildRoute("documents/download") {
        optional(ARG_FIRST_DOWNLOAD, if (firstDownload) "true" else null)
        optional(ARG_DOWNLOAD_RECOMMENDED, if (downloadRecommended) "true" else null)
        optional(ARG_DOWNLOAD_SEARCH, search)
        optional(ARG_DOWNLOAD_ADDONS, if (addons) "true" else null)
        optional(ARG_DOCUMENT_IDS, documentIds)
    }

    fun customRepositories(): String = CUSTOM_REPOSITORIES_PATTERN

    /**
     * [id] is `null` for a NEW repository, and the argument is then omitted entirely (plan D9) —
     * absent, not present-and-empty, so the arm never has to guess what the fork does with `=`.
     */
    fun customRepositoryEditor(id: Long?): String =
        buildRoute("documents/repositories/edit") { optional(ARG_REPOSITORY_ID, id?.toString()) }

    fun progressStatus(): String = PROGRESS_STATUS_PATTERN

    fun cloudDocuments(): String = CLOUD_DOCUMENTS_PATTERN

    /**
     * Joins a list into ONE route argument (plan D4). The join happens before [encodeArg] runs over
     * the whole string, so a member containing a comma would still round-trip as two members — that
     * is acceptable because every live caller passes document initials, which cannot contain one.
     */
    fun encodeList(values: List<String>): String = values.joinToString(",")

    /** Inverse of [encodeList]. Blanks are dropped so a stray separator cannot yield an empty id. */
    fun decodeList(encoded: String?): List<String> =
        encoded?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /**
     * Percent-encodes everything that is not an unreserved URI character. Hand-rolled rather than
     * pulled from a library because this must compile for iOS and JS as well as the JVM, and
     * `java.net.URLEncoder` is JVM-only.
     */
    fun encodeArg(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (byte in raw.encodeToByteArray()) {
            // A UTF-8 continuation/lead byte is always >= 0x80, i.e. negative as a signed Byte.
            // `byte.toInt()` sign-extends it (e.g. 0x80.toByte().toInt() == -128, not 128), so the
            // Char this produces is in 0xFF80..0xFFFF — nowhere near any range isUnreserved() tests
            // (all of which sit below 0x7F) — so every non-ASCII byte always falls to the escape
            // branch below. This is deliberate, not incidental: a future edit to isUnreserved() must
            // not add a range up there, or a raw high byte could start passing through unescaped.
            val c = byte.toInt().toChar()
            if (c.isUnreserved()) sb.append(c)
            else sb.append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
        return sb.toString()
    }

    /**
     * Inverse of [encodeArg]. Every route reaching this function was built by [encodeArg], so a
     * `%` that is not followed by exactly two hex digits — whether malformed or merely truncated
     * at the end of the string — means the input is corrupt or hand-crafted. Both deserve to fail
     * loudly: silently decoding a bad escape (or silently treating a truncated one as literal text)
     * would produce a plausible-looking but wrong string with nothing to catch it before it
     * navigates to a subtly wrong screen.
     */
    fun decodeArg(encoded: String): String {
        val out = ArrayList<Byte>(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '%') {
                require(i + 2 < encoded.length) {
                    "Malformed percent-encoding in \"$encoded\" at index $i: " +
                        "\"${encoded.substring(i)}\" is truncated (a % must be followed by two hex digits)"
                }
                val hi = encoded[i + 1]
                val lo = encoded[i + 2]
                require(hi.isHexDigit() && lo.isHexDigit()) {
                    "Malformed percent-encoding in \"$encoded\" at index $i: " +
                        "\"%$hi$lo\" is not a valid hex escape"
                }
                out.add(((hexVal(hi) shl 4) or hexVal(lo)).toByte())
                i += 3
            } else {
                for (b in c.toString().encodeToByteArray()) out.add(b)
                i += 1
            }
        }
        return out.toByteArray().decodeToString()
    }

    private const val HEX = "0123456789ABCDEF"

    private fun Char.isUnreserved(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' ||
            this == '_' || this == '.' || this == '~'

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        // Unreachable: every caller validates with isHexDigit() first. Throwing rather than
        // returning a sentinel keeps this function honest if that guarantee is ever broken.
        else -> throw IllegalArgumentException("Not a hex digit: '$c'")
    }

    private class RouteBuilder(private val base: String) {
        private val parts = mutableListOf<String>()
        fun optional(name: String, value: String?) {
            if (value != null) parts.add("$name=${encodeArg(value)}")
        }
        fun required(name: String, value: String) {
            parts.add("$name=${encodeArg(value)}")
        }
        fun build(): String = if (parts.isEmpty()) base else base + "?" + parts.joinToString("&")
    }

    private fun buildRoute(base: String, block: RouteBuilder.() -> Unit): String =
        RouteBuilder(base).apply(block).build()
}
