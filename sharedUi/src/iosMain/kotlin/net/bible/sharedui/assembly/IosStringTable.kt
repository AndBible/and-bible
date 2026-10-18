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
package net.bible.sharedui.assembly

import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural

/**
 * The generated string tables of language [tag] as a [StringTable]. Templates are filled by
 * [net.bible.sharedui.strings.keyed.formatTemplate] (the generated `fmt` only knows positional `%1$s` specs, while
 * many Android strings use plain `%s`, `%d` and `%.1f`). [raw] reads a raw text resource (the LLM system prompts);
 * the app supplies it from the bundle, see `IosPlatformParts`.
 */
fun iosStringTable(
    tag: String,
    raw: (name: String) -> String = { error("Raw text resource '$it' is not available") },
): StringTable =
    StringTable.of(tag, { lookup(tag, it) }, { name, count -> lookupPlural(tag, name, count) }, { lookupArray(tag, it) }, raw)
