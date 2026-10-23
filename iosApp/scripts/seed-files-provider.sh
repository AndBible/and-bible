#!/usr/bin/env bash
# Host-side simulator Files setup; never invoked from simulator XCTest.
set -euo pipefail
exec python3 -I - "$@" <<'PY'
import json
import pathlib
import plistlib
import shutil
import subprocess
import sys
import uuid


def fail(message):
    raise SystemExit('seed-files-provider: ' + message)


args = sys.argv[1:]
clean = len(args) == 2 and args[0] == '--clean'
if not clean and len(args) != 3:
    fail('usage: --clean <udid> | <udid> <source-file> <H2 Backup|H2 Fixtures>')
udid = args[1] if clean else args[0]
try:
    if str(uuid.UUID(udid)).upper() != udid.upper():
        fail('invalid explicit UDID')
except ValueError:
    fail('invalid explicit UDID')
source = None
if not clean:
    source = pathlib.Path(args[1]).absolute()
    if source.is_symlink() or not source.is_file():
        fail('source must be an existing regular non-symlink file')
    if args[2] not in ('H2 Backup', 'H2 Fixtures'):
        fail('folder must be H2 Backup or H2 Fixtures')
    source = source.resolve(strict=True)

listing = json.loads(subprocess.check_output(
    ['xcrun', 'simctl', 'list', 'devices', 'available', '-j'], text=True))
devices = [d for group in listing['devices'].values() for d in group
           if d['udid'].upper() == udid.upper()]
if len(devices) != 1 or devices[0]['state'] != 'Booted':
    fail('explicit device must exist uniquely and be Booted')
udid = devices[0]['udid']
base = pathlib.Path.home() / 'Library/Developer/CoreSimulator/Devices' / udid
base = base / 'data/Containers/Shared/AppGroup'
if not base.is_dir() or base.is_symlink():
    fail('simulator shared groups unavailable')
roots = []
for group in base.iterdir():
    if group.is_symlink() or not group.is_dir():
        continue
    metadata = group / '.com.apple.mobile_container_manager.metadata.plist'
    if not metadata.is_file() or metadata.is_symlink():
        continue
    with metadata.open('rb') as handle:
        identifier = plistlib.load(handle).get('MCMMetadataIdentifier')
    if identifier == 'group.com.apple.FileProvider.LocalStorage':
        roots.append(group / 'File Provider Storage')
if len(roots) != 1:
    fail('local provider metadata missing or ambiguous')
root = roots[0]
if root.is_symlink() or not root.is_dir() or root.resolve().parent != root.parent.resolve():
    fail('local provider storage must be an existing non-symlink directory')
root = root.resolve(strict=True)
# Validate every target before any mutation. Never traverse an owned-folder symlink.
owned = [root / name for name in ('H2 Backup', 'H2 Fixtures')]
for folder in owned:
    if folder.is_symlink() or (folder.exists() and not folder.is_dir()):
        fail('unsafe owned folder: ' + str(folder))
if clean:
    for folder in owned:
        if folder.exists():
            shutil.rmtree(folder)
    (root / 'H2 Backup').mkdir()
    print('Cleaned only H2 Backup and H2 Fixtures in ' + str(root))
else:
    folder = root / args[2]
    destination = folder / source.name
    if destination.exists() or destination.is_symlink():
        fail('destination already exists; clean explicitly before seeding')
    folder.mkdir(exist_ok=True)
    # Exclusive creation prevents silently replacing an existing fixture.
    with source.open('rb') as src, destination.open('xb') as dst:
        shutil.copyfileobj(src, dst)
    print(destination)
PY
