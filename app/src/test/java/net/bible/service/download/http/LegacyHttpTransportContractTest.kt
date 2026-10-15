package net.bible.service.download.http

import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.UrlConnectionHttpTransport

class LegacyHttpTransportContractTest : HttpTransportContract() {
    override val legacy = true
    override fun transport(server: TestHttpsServer): HttpTransport = UrlConnectionHttpTransport()
    override fun <T> withTrust(server: TestHttpsServer, block: () -> T): T = server.withLegacyTrust(block)
}
