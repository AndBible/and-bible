"""Behavioral tests: real local sockets and a real TLS handshake, never an AVD."""
import contextlib
import http.client
import os
from pathlib import Path
import select
import socket
import ssl
import subprocess
import sys
import tempfile
import unittest


HERE = Path(__file__).resolve().parent
MAKE_CA = HERE.parent / "llm-stub" / "make-ca.sh"


@contextlib.contextmanager
def running_stub(root, certs, *args):
    process = subprocess.Popen(
        [sys.executable, "-I", str(HERE / "catalog_stub.py"), "--root", str(root), "--port", "0",
         "--cert", str(certs / "server.crt"), "--key", str(certs / "server.key"), *args],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    try:
        ready, _, _ = select.select([process.stdout], [], [], 5)
        line = process.stdout.readline() if ready else ""
        if not line.startswith("READY "):
            process.terminate()
            out, err = process.communicate(timeout=5)
            raise AssertionError(f"Stub did not start: {line}{out}{err}")
        yield int(line.split()[1]), process
    finally:
        process.terminate()
        process.communicate(timeout=5)


class CatalogStubTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        base = Path(cls.tmp.name)
        cls.certs = base / "certs"
        subprocess.run(["bash", str(MAKE_CA), str(cls.certs)], check=True, capture_output=True)
        cls.outer = base / "outer"
        cls.root = cls.outer / "root"
        cls.root.mkdir(parents=True)
        (cls.outer / "secret").write_bytes(b"top secret")
        cls.small = bytes(range(256)) * 4
        (cls.root / "x").write_bytes(cls.small)
        cls.big = os.urandom(200 * 1024)
        (cls.root / "big.zip").write_bytes(cls.big)
        cls.context = ssl.create_default_context(cafile=str(cls.certs / "ca.crt"))
        # The server certificate names the emulator host alias 10.0.2.2, not loopback.
        cls.context.check_hostname = False

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def request(self, port, method, path):
        conn = http.client.HTTPSConnection("127.0.0.1", port, timeout=5, context=self.context)
        try:
            conn.request(method, path)
            response = conn.getresponse()
            return response.status, response.getheader("Content-Length"), response.read()
        finally:
            conn.close()

    def test_get_serves_file_bytes(self):
        with running_stub(self.root, self.certs) as (port, _):
            status, _, body = self.request(port, "GET", "/x")
        self.assertEqual(200, status)
        self.assertEqual(self.small, body)

    def test_head_has_length_no_body(self):
        with running_stub(self.root, self.certs) as (port, _):
            status, length, body = self.request(port, "HEAD", "/x")
        self.assertEqual(200, status)
        self.assertEqual(str(len(self.small)), length)
        self.assertEqual(b"", body)

    def test_missing_is_404(self):
        with running_stub(self.root, self.certs) as (port, _):
            status, _, _ = self.request(port, "GET", "/nope")
            dir_status, _, _ = self.request(port, "GET", "/")
        self.assertEqual(404, status)
        self.assertEqual(404, dir_status)

    def test_dotdot_is_404(self):
        with running_stub(self.root, self.certs) as (port, _):
            status, _, body = self.request(port, "GET", "/../secret")
            enc_status, _, enc_body = self.request(port, "GET", "/%2e%2e/secret")
        self.assertEqual(404, status)
        self.assertEqual(404, enc_status)
        self.assertNotIn(b"top secret", body + enc_body)

    def test_each_request_logged_once(self):
        with running_stub(self.root, self.certs) as (port, process):
            self.request(port, "GET", "/x")
            self.request(port, "GET", "/x")
            process.terminate()
            out, _ = process.communicate(timeout=5)
        lines = [line for line in out.splitlines() if not line.startswith("READY")]
        self.assertEqual([f"GET /x 200 {len(self.small)}"] * 2, lines)

    def test_stall_path_sends_first_chunk_then_holds(self):
        with running_stub(self.root, self.certs, "--stall", "/big.zip") as (port, _):
            raw = socket.create_connection(("127.0.0.1", port), timeout=5)
            tls = self.context.wrap_socket(raw, server_hostname="10.0.2.2")
            try:
                tls.sendall(b"GET /big.zip HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
                tls.settimeout(2)
                data = b""
                with self.assertRaises(socket.timeout):
                    while True:
                        chunk = tls.recv(65536)
                        self.assertTrue(chunk, "server closed instead of holding")
                        data += chunk
                        # Another path is served in full while this one is stalled.
                        if b"\r\n\r\n" in data and getattr(self, "_other", None) is None:
                            self._other = self.request(port, "GET", "/x")
                header, _, body = data.partition(b"\r\n\r\n")
                self.assertIn(f"Content-Length: {len(self.big)}".encode(), header)
                self.assertEqual(65536, len(body))
                self.assertEqual(self.big[:65536], body)
                self.assertEqual((200, str(len(self.small)), self.small), self._other)
            finally:
                self._other = None
                tls.close()


if __name__ == "__main__":
    unittest.main()
