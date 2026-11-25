#!/usr/bin/env python3
"""Split the Linux app image into a small per-release "app" archive and a large, rarely changing "base" archive.

Distribution packagers (e.g. the AUR) can ship the two as separate packages, so a user downloads the Java runtime,
the CEF natives and the third-party jars only when one of them changes, not with every release.

- app: everything that changes with each build: the launcher (`bin/`, `lib/libapplauncher.so`), the launcher
  config `lib/app/andbible.cfg` (it names every jar), the icon, our own jars (`--own-jar`), the jars Compose
  rewrites on every build (PER_BUILD_JAR), the `--extra` files and `lib/base-id`.
- base: the rest: `lib/runtime` (the jlinked JRE), `lib/app/resources/kcef-natives` (CEF) and third-party jars.

The base is named by its base id, a hash of its paths, modes and contents, so a packager sees whether it changed.
The app archive's `lib/base-id` names the base it was built against. Both archives are reproducible (sorted, mtime
0, root-owned), so the same base gives byte-identical archives in every release.

Usage:
  split-app-image.py --image <dir with bin/ and lib/> --out <dir> --version <6.0.1284>
                     --own-jar <file name, e.g. sharedUi-jvm.jar> [...] [--extra <source>=<archive path> ...]
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tarfile

# Compose rewrites this jar on every build, so its content hash (and the hashed name jpackage gives it) differs in
# every release even though its version does not; in base it would make every base new.
PER_BUILD_JAR = re.compile(r'^lib/app/skiko-awt-runtime-[^/]*\.jar$')
APP_FIXED = re.compile(r'^(bin/[^/]+|lib/libapplauncher\.so|lib/andbible\.png|lib/app/andbible\.cfg)$')
BASE_ID_PATH = 'lib/base-id'


def own_jar_pattern(file_name):
    """jpackage (via Compose) copies `<stem>.jar` into lib/app as `<stem>-<hex content hash>.jar`."""
    stem = file_name[:-4] if file_name.endswith('.jar') else file_name
    return re.compile(r'^lib/app/' + re.escape(stem) + r'-[0-9a-f]+\.jar$')


def image_entries(image):
    """Relative paths of every file and symlink in the image, sorted."""
    entries = []
    for root, dirs, files in os.walk(image):
        dirs.sort()
        for name in sorted(dirs + files):
            path = Path(root, name)
            if path.is_symlink() or path.is_file():
                entries.append(path.relative_to(image).as_posix())
    return sorted(entries)


def classify(entries, own_jars):
    """Returns (app, base) path lists. Fails when an own jar does not match exactly one image file."""
    patterns = [own_jar_pattern(name) for name in own_jars]
    for name, pattern in zip(own_jars, patterns):
        matches = [e for e in entries if pattern.match(e)]
        if len(matches) != 1:
            raise SystemExit(f'own jar {name} matches {len(matches)} files in the image: {matches}')
    app, base = [], []
    for entry in entries:
        is_app = APP_FIXED.match(entry) or PER_BUILD_JAR.match(entry) or any(p.match(entry) for p in patterns)
        (app if is_app else base).append(entry)
    return app, base


def normalized_mode(path):
    st = path.lstat()
    if stat.S_ISLNK(st.st_mode):
        return 0o777
    return 0o755 if st.st_mode & 0o111 else 0o644


def base_id(image, base):
    digest = hashlib.sha256()
    for entry in base:
        path = image / entry
        if path.is_symlink():
            content = 'l:' + os.readlink(path)
        else:
            content = hashlib.sha256(path.read_bytes()).hexdigest()
        digest.update(f'{normalized_mode(path):o} {content} {entry}\n'.encode())
    return digest.hexdigest()[:16]


def tar_info(name, mode, kind=tarfile.REGTYPE, size=0, linkname=''):
    info = tarfile.TarInfo(name)
    info.type, info.mode, info.size, info.linkname = kind, mode, size, linkname
    info.mtime, info.uid, info.gid, info.uname, info.gname = 0, 0, 0, '', ''
    return info


def write_archive(output, members):
    """Writes a reproducible zstd tar. members: sorted (archive path, source Path or bytes)."""
    directories = sorted({str(Path(name).parents[i]) for name, _ in members
                          for i in range(len(Path(name).parents) - 1)})
    with open(output, 'wb') as sink:
        zstd = subprocess.Popen(['zstd', '-19', '-T0', '-q', '-c'], stdin=subprocess.PIPE, stdout=sink)
        with tarfile.open(fileobj=zstd.stdin, mode='w|', format=tarfile.GNU_FORMAT) as tar:
            for directory in directories:
                tar.addfile(tar_info(directory, 0o755, tarfile.DIRTYPE))
            for name, source in members:
                if isinstance(source, bytes):
                    tar.addfile(tar_info(name, 0o644, size=len(source)), io.BytesIO(source))
                elif source.is_symlink():
                    tar.addfile(tar_info(name, 0o777, tarfile.SYMTYPE, linkname=os.readlink(source)))
                else:
                    with source.open('rb') as data:
                        tar.addfile(tar_info(name, normalized_mode(source), size=source.stat().st_size), data)
        zstd.stdin.close()
        if zstd.wait() != 0:
            raise SystemExit(f'zstd failed for {output}')


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open('rb') as data:
        for block in iter(lambda: data.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--image', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--own-jar', action='append', default=[], dest='own_jars')
    parser.add_argument('--extra', action='append', default=[], help='<source file>=<path in the app archive>')
    args = parser.parse_args(argv)

    image = args.image.resolve()
    for required in ('bin/andbible', 'lib/app/andbible.cfg', 'lib/runtime/release',
                     'lib/app/resources/kcef-natives/jcef_helper'):
        if not (image / required).is_file():
            raise SystemExit(f'{image} is not a Linux app image: {required} is missing')
    if not args.own_jars:
        raise SystemExit('no --own-jar given')

    app, base = classify(image_entries(image), args.own_jars)
    identity = base_id(image, base)

    extras = []
    for spec in args.extra:
        source, _, target = spec.partition('=')
        if not target or target in app or target in base or target == BASE_ID_PATH:
            raise SystemExit(f'bad --extra {spec}')
        extras.append((target, Path(source)))
    app_members = sorted([(e, image / e) for e in app] + extras + [(BASE_ID_PATH, f'{identity}\n'.encode())])

    args.out.mkdir(parents=True, exist_ok=True)
    app_file = args.out / f'andbible-desktop-{args.version}-linux-x64-app.tar.zst'
    base_file = args.out / f'andbible-desktop-linux-x64-base-{identity}.tar.zst'
    write_archive(app_file, app_members)
    write_archive(base_file, [(e, image / e) for e in base])

    manifest = {
        'version': args.version,
        'base_id': identity,
        'base': {'file': base_file.name, 'sha256': sha256_file(base_file)},
        'app': {'sha256': sha256_file(app_file)},
    }
    (args.out / f'andbible-desktop-{args.version}-linux-x64-split.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'app {app_file.stat().st_size / 1e6:.1f} MB ({len(app)} files), '
          f'base {identity} {base_file.stat().st_size / 1e6:.1f} MB ({len(base)} files)')


if __name__ == '__main__':
    main(sys.argv[1:])
