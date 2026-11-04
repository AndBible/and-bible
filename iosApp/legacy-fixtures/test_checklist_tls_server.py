"""Controls for the disposable TLS server used by simulator acceptance."""
import importlib.util
import pathlib
import unittest

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


if __name__ == "__main__":
    unittest.main()
