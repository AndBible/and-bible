package net.bible.service.common

import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fix batch 1 2.5b. `CommonUtils.changeAppIconAndName()` ends in `forceStopApp()` (`exitProcess(2)`),
 * so the decision is tested through the pure [componentStateToWrite].
 */
class ChangeAppIconAndNameTest {
    @Test fun aFreshInstallsDefaultStatesAlreadyMatchTheManifest() {
        assertNull(componentStateToWrite(COMPONENT_ENABLED_STATE_DEFAULT, manifestEnabled = true, wantEnabled = true))   // StartupActivity
        assertNull(componentStateToWrite(COMPONENT_ENABLED_STATE_DEFAULT, manifestEnabled = false, wantEnabled = false)) // Calculator alias
    }

    @Test fun turningDiscreteOnFromDefaultsWritesBoth() {
        assertEquals(COMPONENT_ENABLED_STATE_DISABLED, componentStateToWrite(COMPONENT_ENABLED_STATE_DEFAULT, true, false))
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, componentStateToWrite(COMPONENT_ENABLED_STATE_DEFAULT, false, true))
    }

    @Test fun explicitStatesCompareAsBefore() {
        assertNull(componentStateToWrite(COMPONENT_ENABLED_STATE_ENABLED, false, true))
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, componentStateToWrite(COMPONENT_ENABLED_STATE_DISABLED, true, true))
    }
}
