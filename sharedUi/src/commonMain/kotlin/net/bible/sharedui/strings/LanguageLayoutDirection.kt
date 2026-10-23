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
package net.bible.sharedui.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val rtlLanguages = setOf("ar", "he", "iw", "fa", "ur")

/**
 * The layout direction of the UI language [tag] (trimmed, `_` read as `-`, primary subtag, case-insensitive).
 * The app resolves its own UI language, so the direction follows it, not the system language.
 */
internal fun layoutDirectionForLanguage(tag: String): LayoutDirection =
    if (tag.trim().replace('_', '-').substringBefore('-').lowercase() in rtlLanguages) LayoutDirection.Rtl else LayoutDirection.Ltr

/** Provides [LocalLayoutDirection] for [languageTag]; recomposes with the direction when the language is switched in-app. */
@Composable
internal fun ProvideLanguageLayoutDirection(languageTag: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirectionForLanguage(languageTag), content = content)
}
