package net.bible.sharedlogic

import net.bible.sharedlogic.text.HtmlEntities
import net.bible.sharedlogic.text.IsoUtcTimestamps
import net.bible.sharedlogic.text.NameBasedUuid
import org.apache.commons.text.StringEscapeUtils
import org.apache.commons.text.translate.EntityArrays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.random.Random

/** JVM oracles for the common helpers in :sharedLogic (`text` package); the old JVM classes are the truth. */
class G3HelperOracleTest {
    private val pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private fun sdf() = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    @Test
    fun isoFormatAndParseMatchSimpleDateFormat() {
        val rnd = Random(42)
        val from = 0L
        val to = 4_102_444_800L // 2100-01-01
        repeat(2000) {
            val ms = (from + rnd.nextLong(to - from)) * 1000
            val expected = sdf().format(java.util.Date(ms))
            assertEquals(expected, IsoUtcTimestamps.format(ms))
            assertEquals(sdf().parse(expected)!!.time, IsoUtcTimestamps.parseLenient(expected))
        }
    }

    @Test
    fun isoParseOverflowMatchesLenientSimpleDateFormat() {
        val rnd = Random(7)
        repeat(50) {
            val s = "%04d-%02d-%02dT%02d:%02d:%02dZ".format(
                rnd.nextInt(1971, 2099), rnd.nextInt(0, 30), rnd.nextInt(0, 80),
                rnd.nextInt(0, 60), rnd.nextInt(0, 130), rnd.nextInt(0, 130),
            )
            assertEquals(s, sdf().parse(s)!!.time, IsoUtcTimestamps.parseLenient(s))
        }
        for (s in listOf("2024-13-01T00:00:00Z", "2024-02-30T00:00:00Z", "2024-01-02T24:00:00Z", "2024-01-02T03:04:05Zjunk")) {
            assertEquals(s, sdf().parse(s)!!.time, IsoUtcTimestamps.parseLenient(s))
        }
    }

    @Test
    fun isoParseWhitespaceVariantsMatchSimpleDateFormat() {
        val variants = listOf(
            " 2022-01-01T00:00:00Z", "\t 2022-01-01T00:00:00Z", "2022- 01-01T00:00:00Z", "2022-01-\t01T00:00:00Z",
            "2022-01-01T 00:00:00Z", "2022-01-01T00: 00:00Z", "2022-01-01T00:00:  00Z",
            // not skipped by SimpleDateFormat: both sides must reject these
            "\n2022-01-01T00:00:00Z", "\u00a02022-01-01T00:00:00Z", "2022-01-01 T00:00:00Z", "2022-01-01T00:00:00 Z",
            "2022-\n01-01T00:00:00Z",
        )
        for (s in variants) {
            val oracle = try { sdf().parse(s)!!.time } catch (e: java.text.ParseException) { null }
            val ours = try { IsoUtcTimestamps.parseLenient(s) } catch (e: IllegalArgumentException) { null }
            assertEquals(s, oracle, ours)
        }
    }

    @Test
    fun isoUnparseableMessageMatches() {
        val oracle = try { sdf().parse("yesterday"); null } catch (e: java.text.ParseException) { e.message }
        val ours = try { IsoUtcTimestamps.parseLenient("yesterday"); null } catch (e: IllegalArgumentException) { e.message }
        assertEquals(oracle, ours)
    }

    private fun allEntityMaps() = listOf(
        EntityArrays.BASIC_ESCAPE, EntityArrays.ISO8859_1_ESCAPE, EntityArrays.HTML40_EXTENDED_ESCAPE,
    )

    @Test
    fun htmlEveryEntityMatchesCommonsText() {
        for (map in allEntityMaps()) {
            for ((raw, escaped) in map) {
                for (s in listOf(escaped.toString(), raw.toString(), "x${escaped}y")) {
                    assertEquals(s, StringEscapeUtils.unescapeHtml4(s), HtmlEntities.unescapeHtml4(s))
                }
            }
        }
        assertEquals(StringEscapeUtils.unescapeHtml4("&amp;quot;"), HtmlEntities.unescapeHtml4("&amp;quot;"))
    }

    @Test
    fun htmlRandomStringsMatchCommonsText() {
        val rnd = Random(99)
        val alphabet = "&#;xX0123456789abcdefAZ ampltqu"
        repeat(500) {
            val s = buildString { repeat(rnd.nextInt(0, 24)) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
            assertEquals(s, StringEscapeUtils.unescapeHtml4(s), HtmlEntities.unescapeHtml4(s))
        }
        for (s in listOf("&#65;&#x42;&#X43;", "&#65", "&#;", "&#x;", "&#128512;", "&#1a;", "&#0;", "&bogus;", "a&b;c")) {
            assertEquals(s, StringEscapeUtils.unescapeHtml4(s), HtmlEntities.unescapeHtml4(s))
        }
    }

    @Test
    fun uuidV3MatchesJvm() {
        val rnd = Random(5)
        repeat(1000) {
            val b = rnd.nextBytes(rnd.nextInt(0, 80))
            assertEquals(UUID.nameUUIDFromBytes(b).toString(), NameBasedUuid.v3(b))
        }
        val src = File("src/main/java/net/bible/service/llm/BuiltInPrompts.kt").readText()
        val keys = Regex("""stableId\("([^"]+)"\)""").findAll(src).map { it.groupValues[1] }.toList()
        assert(keys.size > 20) { "expected the BuiltInPrompts keys, found ${keys.size}" }
        for (k in keys) {
            val b = "andbible-builtin:$k".toByteArray()
            assertEquals(k, UUID.nameUUIDFromBytes(b).toString(), NameBasedUuid.v3(b))
        }
    }
}
