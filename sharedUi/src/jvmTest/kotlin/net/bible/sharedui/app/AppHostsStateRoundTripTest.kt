package net.bible.sharedui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Each lifting task adds a `<feature>StateRoundTrips` test with non-default field values. */
class AppHostsStateRoundTripTest {
    @Test
    fun emptyStateRoundTrips() {
        val json = Json.encodeToString(AppHostsState())
        assertEquals(AppHostsState(), Json.decodeFromString<AppHostsState>(json))
    }
}
