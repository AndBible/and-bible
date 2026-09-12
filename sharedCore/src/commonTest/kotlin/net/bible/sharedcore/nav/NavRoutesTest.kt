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
        val route = NavRoutes.textDisplaySettings(
            scopeLevel = "workspace",
            workspaceId = "w-1",
            settingsBundle = """{"a":"b/c d"}""",
        )
        assertTrue(route.startsWith("settings/textDisplay"))
        assertTrue(route.contains("scopeLevel=workspace"))
        assertTrue(route.contains("workspaceId=w-1"))
        // A raw slash or space in the JSON would split the route.
        assertFalse(route.substringAfter("settings/textDisplay").contains(" "))
        assertFalse(route.substringAfter("settingsBundle=").contains("/"))
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
