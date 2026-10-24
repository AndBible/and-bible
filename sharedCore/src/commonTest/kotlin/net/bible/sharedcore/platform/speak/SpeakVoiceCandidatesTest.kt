package net.bible.sharedcore.platform.speak

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeakVoiceCandidatesTest {
    @Test fun regionTagFirstThenDeviceThenNativeThenBare() =
        assertEquals(listOf("en-US", "en-AU", "en-GB", "en"), speakVoiceCandidates("en-US", "en-AU"))
    @Test fun deviceLocaleOfAnotherLanguageIsIgnored() =
        assertEquals(listOf("fi-FI", "fi"), speakVoiceCandidates("fi", "en-US"))
    @Test fun ancientGreekUsesGreekVoices() =
        assertEquals(listOf("el"), speakVoiceCandidates("grc", "en-US"))
    @Test fun unknownLanguageFallsBackToBare() =
        assertEquals(listOf("xx"), speakVoiceCandidates("xx", "en-US"))
    @Test fun noDuplicates() =
        assertEquals(listOf("en-GB", "en"), speakVoiceCandidates("en-GB", "en-GB"))
}
