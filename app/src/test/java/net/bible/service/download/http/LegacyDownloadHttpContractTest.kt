package net.bible.service.download.http

import net.bible.sharedcore.http.DownloadHttp

class LegacyDownloadHttpContractTest : DownloadHttpContract() {
    override val legacy = true
    override fun http(server: TestHttpsServer, timeoutMs: Int): DownloadHttp = LegacyUrlConnectionDownloadHttp()
    override fun <T> withTrust(server: TestHttpsServer, block: () -> T): T = server.withLegacyTrust(block)
}
