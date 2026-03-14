/*
 * Copyright (c) 2022-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

import org.junit.Assert
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun testMapIdTypeIntToStr_withValidMap() {
        // Test serializing a map with valid UUID-based IdTypes
        val label1 = IdType(MyUUID.randomUUID())
        val label2 = IdType(MyUUID.randomUUID())
        val label3 = IdType(MyUUID.randomUUID())

        val map = mapOf(
            label1 to 5,
            label2 to 10,
            label3 to 0
        )

        val result = converters.mapIdTypeIntToStr(map)

        Assert.assertNotNull("Result should not be null", result)
        // Verify it's valid JSON by checking it contains UUID format (with dashes)
        Assert.assertTrue("Result should contain UUID format", result!!.contains("-"))
    }

    @Test
    fun testMapIdTypeIntToStr_withEmptyMap() {
        // Test serializing an empty map
        val map = emptyMap<IdType, Int>()
        val result = converters.mapIdTypeIntToStr(map)

        Assert.assertNotNull("Result should not be null for empty map", result)
    }

    @Test
    fun testMapIdTypeIntToStr_withNull() {
        // Test serializing null value
        val result = converters.mapIdTypeIntToStr(null)
        Assert.assertNull("Result should be null when input is null", result)
    }

    @Test
    fun testStrToMapIdTypeInt_withValidJson() {
        // Test deserializing a valid JSON string with UUID-based IdTypes
        // First create IdTypes and serialize them to get valid UUIDs
        val label1 = IdType(MyUUID.randomUUID())
        val label2 = IdType(MyUUID.randomUUID())
        val label3 = IdType(MyUUID.randomUUID())

        val originalMap = mapOf(
            label1 to 5,
            label2 to 10,
            label3 to 0
        )

        val json = converters.mapIdTypeIntToStr(originalMap)!!
        val result = converters.strToMapIdTypeInt(json)

        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Map should have 3 entries", 3, result.size)
        Assert.assertEquals("label1 value should be 5", 5, result[label1])
        Assert.assertEquals("label2 value should be 10", 10, result[label2])
        Assert.assertEquals("label3 value should be 0", 0, result[label3])
    }

    @Test
    fun testStrToMapIdTypeInt_withEmptyJson() {
        // Test deserializing an empty JSON object
        val json = "{}"
        val result = converters.strToMapIdTypeInt(json)

        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Map should be empty", 0, result.size)
    }

    @Test
    fun testStrToMapIdTypeInt_withNull() {
        // Test deserializing null value
        val result = converters.strToMapIdTypeInt(null)

        Assert.assertNotNull("Result should not be null for null input", result)
        Assert.assertEquals("Result should be empty map for null input", 0, result.size)
    }

    @Test
    fun testStrToMapIdTypeInt_withInvalidJson() {
        // Test deserializing invalid JSON - should return empty map
        val invalidJson = "invalid json {{"
        val result = converters.strToMapIdTypeInt(invalidJson)

        Assert.assertNotNull("Result should not be null for invalid JSON", result)
        Assert.assertEquals("Result should be empty map for invalid JSON", 0, result.size)
    }

    @Test
    fun testRoundTrip_mapIdTypeInt() {
        // Test serialization followed by deserialization with UUID-based IdTypes
        val cursor1 = IdType(MyUUID.randomUUID())
        val cursor2 = IdType(MyUUID.randomUUID())
        val cursor3 = IdType(MyUUID.randomUUID())

        val originalMap = mutableMapOf(
            cursor1 to 15,
            cursor2 to 3,
            cursor3 to 100
        )

        // Serialize
        val serialized = converters.mapIdTypeIntToStr(originalMap)
        Assert.assertNotNull("Serialized value should not be null", serialized)

        // Deserialize
        val deserialized = converters.strToMapIdTypeInt(serialized)

        // Verify
        Assert.assertEquals("Deserialized map should have same size", originalMap.size, deserialized.size)
        Assert.assertEquals("cursor1 value should match", originalMap[cursor1], deserialized[cursor1])
        Assert.assertEquals("cursor2 value should match", originalMap[cursor2], deserialized[cursor2])
        Assert.assertEquals("cursor3 value should match", originalMap[cursor3], deserialized[cursor3])
    }

    @Test
    fun testStrToMapIdTypeInt_withMultipleEntries() {
        // Test with multiple label IDs using valid UUIDs
        val label1 = IdType(MyUUID.randomUUID())
        val label2 = IdType(MyUUID.randomUUID())

        val originalMap = mapOf(
            label1 to 42,
            label2 to 99
        )

        val json = converters.mapIdTypeIntToStr(originalMap)!!
        val result = converters.strToMapIdTypeInt(json)

        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Map should have 2 entries", 2, result.size)
        Assert.assertEquals("label1 value should be 42", 42, result[label1])
        Assert.assertEquals("label2 value should be 99", 99, result[label2])
    }

    @Test
    fun testMapIdTypeIntToStr_withLargeValues() {
        // Test with large integer values using UUID-based IdTypes
        val large1 = IdType(MyUUID.randomUUID())
        val large2 = IdType(MyUUID.randomUUID())
        val zero = IdType(MyUUID.randomUUID())

        val map = mapOf(
            large1 to Int.MAX_VALUE,
            large2 to Int.MIN_VALUE,
            zero to 0
        )

        val serialized = converters.mapIdTypeIntToStr(map)
        Assert.assertNotNull("Serialized value should not be null", serialized)

        val deserialized = converters.strToMapIdTypeInt(serialized)
        Assert.assertEquals("large1 should serialize/deserialize correctly", Int.MAX_VALUE, deserialized[large1])
        Assert.assertEquals("large2 should serialize/deserialize correctly", Int.MIN_VALUE, deserialized[large2])
        Assert.assertEquals("zero should serialize/deserialize correctly", 0, deserialized[zero])
    }
}
