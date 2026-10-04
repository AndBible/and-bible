package net.bible.android.view.activity.settings

import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.SettingsScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Slice 8 B2: `textDisplaySettingsRoute(scope)` is exactly the inverse of `scopeFromRoute`. */
class TextDisplaySettingsRouteTest {

    private fun roundTrip(scope: SettingsScope, colors: Boolean = false): Pair<SettingsScope, Boolean> {
        val args = NavRoutes.readTextDisplaySettings(textDisplaySettingsRoute(scope, colors))
        return scopeFromRoute(args) to args.startAtColors
    }

    @Test
    fun everyScopeRoundTrips() {
        assertEquals(SettingsScope.Global to false, roundTrip(SettingsScope.Global))
        assertEquals(SettingsScope.Workspace("ws-1") to false, roundTrip(SettingsScope.Workspace("ws-1")))
        assertEquals(SettingsScope.Window("w-2", "ws-1") to false, roundTrip(SettingsScope.Window("w-2", "ws-1")))
    }

    @Test
    fun theColoursFlagTravels() {
        assertTrue(roundTrip(SettingsScope.Workspace("ws-1"), colors = true).second)
    }

    @Test
    fun aScopedRouteNeverCarriesADetachedBundle() {
        // scopeFromRoute REJECTS a route with both (fix round 1 of slice 7 Task 5).
        assertFalse(textDisplaySettingsRoute(SettingsScope.Window("w", "ws")).contains(NavRoutes.ARG_SETTINGS_BUNDLE + "="))
    }
}
