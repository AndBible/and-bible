#!/usr/bin/env python3
"""Offline checks for update-pkgbuild.py: `python3 -I packaging/arch/test-update-pkgbuild.py`."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("update_pkgbuild", HERE / "update-pkgbuild.py")
update_pkgbuild = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(update_pkgbuild)

SHA_A = "a" * 64
SHA_B = "b" * 64
SHA_C = "c" * 64


class RewritePkgbuildTest(unittest.TestCase):
    def test_rewrites_only_the_release_fields_of_the_real_pkgbuild(self) -> None:
        original = (HERE / "PKGBUILD").read_text()
        updated = update_pkgbuild.rewrite_pkgbuild(original, "production-1300", "6.1.1300", 2, [SHA_A, SHA_B, SHA_C])
        self.assertIn("\n_tag=production-1300\n", updated)
        self.assertIn("\npkgver=6.1.1300\n", updated)
        self.assertIn("\npkgrel=2\n", updated)
        self.assertIn(f"sha256sums=(\n  '{SHA_A}'\n  '{SHA_B}'\n  '{SHA_C}'\n)\n", updated)
        # Every other line is untouched: the diff is exactly the three fields and the checksum block.
        changed = [(a, b) for a, b in zip(original.splitlines(), updated.splitlines()) if a != b]
        self.assertEqual(len(original.splitlines()), len(updated.splitlines()))
        self.assertEqual({a.split("=")[0] for a, _ in changed if "=" in a and not a.startswith("  '")},
                         {"_tag", "pkgver", "pkgrel"})
        self.assertTrue(all(a.startswith("  '") for a, _ in changed if "=" not in a))

    def test_refuses_a_pkgbuild_with_an_ambiguous_field(self) -> None:
        text = "pkgver=1\npkgver=2\n_tag=x\npkgrel=1\nsha256sums=(\n  'x'\n)\n"
        with self.assertRaises(SystemExit):
            update_pkgbuild.rewrite_pkgbuild(text, "test-1", "1.0.1", 1, [SHA_A])

    def test_refuses_a_single_line_checksum_block(self) -> None:
        text = "_tag=x\npkgver=1\npkgrel=1\nsha256sums=('x')\n"
        with self.assertRaises(SystemExit):
            update_pkgbuild.rewrite_pkgbuild(text, "test-1", "1.0.1", 1, [SHA_A])


class ReleaseFactsTest(unittest.TestCase):
    def release(self, name: str, assets: list[dict]) -> bytes:
        return json.dumps({"name": name, "assets": assets}).encode()

    def test_takes_version_from_name_and_digest_from_the_linux_tarball(self) -> None:
        payload = self.release("Release 6.1.1300", [
            {"name": "andbible-desktop-production-1300-x64.msi", "digest": f"sha256:{SHA_B}"},
            {"name": "andbible-desktop-production-1300-linux-x64.tar.gz", "digest": f"sha256:{SHA_A}"},
        ])
        version, sha = update_pkgbuild.release_facts("production-1300", fetcher=lambda url, accept: payload)
        self.assertEqual(("6.1.1300", SHA_A), (version, sha))

    def test_rejects_a_release_without_the_tarball(self) -> None:
        payload = self.release("Test release 6.0.1254", [{"name": "andbible-test-1254.apk", "digest": f"sha256:{SHA_A}"}])
        with self.assertRaises(SystemExit):
            update_pkgbuild.release_facts("test-1254", fetcher=lambda url, accept: payload)

    def test_rejects_an_asset_without_a_sha256_digest(self) -> None:
        payload = self.release("Test release 6.0.1254", [{"name": "andbible-desktop-test-1254-linux-x64.tar.gz"}])
        with self.assertRaises(SystemExit):
            update_pkgbuild.release_facts("test-1254", fetcher=lambda url, accept: payload)

    def test_rejects_a_release_name_without_a_version(self) -> None:
        payload = self.release("Nightly", [{"name": "andbible-desktop-test-1-linux-x64.tar.gz", "digest": f"sha256:{SHA_A}"}])
        with self.assertRaises(SystemExit):
            update_pkgbuild.release_facts("test-1", fetcher=lambda url, accept: payload)

    def test_raw_file_hashes_follow_the_tag(self) -> None:
        seen: list[str] = []

        def fetcher(url: str, accept: str) -> bytes:
            seen.append(url)
            return url.encode()

        hashes = update_pkgbuild.raw_file_hashes("beta-1290", fetcher=fetcher)
        self.assertEqual([hashlib.sha256(u.encode()).hexdigest() for u in seen], hashes)
        self.assertTrue(all("/beta-1290/desktopApp/packaging/linux/" in u for u in seen))
        self.assertEqual(2, len(seen))


class ValidPkgbuildTest(unittest.TestCase):
    def test_pkgbuild_source_and_checksum_counts_match(self) -> None:
        text = (HERE / "PKGBUILD").read_text()
        sources = text[text.index("source=(") : text.index(")", text.index("source=("))].count("::")
        sums = text[text.index("sha256sums=(") : text.index(")", text.index("sha256sums=("))].count("'") // 2
        self.assertEqual(3, sources)
        self.assertEqual(sources, sums)

    def test_srcinfo_matches_pkgbuild_version(self) -> None:
        pkgbuild = (HERE / "PKGBUILD").read_text()
        srcinfo = (HERE / ".SRCINFO").read_text()
        for field in ("pkgver", "pkgrel"):
            value = next(l.split("=", 1)[1] for l in pkgbuild.splitlines() if l.startswith(f"{field}="))
            self.assertIn(f"\t{field} = {value}\n", srcinfo, f"{field} differs between PKGBUILD and .SRCINFO")


if __name__ == "__main__":
    sys.exit(unittest.main(verbosity=1).result.wasSuccessful() is False)
