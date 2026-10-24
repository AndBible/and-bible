#!/usr/bin/env python3
"""Local HTTPS static file server for repository catalog fixtures; no upstream requests."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import select
import ssl
from urllib.parse import unquote

STALL_BYTES = 65536
CHUNK = 65536


class CatalogHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        # Requests are logged once, by `log_request_line`, in a fixed format.
        pass

    def log_request_line(self, status, sent):
        print(f"{self.command} {self.path} {status} {sent}", flush=True)

    def resolve(self):
        """File under --root for the request path, or None (missing, directory, traversal)."""
        raw = unquote(self.path.split("?", 1)[0].split("#", 1)[0])
        parts = raw.split("/")
        if ".." in parts or "\0" in raw:
            return None
        root = self.server.root
        target = (root / raw.lstrip("/")).resolve()
        if root != target and root not in target.parents:
            return None
        return target if target.is_file() else None

    def send_not_found(self):
        body = b"Not found\n"
        self.send_response(404)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)
        self.log_request_line(404, 0 if self.command == "HEAD" else len(body))

    def serve(self):
        target = self.resolve()
        if target is None:
            self.send_not_found()
            return
        size = target.stat().st_size
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(size))
        self.end_headers()
        if self.command == "HEAD":
            self.log_request_line(200, 0)
            return
        path = self.path.split("?", 1)[0]
        stall = path in self.server.stall
        limit = min(size, STALL_BYTES) if stall else size
        sent = 0
        try:
            with target.open("rb") as handle:
                while sent < limit:
                    data = handle.read(min(CHUNK, limit - sent))
                    if not data:
                        break
                    self.wfile.write(data)
                    sent += len(data)
            self.wfile.flush()
            self.log_request_line(200, sent)
            if stall and sent < size:
                self.hold_until_disconnect()
        except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
            self.log_request_line(200, sent)
        self.close_connection = True

    def hold_until_disconnect(self):
        """Send nothing more; return once the client closes or resets the connection."""
        sock = self.connection
        while True:
            readable, _, _ = select.select([sock], [], [], 0.5)
            if not readable:
                continue
            try:
                if not sock.recv(1):
                    return
            except (OSError, ssl.SSLError):
                return

    do_GET = serve
    do_HEAD = serve


class CatalogServer(ThreadingHTTPServer):
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    cert_dir = Path(__file__).resolve().parents[2] / ".local/llm-stub/certs"
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--cert", type=Path, default=cert_dir / "server.crt")
    parser.add_argument("--key", type=Path, default=cert_dir / "server.key")
    parser.add_argument("--stall", action="append", default=[], metavar="URL_PATH",
                        help="send headers and the first 64 KiB of this path, then hold the connection")
    options = parser.parse_args()
    if not options.root.is_dir():
        parser.error(f"--root is not a directory: {options.root}")
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    try:
        context.load_cert_chain(options.cert, options.key)
    except (OSError, ssl.SSLError) as error:
        parser.error(f"TLS certificate unavailable; run scripts/llm-stub/make-ca.sh first: {error}")
    with CatalogServer((options.host, options.port), CatalogHandler) as server:
        server.root = options.root.resolve()
        server.stall = set(options.stall)
        server.socket = context.wrap_socket(server.socket, server_side=True)
        print(f"READY {server.server_port}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
