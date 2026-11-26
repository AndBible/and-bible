import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


HERE = Path(__file__).resolve().parent
NS = {'w': 'http://schemas.microsoft.com/wix/2006/wi'}


class MsiPolicyTest(unittest.TestCase):
    def test_open_with_candidates_are_owned_and_do_not_claim_defaults(self):
        root = ET.parse(HERE / 'jpackage-resources/ui.wxf').getroot()
        components = root.findall('.//w:Component', NS)
        values = [(component, value) for component in components
                  for value in component.findall('w:RegistryValue', NS)]
        candidates = [(component, value) for component, value in values
                      if value.get('Key', '').endswith('\\OpenWithProgids')]
        expected_candidates = {
            r'Software\Classes\.epub\OpenWithProgids',
            r'Software\Classes\.sqlite3\OpenWithProgids',
            r'Software\Classes\.zip\OpenWithProgids',
            r'Software\Classes\.csv\OpenWithProgids',
            r'Software\Classes\.jpg\OpenWithProgids',
            r'Software\Classes\.jpeg\OpenWithProgids',
            r'Software\Classes\.png\OpenWithProgids',
            r'Software\Classes\.webp\OpenWithProgids',
            r'Software\Classes\.ttf\OpenWithProgids',
        }
        self.assertEqual(expected_candidates, {value.get('Key') for _, value in candidates})
        for component, value in candidates:
            self.assertEqual('yes', component.get('Win64'))
            self.assertEqual('HKCU', value.get('Root'))
            self.assertEqual('AndBible.Document', value.get('Name'))
            self.assertEqual('yes', value.get('KeyPath'))
            self.assertIn(component.get('Id'),
                          {ref.get('Id') for ref in root.findall('.//w:FeatureRef/w:ComponentRef', NS)})
        icons = [value for _, value in values if value.get('Key') ==
                 r'Software\Classes\AndBible.Document\DefaultIcon']
        self.assertEqual(1, len(icons))
        self.assertEqual('"[INSTALLDIR]AndBible.exe",0', icons[0].get('Value'))
        command = [value for _, value in values if value.get('Key') ==
                   r'Software\Classes\AndBible.Document\shell\open\command']
        self.assertEqual(1, len(command))
        self.assertEqual('HKCU', command[0].get('Root'))
        self.assertEqual('"[INSTALLDIR]AndBible.exe" "%1"', command[0].get('Value'))
        self.assertTrue(any(value.get('Key') == r'Software\Classes\AndBible.Document\Application'
                            and value.get('Name') == 'ApplicationName' and value.get('Value') == 'AndBible'
                            for _, value in values))
        self.assertFalse(root.findall('.//w:Extension', NS))
        for _, value in values:
            key = value.get('Key', '')
            self.assertFalse(key in {fr'Software\Classes\.{extension}' for extension in
                                     ('zip', 'epub', 'sqlite3', 'csv', 'jpg', 'jpeg', 'png', 'webp', 'ttf')}
                             and value.get('Name', '') in {'', '@'})


if __name__ == '__main__':
    unittest.main()
