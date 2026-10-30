#!/usr/bin/env python3
"""Keep Flathub builds on the architecture of the pinned JCEF payload."""
import json
from pathlib import Path
import unittest


class FlathubConfigTest(unittest.TestCase):
    def test_only_supported_x86_64_is_built(self):
        config = json.loads(Path(__file__).with_name('flathub.json').read_text())
        self.assertEqual(config['only-arches'], ['x86_64'])


if __name__ == '__main__':
    unittest.main()
