package net.bible.android.view.activity.nav

import android.os.Looper
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter.resetDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingPlanSyncSubscriptionTest {

    @After fun tearDown() = resetDatabase()

    /** The READINGPLANS accessor's reaction, exactly as `SyncableDatabaseAccessor.reactToUpdates` invokes it. */
    private fun syncReadingPlans() {
        val accessor = DatabaseContainer.getDatabaseAccessorFactories(DatabaseContainer.instance)
            .map { it() }
            .single { it.category == SyncableDatabaseDefinition.READINGPLANS }
        accessor._reactToUpdates!!.invoke(emptyList())
    }

    @Test
    fun aReadingPlanSyncReachesTheSubscriberOnMain_andStopsAfterUnsubscribe() {
        var calls = 0
        var thread: Looper? = null
        val unsubscribe = subscribeToReadingPlanSync { calls++; thread = Looper.myLooper() }

        val worker = Thread { syncReadingPlans() }   // the sync runs off the main thread
        worker.start(); worker.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
        assertSame(Looper.getMainLooper(), thread)

        unsubscribe()
        syncReadingPlans()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("no reaction after unsubscribe", 1, calls)
    }
}
