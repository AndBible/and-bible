#!/usr/bin/env python3
"""Add verified SHA-256 pins to Flatpak URL sources."""
import concurrent.futures
import hashlib
import json
import os
import sys
import tempfile
import urllib.request

path = sys.argv[1]
with open(path, encoding="utf-8") as source:
    entries = json.load(source)
urls = {item["url"] for item in entries if "url" in item}

def digest(url):
    sha256, sha512 = hashlib.sha256(), hashlib.sha512()
    with urllib.request.urlopen(url, timeout=20) as response:
        while chunk := response.read(1024 * 1024):
            sha256.update(chunk)
            sha512.update(chunk)
    return url, (sha256.hexdigest(), sha512.hexdigest())

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    hashes = dict(pool.map(digest, sorted(urls)))
for item in entries:
    if "url" not in item:
        continue
    sha256, sha512 = hashes[item["url"]]
    if item.get("sha512", sha512).lower() != sha512 or item.get("sha256", sha256).lower() != sha256:
        raise ValueError(f"Hash mismatch: {item['url']}")
    item["sha256"] = sha256
fd, temporary = tempfile.mkstemp(dir=os.path.dirname(os.path.abspath(path)))
try:
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        json.dump(entries, output, indent=4, ensure_ascii=False)
        output.write("\n")
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
