"""Disposable, loopback-only WebDAV response for the iOS certificate checklist."""
import http.server
import pathlib
import ssl
import sys
import time
import os
from xml.sax.saxutils import escape


class Handler(http.server.BaseHTTPRequestHandler):
    def do_PUT(self):
        remaining = int(self.headers.get("Content-Length", "0"))
        while remaining:
            chunk = self.rfile.read(min(remaining, 65536))
            if not chunk:
                return
            remaining -= len(chunk)
        evidence = os.environ.get("CHECKLIST_UPLOAD_EVIDENCE") if self.path.endswith("/initial.sqlite3.gz") else None
        if evidence:
            pathlib.Path(evidence, "upload-started").touch()
        delay = min(float(os.environ.get("CHECKLIST_PUT_DELAY", "0")), 30) if evidence else 0
        if delay:
            print("CHECKLIST PUT waiting for real upload response", flush=True)
            time.sleep(delay)
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()
        if evidence:
            pathlib.Path(evidence, "upload-completed").touch()

    def do_MKCOL(self):
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_PROPFIND(self):
        path = escape(self.path)
        body = ('<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response>'
                f'<d:href>{path}</d:href><d:propstat><d:prop><d:resourcetype><d:collection/>'
                '</d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status>'
                '</d:propstat></d:response></d:multistatus>').encode()
        self.send_response(207)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def selected_port(arguments):
    """Keep the same loopback endpoint when the certificate fixture swaps listeners."""
    return int(arguments[3]) if len(arguments) > 3 else 0


if __name__ == "__main__":
    cert, key, port_file = sys.argv[1:4]
    port = selected_port(sys.argv[1:])
    server = http.server.HTTPServer(("127.0.0.1", port), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cert, key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    pathlib.Path(port_file).write_text(str(server.server_port))
    server.serve_forever()
