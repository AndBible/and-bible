package net.bible.service.common

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.navigation.RecentDocuments
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// CommonUtils.settings resolves through BibleApplication.application (a lateinit set by
// BibleApplication.onCreate), which a plain android.app.Application never runs — so, unlike
// the golden-test template this file started from, this needs TestBibleApplication, the same
// as every other Robolectric test that touches CommonUtils.settings/DatabaseContainer
// (e.g. DocumentSyncSettingsTest).
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class RecentDocumentsStoreTest {
    @Test fun anUnsetKeyReadsAsEmpty() {
        CommonUtils.settings.setString(RecentDocumentsStore.KEY, null)
        assertEquals(emptyList<String>(), RecentDocumentsStore.read())
    }

    @Test fun malformedJsonReadsAsEmptyRatherThanThrowing() {
        CommonUtils.settings.setString(RecentDocumentsStore.KEY, "{not a list")
        assertEquals(emptyList<String>(), RecentDocumentsStore.read())
    }

    @Test fun recordingRoundTrips() {
        CommonUtils.settings.setString(RecentDocumentsStore.KEY, null)
        RecentDocumentsStore.record("KJV")
        RecentDocumentsStore.record("ESV")
        assertEquals(listOf("ESV", "KJV"), RecentDocumentsStore.read())
    }

    @Test fun recordingNullIsIgnored() {
        CommonUtils.settings.setString(RecentDocumentsStore.KEY, null)
        RecentDocumentsStore.record("KJV")
        RecentDocumentsStore.record(null)
        assertEquals(listOf("KJV"), RecentDocumentsStore.read())
    }

    @Test fun theStoreNeverGrowsPastTheCap() {
        CommonUtils.settings.setString(RecentDocumentsStore.KEY, null)
        repeat(RecentDocuments.MAX + 5) { RecentDocumentsStore.record("D$it") }
        assertEquals(RecentDocuments.MAX, RecentDocumentsStore.read().size)
    }
}
