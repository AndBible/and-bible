#!/usr/bin/env python3
"""Package generated legacy stores and oracle outputs into fixture zips plus manifest.json.

Usage: package_fixtures.py <gen-root> <oracle-root> <out-root>

Layout in:   <gen-root>/<app>/<ios>/<scenario>/AndBible.store ...
             <oracle-root>/<app>/<ios>/<scenario>/oracle/{report.json,...}
Layout out:  <out-root>/<app>/<ios>/<scenario>.zip   (store files at the root, oracle files under oracle/)
             <out-root>/manifest.json                [{app, ios, scenario, path, sha256, fingerprint}]

Zips are deterministic (sorted entries, fixed timestamps) so a rerun over the same inputs
produces the same sha256. A scenario directory without AndBible.store is skipped.
"""
import hashlib
import json
import os
import sys
import zipfile

FIXED_TIME = (2026, 1, 1, 0, 0, 0)


def _files(folder):
    out = []
    for dirpath, _, names in os.walk(folder):
        for name in names:
            full = os.path.join(dirpath, name)
            out.append((os.path.relpath(full, folder).replace(os.sep, "/"), full))
    return sorted(out)


def _subdirs(path):
    return sorted(d for d in os.listdir(path) if os.path.isdir(os.path.join(path, d))) if os.path.isdir(path) else []


def package(root_in, root_oracle, root_out):
    manifest = []
    for app in _subdirs(root_in):
        for ios in _subdirs(os.path.join(root_in, app)):
            base = os.path.join(root_in, app, ios)
            for scenario in _subdirs(base):
                src = os.path.join(base, scenario)
                if not os.path.isfile(os.path.join(src, "AndBible.store")):
                    continue
                oracle = os.path.join(root_oracle, app, ios, scenario, "oracle")
                dest_dir = os.path.join(root_out, app, ios)
                os.makedirs(dest_dir, exist_ok=True)
                zip_path = os.path.join(dest_dir, scenario + ".zip")
                with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
                    for folder, prefix in ((src, ""), (oracle, "oracle/")):
                        if not os.path.isdir(folder):
                            continue
                        for rel, full in _files(folder):
                            info = zipfile.ZipInfo(prefix + rel, FIXED_TIME)
                            info.compress_type = zipfile.ZIP_DEFLATED
                            info.external_attr = 0o644 << 16
                            with open(full, "rb") as f:
                                z.writestr(info, f.read())
                fingerprint = None
                report = os.path.join(oracle, "report.json")
                if os.path.isfile(report):
                    with open(report) as f:
                        fingerprint = json.load(f).get("fingerprint")
                with open(zip_path, "rb") as f:
                    sha = hashlib.sha256(f.read()).hexdigest()
                manifest.append({"app": app, "ios": ios, "scenario": scenario,
                                 "path": f"{app}/{ios}/{scenario}.zip", "sha256": sha,
                                 "fingerprint": fingerprint})
    os.makedirs(root_out, exist_ok=True)
    with open(os.path.join(root_out, "manifest.json"), "w") as f:
        json.dump(manifest, f, indent=2, sort_keys=True)
        f.write("\n")
    return manifest


def main(argv):
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    print(json.dumps(package(argv[1], argv[2], argv[3]), indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
