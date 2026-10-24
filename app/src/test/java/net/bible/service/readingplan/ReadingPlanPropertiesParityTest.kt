/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.readingplan

import org.crosswire.common.util.PropertiesCharset
import org.crosswire.common.util.PropertiesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Properties

/** The common parser the reading-plan DAO now uses against `java.util.Properties.load` (the old reader). */
class ReadingPlanPropertiesParityTest {
    private fun javaProps(bytes: ByteArray): Map<String, String> =
        Properties().apply { load(ByteArrayInputStream(bytes)) }.entries.associate { it.key as String to it.value as String }

    private fun assertParity(name: String, bytes: ByteArray) {
        val parsed = PropertiesParser.parse(bytes, PropertiesCharset.LATIN1)
        assertEquals(name, javaProps(bytes), parsed)
    }

    @Test fun everyBundledPlanParsesLikeJavaProperties() {
        val plans = File("src/main/assets/readingplan").listFiles { f -> f.name.endsWith(".properties") }!!
        assertTrue(plans.isNotEmpty())
        for (plan in plans) assertParity(plan.name, plan.readBytes())
    }

    @Test fun latin1FixtureWithContinuationsAndComments() {
        val text = "# Plan name\n! other comment\n1 = Gen.1, \\\n    Gen.2\n2:Mat.1\n3 Ps.1\n\\u0041x=caf\u00e9\nVersification=NRSVA\n"
        assertParity("fixture", text.toByteArray(Charsets.ISO_8859_1))
    }
}
