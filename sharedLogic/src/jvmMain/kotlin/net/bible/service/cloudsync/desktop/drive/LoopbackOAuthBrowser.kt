package net.bible.service.cloudsync.desktop.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.oauth.OAuthBrowser
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop [OAuthBrowser]: binds a loopback port chosen by the OS, opens the consent URL through [openBrowser] and
 * waits for Google to redirect the browser back to `http://127.0.0.1:<port>/`.
 *
 * The wait ends on a redirect carrying `code` or `error`, on [timeoutMs] ([CloudSyncUserCancelledException]), on
 * coroutine cancellation (the `CancellationException` propagates) or when another [authorize] call on the same
 * instance supersedes it ([CloudSyncUserCancelledException]). The socket is closed in every case.
 * Requests without `code`/`error` (such as `/favicon.ico`) get a 404 and the wait continues.
 *
 * @param successPage HTML shown in the browser after a redirect was received.
 */
class LoopbackOAuthBrowser(
    private val openBrowser: suspend (String) -> Boolean,
    private val timeoutMs: Long = 5 * 60_000,
    private val successPage: String,
) : OAuthBrowser {
    private val current = AtomicReference<ServerSocket?>(null)

    override suspend fun authorize(buildUrl: (redirectUri: String) -> String): String {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        current.getAndSet(server)?.closeQuietly()
        try {
            val base = "http://127.0.0.1:${server.localPort}"
            return coroutineScope {
                // Accepting starts before the browser is opened, so an opener that itself blocks on the
                // redirect (or a very fast browser) is served.
                val waiter = async(Dispatchers.IO) { awaitRedirect(server, base) }
                // Cancellation of the caller leaves this block; the finally closes the socket, which
                // unblocks accept() in the child so the scope can complete.
                try {
                    if (!openBrowser(buildUrl("$base/"))) throw CloudSyncUserCancelledException()
                    withTimeoutOrNull(timeoutMs) { waiter.await() }
                } finally {
                    server.closeQuietly()
                } ?: throw CloudSyncUserCancelledException()
            }
        } finally {
            server.closeQuietly()
            current.compareAndSet(server, null)
        }
    }

    /** Blocking accept loop; returns null when the socket was closed under it. */
    private fun awaitRedirect(server: ServerSocket, base: String): String? {
        while (true) {
            val socket = try {
                server.accept()
            } catch (e: IOException) {
                if (server.isClosed) return null
                throw e
            }
            val target = socket.use { handle(it) }
            if (target != null) return base + target
        }
    }

    /** Answers one request; returns the request target if it is a redirect carrying `code` or `error`. */
    private fun handle(socket: Socket): String? {
        try {
            socket.soTimeout = 3000
            val input = socket.getInputStream()
            val requestLine = readLine(input) ?: return null
            // Drain the headers (bounded) so closing does not reset the connection under the browser.
            var headerCount = 0
            while (headerCount++ < 100) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(' ')
            val target = parts.getOrNull(1)
            val isRedirect = parts.size >= 2 && parts[0] == "GET" && target != null && target.startsWith("/") &&
                hasCodeOrError(target)
            if (isRedirect) respond(socket, "200 OK", "text/html; charset=utf-8", successPage)
            else respond(socket, "404 Not Found", "text/plain; charset=utf-8", "")
            return if (isRedirect) target else null
        } catch (_: SocketTimeoutException) {
            return null
        } catch (_: IOException) {
            return null
        }
    }

    private fun hasCodeOrError(target: String): Boolean {
        val query = target.substringAfter('?', "").substringBefore('#')
        return query.split('&').any { it.substringBefore('=') == "code" || it.substringBefore('=') == "error" }
    }

    /** Reads one CRLF/LF-terminated line (ISO-8859-1), at most 8 KiB; null at end of stream with no data. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
        throw IOException("Request line too long")
    }

    private fun respond(socket: Socket, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $status\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(head.toByteArray(Charsets.ISO_8859_1))
            write(bytes)
            flush()
        }
    }

    private fun ServerSocket.closeQuietly() {
        try { close() } catch (_: IOException) {}
    }
}
