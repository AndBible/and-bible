package net.bible.sharedcore.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
