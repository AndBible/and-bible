package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.webdav.PinDecision
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosTrustMappingTest {
    private val info = DavCertificateInfo("h.example", "AB", "subj", "", 0, 0)
    private var pending: DavCertificateInfo? = info
    private fun take() = pending.also { pending = null }

    @Test fun dispositions() {
        assertEquals(ChallengeAction.DEFAULT_HANDLING, challengeAction(PinDecision.TRUST_SYSTEM))
        assertEquals(ChallengeAction.USE_PINNED_CREDENTIAL, challengeAction(PinDecision.TRUST_PINNED))
        assertEquals(ChallengeAction.CANCEL, challengeAction(PinDecision.REJECT))
    }

    @Test fun cancelCodeMapsAndConsumes() {
        val e = mapCertificateRejection(URL_ERROR_DOMAIN, -999, ::take)
        assertIs<DavUntrustedCertificateException>(e)
        assertEquals(info, e.certificate)
        assertNull(pending)
    }

    @Test fun certificateErrorRangeMaps() {
        for (code in -1206L..-1201L) {
            pending = info
            assertIs<DavUntrustedCertificateException>(mapCertificateRejection(URL_ERROR_DOMAIN, code, ::take))
        }
    }

    @Test fun unrelatedErrorsDoNotMapOrConsume() {
        assertNull(mapCertificateRejection(URL_ERROR_DOMAIN, -1001, ::take)) // timed out
        assertNull(mapCertificateRejection(URL_ERROR_DOMAIN, -1009, ::take)) // offline
        assertNull(mapCertificateRejection("NSPOSIXErrorDomain", -999, ::take))
        assertNull(mapCertificateRejection(null, -999, ::take))
        assertEquals(info, pending, "pending rejection untouched")
    }

    @Test fun stickyStateIsNotReported() {
        assertIs<DavUntrustedCertificateException>(mapCertificateRejection(URL_ERROR_DOMAIN, -999, ::take))
        // Same cancel-style code later, but the rejection was already consumed: not mapped.
        assertNull(mapCertificateRejection(URL_ERROR_DOMAIN, -999, ::take))
        assertNull(mapCertificateRejection(URL_ERROR_DOMAIN, -1001, ::take))
    }

    @Test fun rejectionPredicate() {
        assertTrue(isCertificateRejectionError(URL_ERROR_DOMAIN, -1202))
        assertFalse(isCertificateRejectionError(URL_ERROR_DOMAIN, -1200))
        assertFalse(isCertificateRejectionError(URL_ERROR_DOMAIN, -1207))
    }
}
