#!/usr/bin/env python3
"""Point the AUR PKGBUILD at another GitHub release.

Usage: update-pkgbuild.py <tag> [--pkgrel N] [--no-srcinfo]

<tag> is a release tag such as production-1300, beta-1290 or test-1254. The script reads the release from the
GitHub API, takes the version from its name ("Release 6.0.1300"), the tarball's SHA-256 from the asset digest,
hashes the .desktop and metainfo files at that tag, rewrites _tag, pkgver, pkgrel and sha256sums in the PKGBUILD
next to this script, and regenerates .SRCINFO with makepkg (skipped with --no-srcinfo or when makepkg is missing;
then run `makepkg --printsrcinfo > .SRCINFO` on an Arch machine).

GITHUB_TOKEN is used when set (the anonymous API limit is 60 requests an hour). Run with `python3 -I`.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

REPO = "AndBible/and-bible"
TAG_RE = re.compile(r"^(production|beta|test)-\d+$")
VERSION_RE = re.compile(r"\b(\d+\.\d+\.\d+)\b")
RAW_FILES = (
    "desktopApp/packaging/linux/org.andbible.AndBible.desktop",
    "desktopApp/packaging/linux/org.andbible.AndBible.metainfo.xml",
)


def fetch(url: str, accept: str) -> bytes:
    headers = {"Accept": accept, "User-Agent": "andbible-aur-update"}
    token = os.environ.get("GITHUB_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as response:
        return response.read()


def release_facts(tag: str, fetcher=fetch) -> tuple[str, str]:
    """The version in the release name and the SHA-256 GitHub reports for the Linux tarball."""
    data = json.loads(fetcher(f"https://api.github.com/repos/{REPO}/releases/tags/{tag}", "application/vnd.github+json"))
    match = VERSION_RE.search(data.get("name") or "")
    if not match:
        raise SystemExit(f"Release {tag} has no version in its name: {data.get('name')!r}")
    asset_name = f"andbible-desktop-{tag}-linux-x64.tar.gz"
    asset = next((a for a in data.get("assets", []) if a.get("name") == asset_name), None)
    if asset is None:
        raise SystemExit(f"Release {tag} has no asset {asset_name}")
    digest = asset.get("digest") or ""
    if not digest.startswith("sha256:") or not re.fullmatch(r"[0-9a-f]{64}", digest[7:]):
        raise SystemExit(f"Asset {asset_name} has no sha256 digest: {digest!r}")
    return match.group(1), digest[7:]


def raw_file_hashes(tag: str, fetcher=fetch) -> list[str]:
    return [hashlib.sha256(fetcher(f"https://raw.githubusercontent.com/{REPO}/{tag}/{path}", "*/*")).hexdigest()
            for path in RAW_FILES]


def rewrite_pkgbuild(text: str, tag: str, version: str, pkgrel: int, sha256sums: list[str]) -> str:
    """Replace the release fields of a PKGBUILD. Everything else, comments included, stays byte for byte."""
    def set_field(src: str, name: str, value: str) -> str:
        pattern = re.compile(rf"^{re.escape(name)}=.*$", re.MULTILINE)
        if len(pattern.findall(src)) != 1:
            raise SystemExit(f"Expected exactly one {name}= line in the PKGBUILD")
        return pattern.sub(f"{name}={value}", src)

    text = set_field(text, "_tag", tag)
    text = set_field(text, "pkgver", version)
    text = set_field(text, "pkgrel", str(pkgrel))
    sums = re.compile(r"^sha256sums=\(\n(?:  '[^']*'\n)+\)$", re.MULTILINE)
    if len(sums.findall(text)) != 1:
        raise SystemExit("Expected exactly one multi-line sha256sums=( ... ) block in the PKGBUILD")
    block = "sha256sums=(\n" + "".join(f"  '{s}'\n" for s in sha256sums) + ")"
    return sums.sub(lambda _: block, text)


def write_srcinfo(pkgbuild_dir: Path) -> bool:
    makepkg = shutil.which("makepkg")
    if makepkg is None:
        return False
    srcinfo = subprocess.run([makepkg, "--printsrcinfo"], cwd=pkgbuild_dir, check=True, capture_output=True, text=True).stdout
    (pkgbuild_dir / ".SRCINFO").write_text(srcinfo)
    return True


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("tag")
    parser.add_argument("--pkgrel", type=int, default=1, help="package release number (default 1, a new upstream version)")
    parser.add_argument("--no-srcinfo", action="store_true", help="do not regenerate .SRCINFO")
    args = parser.parse_args(argv)
    if not TAG_RE.match(args.tag):
        parser.error("tag must look like production-1300, beta-1290 or test-1254")

    pkgbuild_dir = Path(__file__).resolve().parent
    pkgbuild = pkgbuild_dir / "PKGBUILD"
    version, tarball_sha = release_facts(args.tag)
    sums = [tarball_sha, *raw_file_hashes(args.tag)]
    pkgbuild.write_text(rewrite_pkgbuild(pkgbuild.read_text(), args.tag, version, args.pkgrel, sums))
    print(f"PKGBUILD: {args.tag} -> pkgver={version} pkgrel={args.pkgrel} tarball sha256 {tarball_sha}")
    if args.no_srcinfo:
        return 0
    if write_srcinfo(pkgbuild_dir):
        print(".SRCINFO regenerated")
    else:
        print("makepkg not found: run `makepkg --printsrcinfo > .SRCINFO` in packaging/arch on an Arch machine", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
