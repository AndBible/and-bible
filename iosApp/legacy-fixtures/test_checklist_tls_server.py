"""Controls for the disposable TLS server used by simulator acceptance."""
import importlib.util
import pathlib
import unittest
import http.client
import http.server
import threading

MODULE = pathlib.Path(__file__).with_name("checklist-tls-server.py")
spec = importlib.util.spec_from_file_location("checklist_tls_server", MODULE)
server_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server_module)


class PortSelectionTest(unittest.TestCase):
    def test_explicit_replacement_port_is_preserved(self):
        self.assertEqual(server_module.selected_port(["cert", "key", "marker", "18473"]), 18473)

    def test_initial_listener_uses_ephemeral_port(self):
        self.assertEqual(server_module.selected_port(["cert", "key", "marker"]), 0)

    def test_invalid_port_fails_instead_of_binding_random_port(self):
        with self.assertRaises(ValueError):
            server_module.selected_port(["cert", "key", "marker", "invalid"])


class DavFolderTest(unittest.TestCase):
    def test_put_payload_is_consumed_and_reported_created(self):
        server = http.server.HTTPServer(("127.0.0.1", 0), server_module.Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
        try:
            connection.request("PUT", "/sync/initial.gz", body=b"synthetic-database-bytes")
            response = connection.getresponse()
            self.assertEqual(response.status, 201)
            response.read()
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            worker.join(timeout=2)

    def test_create_collection_and_listing_returns_requested_folder(self):
        server = http.server.HTTPServer(("127.0.0.1", 0), server_module.Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
        try:
            connection.request("MKCOL", "/documents/")
            response = connection.getresponse()
            self.assertEqual(response.status, 201)
            response.read()
            connection.request("PROPFIND", "/documents/")
            response = connection.getresponse()
            self.assertEqual(response.status, 207)
            self.assertIn(b"<d:href>/documents/</d:href>", response.read())
        finally:
            connection.close()
            server.shutdown()
            server.server_close()
            worker.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
