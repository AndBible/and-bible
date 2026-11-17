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

package net.bible.service.cloudsync.phos

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhosCodecTest {
    private val kjv = Versifications.instance().getVersification("KJV")

    @Test
    fun derivedIdsMatchTheOtherPhosClients() {
        // same values as md5(namespace + NUL + value) with v4/variant bits, as unit_uuid.pas computes them
        assertEquals("7e3591012fdf4cacbc08deacbc07bd51", PhosIds.labelUuidHex("Gebet"))
        assertEquals("244349bdbc34450a83dc66bb6ef728a4", PhosIds.bookmarkUuidHex("6A168692-F30E62E0"))
        assertEquals("d2d64ac86fc545f0a1345154214e4aff", PhosIds.windowUuidHex("0123456789abcdef0123456789abcdef", 1))
        assertEquals("0123456789abcdef0123456789abcdef", PhosIds.bookmarkUuidHex("0123456789ABCDEF0123456789ABCDEF"))
    }

    @Test
    fun idHexRoundTrip() {
        val hex = "0123456789abcdef0123456789abcdef"
        assertEquals(hex, PhosIds.hex(PhosIds.idOf(hex)))
    }

    @Test
    fun colours() {
        assertEquals("#ff0000", PhosBookmarkCodec.colorToHex(0xFFFF0000.toInt()))
        assertEquals(0xFF00FF00.toInt(), PhosBookmarkCodec.hexToColor("#00ff00", 0))
        assertEquals(7, PhosBookmarkCodec.hexToColor("nonsense", 7))
    }

    @Test
    fun canonIndex() {
        assertEquals(0, PhosVerses.canonIndex(BibleBook.GEN))
        assertEquals(38, PhosVerses.canonIndex(BibleBook.MAL))
        assertEquals(39, PhosVerses.canonIndex(BibleBook.MATT))
        assertEquals(65, PhosVerses.canonIndex(BibleBook.REV))
        assertEquals(-1, PhosVerses.canonIndex(BibleBook.INTRO_BIBLE))
    }

    @Test
    fun locationRoundTrip() {
        val loc = PhosLocation("Acts", 43, 8, 5, 7)
        val ords = PhosVerses.ordinals(kjv, loc)!!
        assertEquals(loc, PhosVerses.location(kjv, ords.first, ords.second))
        val cross = PhosLocation("John", 42, 3, 36, 36, endChap = 4, endVerse = 2)
        val o2 = PhosVerses.ordinals(kjv, cross)!!
        assertEquals(cross, PhosVerses.location(kjv, o2.first, o2.second))
        assertNull(PhosVerses.ordinals(kjv, PhosLocation("Nope", 0, 1, 1, 1)))
    }

    @Test
    fun bibleBookmarkRoundTrip() {
        val ords = PhosVerses.ordinals(kjv, PhosLocation("Rom", 44, 8, 28, 28))!!
        val b = AbBibleBookmark("0123456789abcdef0123456789abcdef", "KJV", ords.first, ords.second, "KJV", 3, 17, false,
            1_700_000_000_123L, 1_700_000_500_456L, null, AbNote("<p>Alles <b>gut</b></p>", "HTML"),
            listOf(AbLink("Trost", 4, 1, true)), "Trost")
        val json = PhosBookmarkCodec.bibleToJson(b, PhosVerses.location(kjv, ords.first, ords.second)!!)
        assertEquals("Rom", json.str("bookOsis"))
        assertEquals("Alles **gut**", json.str("noteMd"))
        assertEquals(1_700_000_500L, json.long("modified"))
        val back = PhosBookmarkCodec.bibleFromJson(b.idHex, json, kjv, b)
        assertEquals(b, back)
    }

    @Test
    fun noteEditedElsewhereBecomesMarkdown() {
        val p = buildJsonObject {
            put("noteMd", "neu")
            put("ab", buildJsonObject { put("noteHtml", "<p>alt</p>") })
        }
        assertEquals(AbNote("neu", "MARKDOWN"), PhosBookmarkCodec.noteFromJson(p))
        val same = buildJsonObject {
            put("noteMd", "alt")
            put("ab", buildJsonObject { put("noteHtml", "<p>alt</p>") })
        }
        assertEquals(AbNote("<p>alt</p>", "HTML"), PhosBookmarkCodec.noteFromJson(same))
    }

    @Test
    fun layoutReconciliationFollowsARemoteOrderIndentEdit() {
        val original = buildJsonObject {
            put("labelNames", JsonArray(listOf(JsonPrimitive("Trost"), JsonPrimitive("Other"))))
            put("sortOrder", 4); put("indent", 1)
            put("ab", buildJsonObject {
                put("primaryLabel", "Trost")
                put("labels", buildJsonObject {
                    put("Trost", JsonArray(listOf(JsonPrimitive(4), JsonPrimitive(1), JsonPrimitive(true))))
                    put("Other", JsonArray(listOf(JsonPrimitive(5), JsonPrimitive(0), JsonPrimitive(true))))
                })
            })
        }
        assertEquals(listOf(AbLink("Trost", 4, 1, true), AbLink("Other", 5, 0, true)), PhosBookmarkCodec.linksFromJson(original))

        // another client reorders/indents the bookmark without touching "ab.labels" at all, since it does
        // not understand that vendor field
        val edited = JsonObject(original + ("sortOrder" to JsonPrimitive(10)) + ("indent" to JsonPrimitive(2)))
        val links = PhosBookmarkCodec.linksFromJson(edited)
        assertEquals("the primary label's layout follows the remote edit", AbLink("Trost", 10, 2, true),
            links.first { it.labelName == "Trost" })
        assertEquals("a non-primary label's cached layout is left alone", AbLink("Other", 5, 0, true),
            links.first { it.labelName == "Other" })
    }

    @Test
    fun desktopBookmarkWithoutAbFields() {
        val p = buildJsonObject {
            put("id", "6A168692-F30E62E0"); put("kind", "range"); put("module", "KJV"); put("bookOsis", "Ps")
            put("bookIdx", 18); put("chap", 23); put("vStart", 1); put("vEnd", 3)
            put("labelNames", JsonArray(listOf(JsonPrimitive("Psalmen")))); put("sortOrder", 2)
            put("created", 1_600_000_000L); put("modified", 1_600_000_100L)
        }
        val b = PhosBookmarkCodec.bibleFromJson("244349bdbc34450a83dc66bb6ef728a4", p, kjv, null)!!
        assertEquals(PhosLocation("Ps", 18, 23, 1, 3), PhosVerses.location(kjv, b.ordinalStart, b.ordinalEnd))
        assertEquals(listOf(AbLink("Psalmen", 2, 0, true)), b.links)
        assertEquals(1_600_000_100_000L, b.updatedMs)
        assertNull(b.note)
    }

    @Test
    fun studyPadNotes() {
        val e = AbStudyPadEntry("0123456789abcdef0123456789abcdef", "Predigt", 3, 1, "# Gliederung", "MARKDOWN")
        val json = PhosBookmarkCodec.studyPadToJson(e)
        assertEquals("entry", json.str("kind"))
        assertTrue(PhosBookmarkCodec.isStudyPadNote(json))
        assertEquals(e, PhosBookmarkCodec.studyPadFromJson(e.idHex, json))
        val desktopNote = buildJsonObject { put("kind", "entry"); put("noteMd", "x"); put("labelNames", JsonArray(listOf(JsonPrimitive("A")))) }
        assertTrue(PhosBookmarkCodec.isStudyPadNote(desktopNote))
        val entry = buildJsonObject { put("kind", "entry"); put("module", "StrongsGreek"); put("entryKey", "G25") }
        assertFalse(PhosBookmarkCodec.isStudyPadNote(entry))
        assertNotNull(PhosBookmarkCodec.genericFromJson("0123456789abcdef0123456789abcdef", entry, null))
    }

    @Test
    fun overlayKeepsForeignFieldsAndSenderId() {
        val server = buildJsonObject {
            put("id", "6A168692-F30E62E0"); put("noteMd", "a"); put("noteHtml", "{\\rtf1 a}"); put("project", "p1"); put("module", "GerElb")
        }
        val mine = buildJsonObject { put("id", "244349bdbc34450a83dc66bb6ef728a4"); put("noteMd", "a") }
        val out = PhosBookmarkCodec.bookmarkOverlay(server, mine)
        assertEquals("6A168692-F30E62E0", out.str("id"))
        assertEquals("p1", out.str("project"))
        assertEquals("{\\rtf1 a}", out.str("noteHtml"))
        assertEquals("GerElb", out.str("module"))
        val changed = PhosBookmarkCodec.bookmarkOverlay(server, buildJsonObject { put("noteMd", "b") })
        assertNull(changed["noteHtml"])
    }

    @Test
    fun labels() {
        val l = AbLabel("0123456789abcdef0123456789abcdef", "Gebet", 0xFF336699.toInt(), underlineStyle = true, favourite = true)
        val json = PhosBookmarkCodec.labelToJson(l)
        assertEquals("#336699", json.str("color"))
        assertEquals(l, PhosBookmarkCodec.labelFromJson("Gebet", json, l, 0))
        val fresh = PhosBookmarkCodec.labelFromJson("Neu", buildJsonObject { put("color", "#112233") }, null, 0)
        assertEquals(PhosIds.labelUuidHex("Neu"), fresh.idHex)
        assertEquals("HIGHLIGHT", fresh.type)
    }

    @Test
    fun htmlToText() {
        assertEquals("a\nb", PhosNoteText.htmlToText("a<br>b"))
        assertEquals("- x\n- y", PhosNoteText.htmlToText("<ul><li>x</li><li>y</li></ul>"))
        assertEquals("1 < 2 & 3", PhosNoteText.htmlToText("1 &lt; 2 &amp; 3"))
    }

    @Test
    fun workspacePanels() {
        assertEquals(0, PhosWorkspaceCodec.canonIndexOfOrdinal(BibleBook.GEN.ordinal))
        assertEquals(39, PhosWorkspaceCodec.canonIndexOfOrdinal(BibleBook.MATT.ordinal))
        assertEquals(BibleBook.REV.ordinal, PhosWorkspaceCodec.ordinalOfCanonIndex(65))
        val p = PhosPanel("KJV", "Bible", 1.5, 2, true, 42, 2, 16, null)
        assertEquals(p, PhosWorkspaceCodec.panelFromJson(PhosWorkspaceCodec.panelToJson(p)))
        val server = PhosWorkspaceCodec.workspaceToJson("0123456789abcdef0123456789abcdef", "A", listOf(p))
        val withDisplay = JsonObject(server + ("panels" to JsonArray(listOf(
            JsonObject((server["panels"] as JsonArray)[0] as JsonObject + ("display" to JsonPrimitive("x")))))))
        val mine = PhosWorkspaceCodec.workspaceToJson("0123456789abcdef0123456789abcdef", "B", listOf(p.copy(verseIndex = 17)))
        val out = PhosWorkspaceCodec.overlay(withDisplay, mine)
        val panel = (out["panels"] as JsonArray)[0] as JsonObject
        assertEquals("x", panel.str("display"))
        assertEquals(17, panel.intOr("verseIndex", 0))
        assertEquals("B", out.str("name"))
    }

    @Test
    fun readingPlans() {
        val p = AbPlanProgress("y1ot1nt2_mcheyne", PhosReadingPlanCodec.dateMs("2026-01-05")!!, 4,
            mapOf(4 to listOf(true, false, true, true), 6 to listOf(true, true, true, true)))
        val json = PhosReadingPlanCodec.toJson(p)
        assertEquals("2026-01-05", json.str("startDate"))
        assertEquals(listOf(1, 2, 3, 6), (json["doneDays"] as JsonArray).map { (it as JsonPrimitive).content.toInt() })
        assertEquals(p, PhosReadingPlanCodec.fromJson(p.planCode, json, p) { 4 })
        assertEquals(listOf(true, false), PhosReadingPlanCodec.parseStatus(
            """{"chapterReadArray":[{"readingNumber":1,"isRead":true},{"readingNumber":2}]}"""))
    }

    @Test
    fun importingDoneDaysBeforeCurrentKeepsTheirExplicitStatus() {
        // doneDays=[1] alone advances "current" to 2, but day 1's own status must not be discarded: a
        // date-based plan does not treat days before "current" as implicitly read
        val json = buildJsonObject {
            put("startDate", "2026-01-05")
            put("doneDays", JsonArray(listOf(JsonPrimitive(1))))
        }
        val p = PhosReadingPlanCodec.fromJson("plan", json, null) { 1 }
        assertEquals(2, p.currentDay)
        assertEquals(listOf(true), p.status[1])
    }

    @Test
    fun partialReadingIsPaddedBeforeBeingTreatedAsFullyRead() {
        // only reading 1 of a 4-reading day was touched: padTouched must not let it look fully read
        val padded = PhosReadingPlanCodec.padTouched(listOf(true), 4)
        assertEquals(listOf(true, false, false, false), padded)
        assertFalse(padded.all { it })
        // a day whose touched readings already cover (or exceed) the real count is left alone
        assertEquals(listOf(true, true), PhosReadingPlanCodec.padTouched(listOf(true, true), 2))
    }
}
