/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.ai

import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ai.AiDocGroupVd
import net.bible.sharedcore.ai.AiDocVd
import net.bible.sharedcore.ai.DocumentFilterService
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books

/**
 * Android impl of [DocumentFilterService] backing [net.bible.sharedcore.ai.AiDocumentFilterController]
 * (the per-document AI access filter). Mirrors classic [AiDocumentFilterActivity] exactly:
 *
 * - [groups] = `Books.installed().books` grouped by [BookCategory], listing the same four categories
 *   in the same order as classic — BIBLE / COMMENTARY / DICTIONARY / GENERAL_BOOK — each with its
 *   books sorted by initials, empty categories omitted. `categoryId` = the [BookCategory] enum
 *   constant (`.name`, e.g. `"BIBLE"`), used as a stable key by the controller/screen; `categoryLabel`
 *   = [BookCategory.toString] (the internationalized `externalName`, e.g. "Biblical Texts",
 *   "Commentaries", "Dictionaries", "General Books") per [AiDocGroupVd]'s contract — a human label
 *   rather than classic's raw enum-name section headers. NB the Kotlin `.name` (enum constant) vs
 *   `getName()` (JSword bean, the non-internationalized internal name) hazard: we deliberately use
 *   `.name` for the id and `toString()` for the label (never the shadowed `getName()`).
 * - Blacklist semantics: [AiDocVd.allowed] = `initials !in aiExcludedDocuments`, so a freshly
 *   installed document starts allowed.
 * - [setExcluded] persists the working excluded set to
 *   [CommonUtils.aiSettings] `aiExcludedDocuments` (classic "Save").
 *
 * Registered as a Koin single (stateless — reads installed books + `CommonUtils.aiSettings` live).
 */
class DocumentFilterServiceImpl : DocumentFilterService {
    private val settings get() = CommonUtils.aiSettings

    override fun groups(): List<AiDocGroupVd> {
        val excluded = settings.aiExcludedDocuments
        val allBooks = Books.installed().books
        return ORDERED_CATEGORIES.mapNotNull { category ->
            val booksInCategory = allBooks
                .filter { it.bookCategory == category }
                .sortedBy { it.initials }
            if (booksInCategory.isEmpty()) return@mapNotNull null
            AiDocGroupVd(
                categoryId = category.name,
                categoryLabel = category.toString(),
                docs = booksInCategory.map { book ->
                    AiDocVd(initials = book.initials, name = book.name, allowed = book.initials !in excluded)
                },
            )
        }
    }

    override fun setExcluded(excludedInitials: Set<String>) {
        settings.aiExcludedDocuments = excludedInitials
    }

    companion object {
        /** The four categories classic [AiDocumentFilterActivity] lists, in that order. */
        private val ORDERED_CATEGORIES = listOf(
            BookCategory.BIBLE,
            BookCategory.COMMENTARY,
            BookCategory.DICTIONARY,
            BookCategory.GENERAL_BOOK,
        )
    }
}
