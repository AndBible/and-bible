package net.bible.service.cloudsync.webdav

import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals

class PinPolicyTest {
    @Test fun systemTrustedWins() = assertEquals(PinDecision.TRUST_SYSTEM, pinDecision(true, "h", "AA", null))

    @Test fun matchingPinTrusted() =
        assertEquals(PinDecision.TRUST_PINNED, pinDecision(false, "h", "AA", CertPin("h", "AA")))

    @Test fun otherHostRejected() =
        assertEquals(PinDecision.REJECT, pinDecision(false, "h", "AA", CertPin("other", "AA")))

    @Test fun otherFingerprintRejected() =
        assertEquals(PinDecision.REJECT, pinDecision(false, "h", "AA", CertPin("h", "BB")))

    @Test fun noPinRejected() = assertEquals(PinDecision.REJECT, pinDecision(false, "h", "AA", null))

    @Test fun fingerprintCaseInsensitive() =
        assertEquals(PinDecision.TRUST_PINNED, pinDecision(false, "h", "ab12", CertPin("h", "AB12")))

    @Test fun sha256HexUpperOfAbc() = assertEquals(
        "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
        sha256HexUpper("abc".encodeUtf8().toByteArray()),
    )
}
