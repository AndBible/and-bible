package net.bible.sharedcore.docs

/**
 * All documentation targets opened by the app. Add targets here, not at call sites.
 *
 * The website deep-link gate reads these literal entries and checks every page and heading
 * against the built documentation. Page stems also match the tutorial table of contents.
 */
enum class DocsLink(val page: String, val anchor: String? = null) {
    /** Ai documentation. */
    AI("ai"),
    /** Ai ai tools documentation. */
    AI_AI_TOOLS("ai", "ai-tools"),
    /** Ai available data and documents documentation. */
    AI_AVAILABLE_DATA_AND_DOCUMENTS("ai", "available-data-and-documents"),
    /** Ai available models documentation. */
    AI_AVAILABLE_MODELS("ai", "available-models"),
    /** Ai choosing a provider documentation. */
    AI_CHOOSING_A_PROVIDER("ai", "choosing-a-provider"),
    /** Ai custom prompts documentation. */
    AI_CUSTOM_PROMPTS("ai", "custom-prompts"),
    /** Ai getting started documentation. */
    AI_GETTING_STARTED("ai", "getting-started"),
    /** Ai setting permissions documentation. */
    AI_SETTING_PERMISSIONS("ai", "setting-permissions"),
    /** Backup restore documentation. */
    BACKUP_RESTORE("backup_restore"),
    /** Bible links documentation. */
    BIBLE_LINKS("bible_links"),
    /** Bookmarks documentation. */
    BOOKMARKS("bookmarks"),
    /** Cloud sync documentation. */
    CLOUD_SYNC("cloud_sync"),
    /** Customisation documentation. */
    CUSTOMISATION("customisation"),
    /** Customisation custom css documentation. */
    CUSTOMISATION_CUSTOM_CSS("customisation/custom_css"),
    /** Customisation module creation documentation. */
    CUSTOMISATION_MODULE_CREATION("customisation/module_creation"),
    /** Custom repositories documentation. */
    CUSTOM_REPOSITORIES("custom_repositories"),
    /** Desktop documentation. */
    DESKTOP("desktop"),
    /** Discrete build documentation. */
    DISCRETE_BUILD("discrete_build"),
    /** Documents documentation. */
    DOCUMENTS("documents"),
    /** Document sync documentation. */
    DOCUMENT_SYNC("document_sync"),
    /** Faq documentation. */
    FAQ("faq"),
    /** Getting started documentation. */
    GETTING_STARTED("getting_started"),
    /** Getting started android documentation. */
    GETTING_STARTED_ANDROID("getting_started", "android"),
    /** Getting started installation documentation. */
    GETTING_STARTED_INSTALLATION("getting_started", "installation"),
    /** Getting started iphone and ipad documentation. */
    GETTING_STARTED_IPHONE_AND_IPAD("getting_started", "iphone-and-ipad"),
    /** Getting started linux documentation. */
    GETTING_STARTED_LINUX("getting_started", "linux"),
    /** Getting started mac documentation. */
    GETTING_STARTED_MAC("getting_started", "mac"),
    /** Getting started windows documentation. */
    GETTING_STARTED_WINDOWS("getting_started", "windows"),
    /** Labels documentation. */
    LABELS("labels"),
    /** Look and feel documentation. */
    LOOK_AND_FEEL("look_and_feel"),
    /** Memorize documentation. */
    MEMORIZE("memorize"),
    /** My documents documentation. */
    MY_DOCUMENTS("my_documents"),
    /** Navigation documentation. */
    NAVIGATION("navigation"),
    /** Notes documentation. */
    NOTES("notes"),
    /** Platform differences documentation. */
    PLATFORM_DIFFERENCES("platform_differences"),
    /** Reading plans documentation. */
    READING_PLANS("reading_plans"),
    /** Reading progress documentation. */
    READING_PROGRESS("reading_progress"),
    /** Search documentation. */
    SEARCH("search"),
    /** Search advanced search documentation. */
    SEARCH_ADVANCED_SEARCH("search", "advanced-search"),
    /** Share documentation. */
    SHARE("share"),
    /** Speak documentation. */
    SPEAK("speak"),
    /** Study pads documentation. */
    STUDY_PADS("study_pads"),
    /** Support documentation. */
    SUPPORT("support"),
    /** Usage documentation. */
    USAGE("usage"),
    /** Verse action dialog documentation. */
    VERSE_ACTION_DIALOG("verse_action_dialog"),
    /** Windows documentation. */
    WINDOWS("windows"),
    /** Workspaces documentation. */
    WORKSPACES("workspaces");

    val url: String get() = DocsLinks.page(this)
}
