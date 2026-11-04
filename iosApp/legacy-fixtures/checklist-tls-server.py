"""Disposable, loopback-only WebDAV response for the iOS certificate checklist."""
import http.server
import pathlib
import ssl
import sys


class Handler(http.server.BaseHTTPRequestHandler):
    def do_PROPFIND(self):
        body = (b'<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response>'
                b'<d:href>/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/>'
                b'</d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status>'
                b'</d:propstat></d:response></d:multistatus>')
        self.send_response(207)
        self.send_header("Content-Type", "application/xml")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    cert, key, port_file = sys.argv[1:]
    server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cert, key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    pathlib.Path(port_file).write_text(str(server.server_port))
    server.serve_forever()
