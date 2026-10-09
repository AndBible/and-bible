package net.bible.sharedcore.platform

import kotlin.test.Test
import kotlin.test.assertEquals

private enum class Fruit { APPLE, PEAR }

class AppSettingsTest {
    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = (m[key] as String?) ?: default
        override fun getLong(key: String, default: Long) = (m[key] as Long?) ?: default
        override fun getInt(key: String, default: Int) = (m[key] as Long?)?.toInt() ?: default
        override fun getBoolean(key: String, default: Boolean) = (m[key] as Boolean?) ?: default
        override fun getDouble(key: String, default: Double) = (m[key] as Double?) ?: default
        override fun getFloat(key: String, default: Float) = (m[key] as Double?)?.toFloat() ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value?.toLong() }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value?.toDouble() }
    }

    @Test fun stringSetRoundTripsAndNullRemoves() {
        val s = MapSettings()
        s.setStringSet("k", setOf("a", "b"))
        assertEquals(setOf("a", "b"), s.getStringSet("k"))
        s.setStringSet("k", null)
        assertEquals(setOf("x"), s.getStringSet("k", setOf("x")))
    }

    @Test fun corruptStringSetFallsBackToDefault() {
        val s = MapSettings()
        s.setString("k", "{not json")
        assertEquals(setOf("d"), s.getStringSet("k", setOf("d")))
    }

    @Test fun enumSetRoundTripsAndCorruptFallsBack() {
        val s = MapSettings()
        s.setEnumSet("e", setOf(Fruit.PEAR))
        assertEquals(setOf(Fruit.PEAR), s.getEnumSet<Fruit>("e"))
        s.setString("e", "[\"BANANA\"]")
        assertEquals(setOf(Fruit.APPLE), s.getEnumSet("e", setOf(Fruit.APPLE)))
    }
}
