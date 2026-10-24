import unittest

import merge_manifest


def entry(app, ios, scenario, sha="x"):
    return {"app": app, "ios": ios, "scenario": scenario, "path": f"{app}/{ios}/{scenario}.zip", "sha256": sha, "fingerprint": "f"}


class MergeManifestTest(unittest.TestCase):
    def test_adds_scenario_and_keeps_the_other_entries_untouched(self):
        committed = [entry("1.0.1", "18", "rich", "old"), entry("1.0.2", "26", "rich", "old")]
        new = [entry("1.0.1", "18", "rich", "NEW"), entry("1.0.1", "18", "extended"), entry("1.0.2", "26", "extended")]
        merged = merge_manifest.merge(committed, new, "extended")
        self.assertEqual([(e["app"], e["ios"], e["scenario"], e["sha256"]) for e in merged],
                         [("1.0.1", "18", "extended", "x"), ("1.0.1", "18", "rich", "old"),
                          ("1.0.2", "26", "extended", "x"), ("1.0.2", "26", "rich", "old")])

    def test_replaces_an_existing_entry_of_the_scenario(self):
        merged = merge_manifest.merge([entry("1.0.2", "26", "extended", "old")], [entry("1.0.2", "26", "extended", "NEW")], "extended")
        self.assertEqual([e["sha256"] for e in merged], ["NEW"])

    def test_missing_scenario_in_the_new_manifest_is_an_error(self):
        with self.assertRaises(SystemExit):
            merge_manifest.merge([], [entry("1.0.2", "26", "rich")], "extended")


if __name__ == "__main__":
    unittest.main()
