package net.bible.service.cloudsync.ios

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import net.bible.service.cloudsync.host.errorReportAction

class ErrorReportActionTest {
    @Test fun errorDialogBackupNavigates() = runTest {
        var route: String? = null
        errorReportAction("backup", null, { error("Must not report") }, { route = net.bible.sharedcore.nav.NavRoutes.BACKUP })
        assertEquals(net.bible.sharedcore.nav.NavRoutes.BACKUP, route)
    }
    @Test fun reportCarriesCauseAndSkipDoesNothing() = runTest {
        val cause = IllegalStateException("failure")
        var sent: Throwable? = null
        errorReportAction("report", cause, { sent = it }, { error("Must not backup") })
        assertSame(cause, sent)
        errorReportAction(null, cause, { error("Must not report") }, { error("Must not backup") })
    }
}
