package net.bible.sharedcore.bibleview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleBridgeDecoderTest {
    private val bridge = RecordingBridge()

    private fun args(vararg v: Any?): List<JsonElement> = v.map {
        when (it) {
            null -> JsonNull
            is JsonElement -> it
            is String -> JsonPrimitive(it)
            is Number -> JsonPrimitive(it)
            is Boolean -> JsonPrimitive(it)
            else -> error("unsupported $it")
        }
    }

    private fun decode(method: String, vararg v: Any?) = BibleBridgeDecoder.decode(method, args(*v), bridge)

    @Test fun decodesTypedArguments() {
        assertTrue(decode("addBookmark", "KJV", 5, 7, true))
        assertEquals(listOf("addBookmark" to listOf<Any?>("KJV", 5, 7, true)), bridge.calls)
    }

    @Test fun nullableStringAcceptsNull() {
        decode("saveBookmarkNote", "b1", null)
        assertEquals(listOf<Any?>("b1", null), bridge.calls.single().second)
    }

    @Test fun nullNumberCoercesToZero() {
        decode("scrolledToOrdinal", "KJVA:Eph.2", null)
        assertEquals(listOf<Any?>("KJVA:Eph.2", 0), bridge.calls.single().second)
    }

    @Test fun nullBooleanCoercesToFalse() {
        decode("setEditing", null)
        assertEquals(listOf<Any?>(false), bridge.calls.single().second)
    }

    @Test fun fractionalNumberTruncatesTowardZero() {
        decode("setStudyPadCursor", "l", 3.9)
        decode("setStudyPadCursor", "l", -2.7)
        assertEquals(listOf<Any?>(3, -2), bridge.calls.map { it.second[1] })
    }

    @Test fun deferredCallIdIsLong() {
        decode("parseRef", 12, "Gen 1")
        assertEquals(listOf<Any?>(12L, "Gen 1"), bridge.calls.single().second)
    }

    @Test fun wrongArityThrows() {
        assertFailsWith<BridgeArgumentException> { decode("addBookmark", "KJV", 5) }
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun nullForNonNullStringThrows() {
        assertFailsWith<BridgeArgumentException> { decode("removeBookmark", null) }
    }

    @Test fun stringForNumberThrows() {
        assertFailsWith<BridgeArgumentException> { decode("scrolledToOrdinal", "k", "5") }
    }

    @Test fun numberForStringThrows() {
        assertFailsWith<BridgeArgumentException> { decode("removeBookmark", 5) }
    }

    @Test fun stringForBooleanThrows() {
        assertFailsWith<BridgeArgumentException> { decode("setEditing", "true") }
    }

    @Test fun objectArgumentThrows() {
        assertFailsWith<BridgeArgumentException> { decode("removeBookmark", JsonObject(emptyMap())) }
    }

    @Test fun unknownMethodReturnsFalse() {
        assertFalse(decode("noSuchMethod"))
        assertTrue(bridge.calls.isEmpty())
    }

    @Test fun callIdOfRecoversNullAsZeroAndRejectsMissingOrWronglyTypedIds() {
        assertEquals(7L, BibleBridgeDecoder.callIdOf(args(7, "x")))
        assertNull(BibleBridgeDecoder.callIdOf(args("7")))
        assertEquals(0L, BibleBridgeDecoder.callIdOf(args(null)))
        assertNull(BibleBridgeDecoder.callIdOf(args(true)))
        assertNull(BibleBridgeDecoder.callIdOf(emptyList()))
    }

    @Test fun callIdOfTruncatesFiniteFractionalNumbersTowardZero() {
        assertEquals(7L, BibleBridgeDecoder.callIdOf(args(7.0)))
        assertEquals(-2L, BibleBridgeDecoder.callIdOf(args(-2.7)))
    }

    @Test fun callIdOfSaturatesParsedNumbersOutsideDoubleRange() {
        assertEquals(Long.MAX_VALUE, BibleBridgeDecoder.callIdOf(listOf(Json.parseToJsonElement("1e400"))))
        assertEquals(Long.MIN_VALUE, BibleBridgeDecoder.callIdOf(listOf(Json.parseToJsonElement("-1e400"))))
    }

    @Test fun tableHas87MethodsAndDeferredOnesStartWithACallId() {
        assertEquals(87, BibleBridgeDecoder.entries.size)
        assertEquals(5, BibleBridgeDecoder.deferredMethods.size)
        BibleBridgeDecoder.deferredMethods.forEach { assertEquals('L', BibleBridgeDecoder.entries.getValue(it).kinds.first(), it) }
    }

    /** Catches a wrong accessor, a swapped index or a call to a differently named method in any entry. */
    @Test fun everyEntryPassesItsArgumentsInOrderToTheSameNamedMethod() {
        for ((method, entry) in BibleBridgeDecoder.entries) {
            val recorder = RecordingBridge()
            val sample = entry.kinds.mapIndexed { i, k ->
                when (k) {
                    'S', 's' -> JsonPrimitive("s$i")
                    'I' -> JsonPrimitive(i + 1)
                    'L' -> JsonPrimitive(i + 1)
                    'B' -> JsonPrimitive(i % 2 == 0)
                    else -> error("kind $k in $method")
                }
            }
            val expected: List<Any?> = entry.kinds.mapIndexed { i, k ->
                when (k) {
                    'S', 's' -> "s$i"
                    'I' -> i + 1
                    'L' -> (i + 1).toLong()
                    else -> i % 2 == 0
                }
            }
            assertTrue(BibleBridgeDecoder.decode(method, sample, recorder), method)
            assertEquals(listOf(method to expected), recorder.calls, method)
        }
    }
}
