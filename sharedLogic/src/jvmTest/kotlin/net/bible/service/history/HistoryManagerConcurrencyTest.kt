package net.bible.service.history

import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.sharedlogic.addons.fakeAddonBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryManagerConcurrencyTest {
    private val book = fakeAddonBook("HistoryRace", null)
    private val windowId = IdType()
    // History storage only needs the owner's id; avoid starting the unrelated page/UI graph.
    private val window = blankWindow().also {
        Window::class.java.getDeclaredField("id").apply { isAccessible = true }.set(it, windowId)
    }
    private val manager = HistoryManager(WindowControl(), object : HistoryPlatform {
        override fun screenHistoryItem(window: Window, screenToken: Any?) = screenToken as HistoryItem
        override fun isOnReadingScreen() = false
        override fun leaveCurrentScreen() = Unit
    })

    @AfterTest
    fun tearDown() {
        HistoryManager.resetInstanceForTest()
    }

    @Test
    fun savingHistoryUsesASnapshotWhileAnotherThreadAddsAnItem() {
        val serializing = CountDownLatch(1)
        val added = CountDownLatch(1)
        val firstKey = object : Key by verse("John 3:16") {
            override fun getOsisID(): String {
                serializing.countDown()
                assertTrue(added.await(10, TimeUnit.SECONDS), "Adding history must not wait for key serialization")
                return "John.3.16"
            }
        }
        add(firstKey)
        add(verse("John 3:17"))
        val executor = Executors.newSingleThreadExecutor()
        val writer = executor.submit {
            assertTrue(serializing.await(10, TimeUnit.SECONDS), "History serialization did not start")
            try {
                add(verse("John 3:18"))
            } finally {
                added.countDown()
            }
        }
        try {
            val saved = manager.getEntities(windowId)
            writer.get(10, TimeUnit.SECONDS)
            assertEquals(listOf("John.3.16", "John.3.17"), saved.map { it.key })
            assertEquals(listOf("John.3.16", "John.3.17", "John.3.18"), manager.getEntities(windowId).map { it.key })
        } finally {
            serializing.countDown()
            added.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun historyScreenKeepsItsNewestFirstSnapshotAfterAddsAndClear() {
        add(verse("John 3:16"))
        add(verse("John 3:17"))
        val displayed = manager.getHistory(windowId)
        add(verse("John 3:18"))
        manager.clear()

        assertEquals(listOf("John.3.17", "John.3.16"), displayed.map { (it as KeyHistoryItem).key.getOsisID() })
        assertTrue(manager.getHistory(windowId).isEmpty())
        assertTrue(manager.getEntities(windowId).isEmpty())
    }

    @Test
    fun concurrentAddsRestoreAndClearKeepHistorySnapshotsBounded() {
        val executor = Executors.newFixedThreadPool(3)
        val start = CountDownLatch(1)
        val keys = listOf(verse("John 3:16"), verse("John 3:17"))
        try {
            val writer = executor.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                repeat(5_000) { add(keys[it % keys.size]) }
            }
            val reader = executor.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                repeat(5_000) {
                    assertTrue(manager.getHistory(windowId).size <= HistoryManager.MAX_HISTORY)
                    assertTrue(manager.getEntities(windowId).size <= HistoryManager.MAX_HISTORY)
                }
            }
            val restorer = executor.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                repeat(1_000) {
                    manager.restoreFrom(window, emptyList())
                    manager.clear()
                }
            }
            start.countDown()
            listOf(writer, reader, restorer).forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun add(key: Key) = manager.addHistoryItem(window, KeyHistoryItem(book, key, null, window))
    private fun verse(reference: String): Verse = requireNotNull(VerseFactory.fromString(Versifications.instance().getVersification("KJV"), reference))

    private fun blankWindow(): Window {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(field.get(null), Window::class.java) as Window
    }
}
