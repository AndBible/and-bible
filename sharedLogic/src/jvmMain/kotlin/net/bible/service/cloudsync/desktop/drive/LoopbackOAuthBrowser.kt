package net.bible.service.cloudsync.desktop.drive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop [OAuthBrowser]: binds a loopback port chosen by the OS, opens the consent URL through [openBrowser] and
 * waits for Google to redirect the browser back to `http://127.0.0.1:<port>/`.
 *
 * The wait ends on a redirect carrying `code` or `error`, on [timeoutMs] ([CloudSyncUserCancelledException]), on
 * coroutine cancellation (the `CancellationException` propagates) or when another [authorize] call in this JVM supersedes it ([CloudSyncUserCancelledException]). The socket is closed in every case.
 * Requests without `code`/`error` (such as `/favicon.ico`) get a 404 and the wait continues.
 *
 * @param successPage HTML shown in the browser after a redirect carrying `code` was received.
 * @param errorPage HTML shown after a redirect carrying `error` (such as `access_denied`); defaults to [successPage].
 */
class LoopbackOAuthBrowser(
    private val openBrowser: suspend (String) -> Boolean,
    private val timeoutMs: Long = 5 * 60_000,
    private val successPage: String,
    private val errorPage: String = successPage,
) : OAuthBrowser {

    /** One authorize call: its listening socket, its accepted clients and the redirect it waits for. */
    private class Attempt(val server: ServerSocket) {
        val result = CompletableDeferred<String>()
        val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

        fun supersede() {
            result.completeExceptionally(CloudSyncUserCancelledException())
            close()
        }

        fun close() {
            try { server.close() } catch (_: IOException) {}
            clients.forEach { try { it.close() } catch (_: IOException) {} }
        }
    }

    override suspend fun authorize(buildUrl: (redirectUri: String) -> String): String {
        val attempt = Attempt(ServerSocket(0, 1, LOOPBACK))
        current.getAndSet(attempt)?.supersede()
        try {
            val base = "http://127.0.0.1:${attempt.server.localPort}"
            return coroutineScope {
                // Accepting starts before the browser is opened, so an opener that itself blocks on the
                // redirect (or a very fast browser) is served.
                launch(Dispatchers.IO) { acceptLoop(attempt, base, this) }
                try {
                    withTimeoutOrNull(timeoutMs) {
                        if (!openBrowser(buildUrl("$base/"))) throw CloudSyncUserCancelledException()
                        attempt.result.await()
                    }
                } finally {
                    // Unblocks accept() and any stalled handler so the scope can complete.
                    attempt.close()
                } ?: throw CloudSyncUserCancelledException()
            }
        } finally {
            attempt.close()
            current.compareAndSet(attempt, null)
        }
    }

    /** Blocking accept loop; every connection is served on its own coroutine so a silent one blocks nobody. */
    private fun acceptLoop(attempt: Attempt, base: String, scope: CoroutineScope) {
        while (true) {
            val socket = try {
                attempt.server.accept()
            } catch (e: IOException) {
                if (attempt.server.isClosed) return
                throw e
            }
            attempt.clients.add(socket)
            if (attempt.server.isClosed) { socket.close(); return }
            scope.launch(Dispatchers.IO) {
                try {
                    val target = socket.use { handle(it) }
                    if (target != null) attempt.result.complete(base + target)
                } finally {
                    attempt.clients.remove(socket)
                }
            }
        }
    }

    /** Answers one request within [REQUEST_DEADLINE_MS] in total; returns the target if it carries `code`/`error`. */
    private fun handle(socket: Socket): String? {
        try {
            val deadline = System.nanoTime() + REQUEST_DEADLINE_MS * 1_000_000
            val input = socket.getInputStream()
            val requestLine = readLine(socket, input, deadline) ?: return null
            // Drain the headers (bounded) so closing does not reset the connection under the browser.
            var headerCount = 0
            while (headerCount++ < 100) {
                val line = readLine(socket, input, deadline) ?: break
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(' ')
            val target = parts.getOrNull(1)
            val isRedirect = parts.size >= 2 && parts[0] == "GET" && target != null && target.startsWith("/") &&
                hasCodeOrError(target)
            if (isRedirect) respond(socket, "200 OK", "text/html; charset=utf-8", if (isErrorRedirect(target!!)) errorPage else successPage)
            else respond(socket, "404 Not Found", "text/plain; charset=utf-8", "")
            return if (isRedirect) target else null
        } catch (_: IOException) {
            return null
        }
    }

    private fun isErrorRedirect(target: String): Boolean {
        val query = target.substringAfter('?', "").substringBefore('#')
        return query.split('&').any { it.substringBefore('=') == "error" }
    }

    private fun hasCodeOrError(target: String): Boolean {
        val query = target.substringAfter('?', "").substringBefore('#')
        return query.split('&').any { it.substringBefore('=') == "code" || it.substringBefore('=') == "error" }
    }

    /** Reads one CRLF/LF-terminated line (ISO-8859-1), at most 8 KiB, before [deadline] (nanoTime). */
    private fun readLine(socket: Socket, input: InputStream, deadline: Long): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val remainingMs = (deadline - System.nanoTime()) / 1_000_000
            if (remainingMs <= 0) throw SocketTimeoutException("Request deadline exceeded")
            socket.soTimeout = remainingMs.toInt()
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

    private companion object {
        val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        const val REQUEST_DEADLINE_MS = 5000L

        /** The attempt in flight in this JVM; a new authorize supersedes it. */
        val current = AtomicReference<Attempt?>(null)
    }
}
