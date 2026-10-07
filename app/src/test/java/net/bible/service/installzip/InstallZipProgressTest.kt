package net.bible.service.installzip

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallZipProgressTest {
    @After fun tearDown() { InstallZipProgress.resetSubscribersForTest() }

    @Test fun reportsReachSubscribersInOrder_andNotAfterCancel() {
        val seen = mutableListOf<String>()
        val subscription = InstallZipProgress.messages.subscribe { seen += it }
        InstallZipProgress.report("a")
        InstallZipProgress.report("b")
        subscription.cancel()
        InstallZipProgress.report("c")
        assertEquals(listOf("a", "b"), seen)
    }
}
