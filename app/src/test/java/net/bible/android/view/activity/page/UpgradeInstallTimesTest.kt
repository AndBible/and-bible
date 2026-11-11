package net.bible.android.view.activity.page

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class UpgradeInstallTimesTest {
    @Test fun equalTimesIsFreshInstall() { assertFalse(isUpgradeInstallTimes(1_000L, 1_000L)) }

    @Test fun fewMillisecondsLaterIsStillFreshInstall() { assertFalse(isUpgradeInstallTimes(1_000L, 1_050L)) }

    @Test fun oneDayLaterIsUpgrade() { assertTrue(isUpgradeInstallTimes(1_000L, 1_000L + 86_400_000L)) }

    @Test fun boundaryIsExclusive() {
        assertFalse(isUpgradeInstallTimes(1_000L, 61_000L))
        assertTrue(isUpgradeInstallTimes(1_000L, 61_001L))
    }
}
