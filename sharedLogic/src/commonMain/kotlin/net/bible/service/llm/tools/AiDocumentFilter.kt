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

package net.bible.service.llm.tools

import net.bible.service.llm.AiSettings
import org.koin.core.component.KoinComponent
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book

/**
 * Centralized filter for AI document access.
 *
 * Uses a blacklist approach: documents in [aiSettings.aiExcludedDocuments]
 * are excluded from AI tool access. New documents are allowed by default.
 */
object AiDocumentFilter : KoinComponent {
    private val aiSettings: AiSettings get() = getKoin().get()
    fun isAllowed(initials: String): Boolean =
        initials !in aiSettings.aiExcludedDocuments

    fun <T : Book> filterAllowed(books: List<T>): List<T> {
        val excluded = aiSettings.aiExcludedDocuments
        return if (excluded.isEmpty()) books
        else books.filter { it.initials !in excluded }
    }

    fun preferredStrongsGreek(): String? =
        SwordDocumentFacade.defaultStrongsGreekDictionary(getKoin().get<net.bible.service.download.FakeBookFactory>(), getKoin().get<net.bible.sharedcore.platform.AppSettings>())
            .firstOrNull { isAllowed(it.initials) }?.initials

    fun preferredStrongsHebrew(): String? =
        SwordDocumentFacade.defaultStrongsHebrewDictionary(getKoin().get<net.bible.service.download.FakeBookFactory>(), getKoin().get<net.bible.sharedcore.platform.AppSettings>())
            .firstOrNull { isAllowed(it.initials) }?.initials

    fun preferredRobinsonMorphology(): String? =
        SwordDocumentFacade.defaultRobinsonGreekMorphology(getKoin().get<net.bible.service.download.FakeBookFactory>(), getKoin().get<net.bible.sharedcore.platform.AppSettings>())
            .firstOrNull { isAllowed(it.initials) }?.initials
}
