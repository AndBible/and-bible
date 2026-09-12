package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavRoutesTest {

    @Test
    fun noArgRoutesAreStableLiterals() {
        assertEquals("ai/prompts", NavRoutes.AI_PROMPTS)
        assertEquals("ai/connectionSettings", NavRoutes.AI_CONNECTION_SETTINGS)
        assertEquals("ai/models", NavRoutes.AI_MODELS)
        assertEquals("ai/documentFilter", NavRoutes.AI_DOCUMENT_FILTER)
        assertEquals("ai/globalToolPermissions", NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS)
        assertEquals("ai/toolInfo", NavRoutes.AI_TOOL_INFO)
        assertEquals("ai/rawLogHistory", NavRoutes.AI_RAW_LOG_HISTORY)
    }

    @Test
    fun aiProvidersCarriesTheEasySetupFlag() {
        assertEquals("ai/providers?startEasySetup=false", NavRoutes.aiProviders(startEasySetup = false))
        assertEquals("ai/providers?startEasySetup=true", NavRoutes.aiProviders(startEasySetup = true))
    }

    @Test
    fun promptEditOmitsAbsentOptionalArgs() {
        assertEquals("ai/promptEdit?executeAfterSave=false", NavRoutes.promptEdit())
        assertEquals(
            "ai/promptEdit?promptId=abc-123&executeAfterSave=false",
            NavRoutes.promptEdit(promptId = "abc-123"),
        )
    }

    /**
     * D3: template and defaultContext are FREE TEXT. They have no producer today, but a route that
     * mangled one would be a silent data bug the day the "customize this prompt" flow is rewired.
     */
    @Test
    fun freeTextArgsSurviveARoundTrip() {
        val template = "Explain {verse}: what does it mean? / context #1 & more"
        val route = NavRoutes.promptEdit(template = template)
        val encoded = route.substringAfter("template=").substringBefore("&")
        assertEquals(template, NavRoutes.decodeArg(encoded))
        assertTrue("/" !in encoded, "a raw slash would split the route path")
        assertTrue("?" !in encoded && "&" !in encoded, "raw query separators would corrupt later args")
        assertTrue("#" !in encoded, "a raw fragment marker would truncate the arg")
    }

    /**
     * FINDING 1 (Task 1 review round 1): the round-trip above never exercised multi-byte UTF-8,
     * even though that is the stated reason it exists. This app ships 30+ locales, so a Cyrillic
     * or CJK prompt template is realistic content, not a hypothetical — Б is a 2-byte UTF-8
     * sequence, 语 a 3-byte one.
     */
    @Test
    fun freeTextArgsSurviveARoundTripWithMultiByteUtf8() {
        val template = "Молитва Б: 语言 study notes"
        val route = NavRoutes.promptEdit(template = template)
        val encoded = route.substringAfter("template=").substringBefore("&")
        assertEquals(template, NavRoutes.decodeArg(encoded))
        assertTrue(
            encoded.all { it.code <= 0x7F },
            "encodeArg must leave no raw non-ASCII byte in the route: \"$encoded\"",
        )
    }

    /**
     * FINDING 2 (Task 1 review round 1): a malformed percent sequence must fail loudly, not
     * silently decode to a plausible-looking but wrong string.
     */
    @Test
    fun decodeArgThrowsOnInvalidHexEscape() {
        assertFailsWith<IllegalArgumentException> { NavRoutes.decodeArg("%G5") }
    }

    /**
     * FINDING 2 follow-up: a `%` truncated at end-of-string is just as malformed as a bad hex
     * digit, so it must throw too rather than silently falling through as literal text.
     */
    @Test
    fun decodeArgThrowsOnTruncatedEscapeAtEndOfString() {
        assertFailsWith<IllegalArgumentException> { NavRoutes.decodeArg("abc%A") }
    }

    @Test
    fun rawLlmLogSupportsItsTwoMutuallyExclusiveModes() {
        assertEquals("ai/rawLlmLog?logRecordId=rec-1", NavRoutes.rawLlmLog(logRecordId = "rec-1"))
        assertEquals("ai/rawLlmLog?workspaceId=ws-9", NavRoutes.rawLlmLog(workspaceId = "ws-9"))
        assertEquals("ai/rawLlmLog", NavRoutes.rawLlmLog())
    }

    @Test
    fun argumentNamesMatchThePatternsTheyAppearIn() {
        assertTrue(NavRoutes.AI_PROVIDERS_PATTERN.contains("{${NavRoutes.ARG_START_EASY_SETUP}}"))
        assertTrue(NavRoutes.PROMPT_EDIT_PATTERN.contains("{${NavRoutes.ARG_PROMPT_ID}}"))
        assertTrue(NavRoutes.RAW_LLM_LOG_PATTERN.contains("{${NavRoutes.ARG_WORKSPACE_ID}}"))
    }

    // ——— slice 7: reading view, choosers, workspace ———

    @Test
    fun gridChoosePassageCarriesIsScriptureAndNothingElse() {
        assertEquals("navigation/gridChoosePassage?isScripture=true", NavRoutes.gridChoosePassage(true))
        assertEquals("navigation/gridChoosePassage?isScripture=false", NavRoutes.gridChoosePassage())
        assertTrue(NavRoutes.readGridChoosePassage(NavRoutes.gridChoosePassage(true)))
        assertFalse(NavRoutes.readGridChoosePassage(NavRoutes.gridChoosePassage(false)))
    }

    /**
     * Spec 6.1.1: the only producer of that extra moves to the sheet, so the route must not grow an
     * argument nothing sets. A route argument with no producer is dead weight that later readers
     * mistake for a live contract.
     */
    @Test
    fun gridChoosePassageRouteCarriesNoNavigateToVerseArgument() {
        assertFalse(NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN.contains("navigateToVerse"))
    }

    @Test
    fun chooseDocumentCarriesAnOptionalType() {
        assertEquals("navigation/chooseDocument?type=BIBLE", NavRoutes.chooseDocument("BIBLE"))
        assertEquals("navigation/chooseDocument", NavRoutes.chooseDocument())
        assertEquals("BIBLE", NavRoutes.readChooseDocument(NavRoutes.chooseDocument("BIBLE")))
        assertNull(NavRoutes.readChooseDocument(NavRoutes.chooseDocument()))
    }

    @Test
    fun textDisplaySettingsCarriesAllFiveArgumentsAndEncodesTheBundle() {
        val json = """{"a":"b/c d"}"""
        val route = NavRoutes.textDisplaySettings(
            scopeLevel = "workspace",
            windowId = "win-1",
            workspaceId = "w-1",
            startAtColors = true,
            settingsBundle = json,
        )
        assertTrue(route.startsWith("settings/textDisplay"))
        assertTrue(route.contains("scopeLevel=workspace"))
        // Fix round 1 (review M1): windowId had no test at all, so dropping its `optional(...)`
        // line left the whole suite green.
        assertTrue(route.contains("windowId=win-1"), route)
        assertTrue(route.contains("workspaceId=w-1"))
        assertTrue(route.contains("startAtColors=true"), route)
        // A raw slash or space in the JSON would split the route.
        assertFalse(route.substringAfter("settings/textDisplay").contains(" "))
        assertFalse(route.substringAfter("settingsBundle=").substringBefore("&").contains("/"))
        // Fix round 1 (review M2): a containment check passes just as happily on a DOUBLE-encoded
        // bundle, which is the hazard the builder's kdoc warns about. Only a round trip sees it.
        assertEquals(json, NavRoutes.decodeArg(route.substringAfter("settingsBundle=").substringBefore("&")))
    }

    /**
     * Fix round 1 (review M1): `startAtColors` is `required`, not `optional`, precisely so a reader
     * never has to tell "absent" from "false" — but nothing asserted it, so weakening it to
     * `optional(..., null)` kept the suite green.
     */
    @Test
    fun textDisplaySettingsAlwaysEmitsStartAtColorsAndNothingElseByDefault() {
        assertEquals("settings/textDisplay?startAtColors=false", NavRoutes.textDisplaySettings())
    }

    /**
     * Fix round 1 (review M1), mirroring `NavRoutesSlices356Test.everyPatternRegistersEveryArgumentItsBuilderCanEmit`:
     * a pattern that forgets an argument its builder can emit silently drops it at navigate time.
     */
    @Test
    fun everySlice7PatternRegistersEveryArgumentItsBuilderCanEmit() {
        fun argsIn(s: String) = Regex("[?&]([A-Za-z]+)=").findAll(s).map { it.groupValues[1] }.toSet()
        assertTrue(
            argsIn(NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN)
                .containsAll(argsIn(NavRoutes.gridChoosePassage(true))),
        )
        assertTrue(
            argsIn(NavRoutes.CHOOSE_DOCUMENT_PATTERN).containsAll(argsIn(NavRoutes.chooseDocument("BIBLE"))),
        )
        assertTrue(
            argsIn(NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN).containsAll(
                argsIn(NavRoutes.textDisplaySettings("global", "win-1", "w-1", true, """{"a":1}""")),
            ),
        )
    }

    /**
     * Fix round 1 (review n4): the pattern and the builder repeat the same base path as two separate
     * literals, and a typo in either is a runtime "destination not found" that no other test sees.
     */
    @Test
    fun slice7PatternsAndBuildersAgreeOnTheirBasePath() {
        assertEquals(
            NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN.substringBefore('?'),
            NavRoutes.gridChoosePassage().substringBefore('?'),
        )
        assertEquals(
            NavRoutes.CHOOSE_DOCUMENT_PATTERN.substringBefore('?'),
            NavRoutes.chooseDocument("BIBLE").substringBefore('?'),
        )
        assertEquals(
            NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN.substringBefore('?'),
            NavRoutes.textDisplaySettings().substringBefore('?'),
        )
    }

    /**
     * Fix round 1 (review M3): `readChooseDocument`/`readGridChoosePassage` parse by argument NAME
     * instead of the plan's `substringAfter("type=")`, and decode the value. Neither property was
     * tested — `"BIBLE"` encodes to itself, so deleting the `decodeArg` step kept the suite green,
     * and nothing exercised a foreign argument whose name ends in the same characters.
     */
    @Test
    fun readChooseDocumentDecodesItsValueAndReadsOnlyItsOwnArgument() {
        assertEquals("a b/c", NavRoutes.readChooseDocument(NavRoutes.chooseDocument("a b/c")))
        // Empty counts as absent, as it does for the navigation library's own `(.+?)` regex.
        assertNull(NavRoutes.readChooseDocument("navigation/chooseDocument?type="))
        // A different argument whose name merely ENDS with "type".
        assertNull(NavRoutes.readChooseDocument("search/form?searchType=BIBLE"))
        // A bare flag with no '=' must not derail the parse of the arguments around it.
        assertEquals("BIBLE", NavRoutes.readChooseDocument("navigation/chooseDocument?a=1&flag&type=BIBLE&b=2"))
    }

    @Test
    fun readGridChoosePassageReadsOnlyItsOwnArgument() {
        assertFalse(NavRoutes.readGridChoosePassage("navigation/gridChoosePassage?notIsScripture=true"))
        // The unsubstituted pattern is not a route anyone navigated to; it must read as false
        // rather than as "{isScripture}" being truthy.
        assertFalse(NavRoutes.readGridChoosePassage(NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN))
    }

    @Test
    fun argumentFreeRoutesAreStableStrings() {
        assertEquals("reading", NavRoutes.READING)
        assertEquals("navigation/chooseGeneralBookKey", NavRoutes.CHOOSE_GENERAL_BOOK_KEY)
        assertEquals("navigation/chooseMapKey", NavRoutes.CHOOSE_MAP_KEY)
        assertEquals("navigation/chooseDictionaryWord", NavRoutes.CHOOSE_DICTIONARY_WORD)
        assertEquals("workspaces/selector", NavRoutes.WORKSPACE_SELECTOR)
    }

    @Test
    fun slice7ArgumentNamesMatchThePatternsTheyAppearIn() {
        assertTrue(NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN.contains("{${NavRoutes.ARG_IS_SCRIPTURE}}"))
        assertTrue(NavRoutes.CHOOSE_DOCUMENT_PATTERN.contains("{${NavRoutes.ARG_DOCUMENT_TYPE}}"))
        for (arg in listOf(
            NavRoutes.ARG_SCOPE_LEVEL,
            NavRoutes.ARG_WINDOW_ID,
            NavRoutes.ARG_WORKSPACE_ID,
            NavRoutes.ARG_START_AT_COLORS,
            NavRoutes.ARG_SETTINGS_BUNDLE,
        )) {
            assertTrue(
                NavRoutes.TEXT_DISPLAY_SETTINGS_PATTERN.contains("{$arg}"),
                "TEXT_DISPLAY_SETTINGS_PATTERN is missing {$arg}",
            )
        }
    }
}
