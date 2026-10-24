package net.bible.service.common

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.database.progress.GlobalReadingProgressSettings
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [ReadingProgressSettings] reads and writes the store snapshot; the row follows once the launcher drains. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingProgressSettingsSnapshotTest {
    @Before fun setUp() { DatabaseResetter.resetDatabase() }
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    private fun store() = KoinJavaComponent.get<ReadingProgressSettingsStore>(ReadingProgressSettingsStore::class.java)
    private fun row() = runBlocking { DatabaseContainer.instance.progressDb.globalReadingProgressSettingsDao().get() }

    @Test fun propertiesReadAndWriteTheSnapshotAndPersist() = runBlocking {
        runBlocking { store().load() }
        ReadingProgressSettings.memorizeWordVisibility = "hidden"
        ReadingProgressSettings.activeCycle = 2
        assertEquals("hidden", store().current.memorizeWordVisibility)
        assertEquals(2, ReadingProgressSettings.activeCycle)
        withTimeout(10_000) { KoinJavaComponent.get<OrderedLauncher>(OrderedLauncher::class.java).join(ReadingProgressSettingsStore.KEY) }
        assertEquals(GlobalReadingProgressSettings(memorizeWordVisibility = "hidden", activeCycle = 2), row())
    }

    @Test fun changedStillFiresOnNotifyChanged() {
        var fired = 0
        val sub = ReadingProgressSettings.changed.subscribe { fired++ }
        ReadingProgressSettings.notifyChanged()
        sub.cancel()
        assertEquals(1, fired)
    }
}
