package net.bible.sharedui.ios.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class IosOsRoutingReceiptTest {
    private val filename = "F-OS-12345678-1234-1234-1234-123456789abc.zip"

    @Test fun onlyExactExpectedFileUrlProducesReceiptAndCountsDuplicates() {
        val state = OsRoutingReceiptState("receipt-process")
        state.expect(filename, "export-process")
        state.observe("file:///tmp/F-OS-00000000-1234-1234-1234-123456789abc.zip")
        state.observe("https://example.invalid/$filename")
        assertNull(state.receiptValue)
        state.observe("file:///tmp/$filename")
        assertEquals("$filename|count=1|process=receipt-process", state.receiptValue)
        state.observe("file:///tmp/$filename")
        assertEquals("$filename|count=2|process=receipt-process", state.receiptValue)
    }

    @Test fun expectationAndNewProvisioningCannotForgeReceiptOrReusePriorArrival() {
        val state = OsRoutingReceiptState("new-process")
        state.observe("file:///tmp/$filename")
        state.expect(filename, "old-process")
        assertNull(state.receiptValue)
        assertEquals("old-process", state.exportProcess)
        state.observe("file:///tmp/$filename")
        state.clear()
        assertNull(state.receiptValue)
        assertNull(state.expectedName)
    }

    @Test fun invalidNamesCannotArmWitness() {
        val state = OsRoutingReceiptState("process")
        for (name in listOf("../$filename", "C:$filename", "f-runtime.epub", "F-OS-invalid.zip")) {
            assertFalse(state.expect(name, "export"))
            assertNull(state.expectedName)
        }
    }
}
