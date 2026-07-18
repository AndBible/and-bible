package net.bible.sharedcore.ai

/**
 * Host seam for `AiDocumentFilterActivity` (document access filter for AI tools).
 *
 * Wraps `Books.installed().books` grouped by `BookCategory` plus
 * `GlobalAiSettings.aiExcludedDocuments` (read via `CommonUtils.aiSettings`, persisted via
 * `GlobalAiSettingsDao.set`). Blacklist semantics: a document not present in the excluded set is
 * allowed by default, so a freshly installed document starts allowed with no explicit action.
 */
interface DocumentFilterService {
    /** Installed documents grouped by [BookCategory][org.crosswire.jsword.book.BookCategory]. */
    fun groups(): List<AiDocGroupVd>

    /** Persist the new excluded set (classic "Save" button — apply on save, not per-toggle). */
    fun setExcluded(excludedInitials: Set<String>)
}
