/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.llm.processors

import net.bible.service.llm.LlmProcessor
import java.util.Locale

/**
 * LLM processor for translating document content to a target language.
 *
 * The processing params is the target language code (e.g., "fi", "en", "de").
 */
object TranslationProcessor : LlmProcessor {

    override val processorId: String = "translations"

    override fun getSystemPrompt(params: String): String {
        val targetLanguage = params
        return """You are a translator. Translate the text content within the XML document to $targetLanguage.
IMPORTANT RULES:
1. Preserve ALL XML tags, attributes, and structure exactly as they are
2. Only translate the text content between tags
3. Do not add any explanations, comments, or markdown formatting
4. Return ONLY the translated XML document, nothing else
5. Keep verse numbers, references, and other metadata unchanged"""
    }

    override fun getDescription(params: String): String {
        val languageName = Locale.forLanguageTag(params).displayLanguage
        return "Translated to $languageName"
    }

    override fun getLanguageCode(params: String): String = params
}
