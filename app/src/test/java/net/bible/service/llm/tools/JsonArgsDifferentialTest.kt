package net.bible.service.llm.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.bible.android.TEST_SDK
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Catches changed coercion/default/null semantics at the tool argument boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK])
class JsonArgsDifferentialTest {
    private val values = listOf(
        "\"s\"", "\"\"", "0", "12", "-12", "12.7", "-12.7", "1e3", "1e-3",
        "true", "false", "null", "{}", "{\"n\":1}", "[]", "[\"a\",\"b\"]", "[1,2]",
        "\"[1,2]\"", "\"12\"", "\"12.7\"", "\" 3 \"", "\"abc\"", "\"TRUE\"", "\"no\"",
        "\"false\"", "\"FaLsE\"", "\"true\"", "\" True \"", "\"\\u00e4\"", "\"a\\/b\"",
        "2147483648", "-2147483649", "\"1e2\"", "\"NaN\"", "\"Infinity\"",
        "[null,true,{},[]]", "{\"a\":[1,2]}", "\"-0\"", "0.0", "-0.0",
        "9223372036854775807", "9223372036854775808", "-9223372036854775809",
        "1e30", "1e-30", "[1e3,0.0,-0.0,\"a/b\"]", "{\"a/b\":1e3}"
    )

    @Test
    fun parserRejectsMalformedAndNonObjectArgumentsAndAcceptsBlank() {
        assertEquals(Json.parseToJsonElement("{}"), parseToolArguments("  \n"))
        assertEquals(Json.parseToJsonElement("{\"n\":1}"), parseToolArguments("{\"n\":1}"))
        for (text in listOf("not JSON", "[]", "null", "{\"n\":")) {
            org.junit.Assert.assertThrows(kotlinx.serialization.SerializationException::class.java) { parseToolArguments(text) }
        }
    }

    @Test
    fun prettyPrinterPreservesStructureAndUsesTwoSpaceIndent() {
        val text = """{"n":1,"nested":{"s":"a/b"},"items":[true,null]}"""
        val pretty = prettyJson(text)
        assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(pretty))
        org.junit.Assert.assertTrue(pretty.contains("\n  \"n\": 1"))
        val legacyPretty = JSONObject(text).toString(2)
        assertEquals(legacyPretty.replace("\\/", "/"), pretty)
        assertEquals("{\n  \"choices\": []\n}", JSONObject("{\"choices\":[]}").toString(2))
    }

    @Test
    fun accessorsMatchFrameworkCoercions() {
        for (text in listOf("{}") + values.map { "{\"value\":$it}" }) {
            val legacy = JSONObject(text)
            val portable = Json.parseToJsonElement(text).jsonObject
            for (key in listOf("value", "missing")) {
                val context = "$text key=$key"
                for (fallback in listOf("", "fb")) {
                    assertEquals("$context optString fallback=$fallback", legacy.optString(key, fallback), portable.optString(key, fallback))
                }
                for (fallback in listOf(0, -1)) {
                    assertEquals("$context optInt fallback=$fallback", legacy.optInt(key, fallback), portable.optInt(key, fallback))
                }
                for (fallback in listOf(false, true)) {
                    assertEquals("$context optBoolean fallback=$fallback", legacy.optBoolean(key, fallback), portable.optBoolean(key, fallback))
                }
                assertEquals("$context has", legacy.has(key), portable.has(key))
                assertEquals("$context isNull", legacy.isNull(key), portable.isNull(key))
                val oldArray = legacy.optJSONArray(key)
                val newArray = portable.optArray(key)
                assertEquals("$context array size", oldArray?.length(), newArray?.size)
                if (oldArray != null && newArray != null) {
                    for (i in 0 until oldArray.length()) {
                        assertEquals("$context array[$i]", oldArray.getString(i), newArray.stringAt(i))
                    }
                }
            }
        }
    }
}
