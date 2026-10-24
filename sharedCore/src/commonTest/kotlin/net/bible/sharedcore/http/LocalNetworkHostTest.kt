package net.bible.sharedcore.http

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkHostTest {
    @Test fun classifiesHosts() {
        listOf("localhost", "127.0.0.1", "10.0.0.2", "172.16.0.1", "172.31.255.1",
            "192.168.1.5", "169.254.1.1", "NAS.local", "[::1]", "fe80::1").forEach {
            assertTrue(LocalNetworkHost.isLocal(it), it)
        }
        listOf("172.32.0.1", "example.org", "8.8.8.8", "local.example.org", "").forEach {
            assertFalse(LocalNetworkHost.isLocal(it), it)
        }
    }

    @Test fun subnetBoundariesAndCase() {
        listOf("LOCALHOST", "a.b.LOCAL", "127.0.0.0", "127.255.255.255", "10.0.0.0",
            "10.255.255.255", "172.16.0.0", "172.31.255.255", "192.168.0.0",
            "192.168.255.255", "169.254.0.0", "169.254.255.255", "FE80::", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "0:0:0:0:0:0:0:1").forEach { assertTrue(LocalNetworkHost.isLocal(it), it) }
        listOf("126.255.255.255", "128.0.0.0", "9.255.255.255", "11.0.0.0",
            "172.15.255.255", "192.167.255.255", "192.169.0.0", "169.253.255.255",
            "169.255.0.0", "fe7f::1", "fec0::1", "::", "::2", "fc00::1",
            "localhost.example.org", "notlocal", ".local").forEach { assertFalse(LocalNetworkHost.isLocal(it), it) }
    }

    @Test fun rejectsAmbiguousLeadingZeroIpv4Components() {
        listOf("010.0.0.1", "0127.0.0.1", "127.00.0.1", "10.0.00.1", "192.168.1.005",
            "172.016.0.1", "169.254.01.1").forEach { assertFalse(LocalNetworkHost.isLocal(it), it) }
        listOf("10.0.0.1", "127.0.0.1", "192.168.1.5").forEach { assertTrue(LocalNetworkHost.isLocal(it), it) }
    }

    @Test fun rejectsMalformedNumericHostsInsteadOfMatchingPrefixes() {
        listOf("127.0.0.256", "10.999.1.1", "192.168.-1.1", "172.16.0", "127.1",
            "127.0.0.1.evil.org", "10.0.0.1x", "10..0.1", "10.0.0.1.2", "+127.0.0.1",
            "0x7f000001", "2130706433", "fe80::xyz", "fe80:::1", "fe80::1::2",
            "fe80:1", "[::1", "::1]", "[::1]:80", "fe80:0:0:0:0:0:0:0:1",
            "fe80:10000::1", "fe80::1.evil.org").forEach { assertFalse(LocalNetworkHost.isLocal(it), it) }
    }
}
