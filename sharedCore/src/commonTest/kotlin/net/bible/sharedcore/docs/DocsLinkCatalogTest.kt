package net.bible.sharedcore.docs

import kotlin.test.Test
import kotlin.test.assertEquals

class DocsLinkCatalogTest {
    @Test
    fun catalogUrlsMatchTargetsExtractedBeforeRefactoring() {
        // Snapshot extracted before the refactor: a builder or target change must be intentional.
        val original = mapOf(
            DocsLink.AI to "https://andbible.org/docs/ai/",
            DocsLink.AI_AI_TOOLS to "https://andbible.org/docs/ai/#ai-tools",
            DocsLink.AI_AVAILABLE_DATA_AND_DOCUMENTS to "https://andbible.org/docs/ai/#available-data-and-documents",
            DocsLink.AI_AVAILABLE_MODELS to "https://andbible.org/docs/ai/#available-models",
            DocsLink.AI_CHOOSING_A_PROVIDER to "https://andbible.org/docs/ai/#choosing-a-provider",
            DocsLink.AI_CUSTOM_PROMPTS to "https://andbible.org/docs/ai/#custom-prompts",
            DocsLink.AI_GETTING_STARTED to "https://andbible.org/docs/ai/#getting-started",
            DocsLink.AI_SETTING_PERMISSIONS to "https://andbible.org/docs/ai/#setting-permissions",
            DocsLink.BACKUP_RESTORE to "https://andbible.org/docs/backup_restore/",
            DocsLink.BIBLE_LINKS to "https://andbible.org/docs/bible_links/",
            DocsLink.BOOKMARKS to "https://andbible.org/docs/bookmarks/",
            DocsLink.CLOUD_SYNC to "https://andbible.org/docs/cloud_sync/",
            DocsLink.CUSTOMISATION to "https://andbible.org/docs/customisation/",
            DocsLink.CUSTOMISATION_CUSTOM_CSS to "https://andbible.org/docs/customisation/custom_css/",
            DocsLink.CUSTOMISATION_MODULE_CREATION to "https://andbible.org/docs/customisation/module_creation/",
            DocsLink.CUSTOM_REPOSITORIES to "https://andbible.org/docs/custom_repositories/",
            DocsLink.DESKTOP to "https://andbible.org/docs/desktop/",
            DocsLink.DISCRETE_BUILD to "https://andbible.org/docs/discrete_build/",
            DocsLink.DOCUMENTS to "https://andbible.org/docs/documents/",
            DocsLink.DOCUMENT_SYNC to "https://andbible.org/docs/document_sync/",
            DocsLink.FAQ to "https://andbible.org/docs/faq/",
            DocsLink.GETTING_STARTED to "https://andbible.org/docs/getting_started/",
            DocsLink.GETTING_STARTED_ANDROID to "https://andbible.org/docs/getting_started/#android",
            DocsLink.GETTING_STARTED_INSTALLATION to "https://andbible.org/docs/getting_started/#installation",
            DocsLink.GETTING_STARTED_IPHONE_AND_IPAD to "https://andbible.org/docs/getting_started/#iphone-and-ipad",
            DocsLink.GETTING_STARTED_LINUX to "https://andbible.org/docs/getting_started/#linux",
            DocsLink.GETTING_STARTED_MAC to "https://andbible.org/docs/getting_started/#mac",
            DocsLink.GETTING_STARTED_WINDOWS to "https://andbible.org/docs/getting_started/#windows",
            DocsLink.LABELS to "https://andbible.org/docs/labels/",
            DocsLink.LOOK_AND_FEEL to "https://andbible.org/docs/look_and_feel/",
            DocsLink.MEMORIZE to "https://andbible.org/docs/memorize/",
            DocsLink.MY_DOCUMENTS to "https://andbible.org/docs/my_documents/",
            DocsLink.NAVIGATION to "https://andbible.org/docs/navigation/",
            DocsLink.NOTES to "https://andbible.org/docs/notes/",
            DocsLink.PLATFORM_DIFFERENCES to "https://andbible.org/docs/platform_differences/",
            DocsLink.READING_PLANS to "https://andbible.org/docs/reading_plans/",
            DocsLink.READING_PROGRESS to "https://andbible.org/docs/reading_progress/",
            DocsLink.SEARCH to "https://andbible.org/docs/search/",
            DocsLink.SEARCH_ADVANCED_SEARCH to "https://andbible.org/docs/search/#advanced-search",
            DocsLink.SHARE to "https://andbible.org/docs/share/",
            DocsLink.SPEAK to "https://andbible.org/docs/speak/",
            DocsLink.STUDY_PADS to "https://andbible.org/docs/study_pads/",
            DocsLink.SUPPORT to "https://andbible.org/docs/support/",
            DocsLink.USAGE to "https://andbible.org/docs/usage/",
            DocsLink.VERSE_ACTION_DIALOG to "https://andbible.org/docs/verse_action_dialog/",
            DocsLink.WINDOWS to "https://andbible.org/docs/windows/",
            DocsLink.WORKSPACES to "https://andbible.org/docs/workspaces/",
        )
        assertEquals(DocsLink.entries.toSet(), original.keys)
        original.forEach { (link, expected) ->
            assertEquals(expected, link.url, link.name)
            assertEquals(expected, DocsLinks.page(link), link.name)
        }
    }
}
