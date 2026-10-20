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
package net.bible.sharedui.reading.host

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingHostDepsFieldsTest {
    @Test
    fun theListedFieldsAreExactlyTheConstructorParameters() {
        // Constructor vals are stored in private final fields of the same name.
        val actual = ReadingHostDeps::class.java.declaredFields
            .filter { !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }.toSet()
        assertEquals(ReadingHostDepsFields.ALL - actual, emptySet(), "listed but no longer a field")
        assertEquals(actual - ReadingHostDepsFields.ALL, emptySet(), "a field the list (and so the iOS host test) does not know")
    }
}
