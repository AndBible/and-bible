#!/usr/bin/env bash
# Host-side (Mac) Files-provider preparation before an XCUITest run; `scripts/mac-remote.sh xcode-test` calls it on the
# booted simulator. Idempotent, so the suite no longer depends on a hand-seeded simulator (issue #3949):
#  1. removes what earlier UI runs left in "On My iPhone/iPad" (uniquely named export folders and files; dozens of them
#     push picker targets off-screen, see memory ios-simulator-files-clutter-breaks-picker-tests),
#  2. makes sure the fixtures the suite reads exist with the expected bytes:
#     H2 Fixtures/{h2-kjv.zip, db-backup.abdb.zip, h2-bookmarks.csv} and an empty-able H2 Backup folder.
# Usage: bash iosApp/scripts/prepare-ui-test-files.sh <booted-simulator-udid>   (run from the repository root)
# h2-kjv.zip comes from tmp/h2-files/h2-kjv.zip (mac-remote packages it from ~/.sword before the sync); when it is
# missing the KJV fixture is reported and skipped, the KJV-provisioning tests then fail with their HARNESS message.
set -euo pipefail
exec python3 -I - "$@" <<'PY'
import filecmp
import json
import pathlib
import plistlib
import shutil
import subprocess
import sys
import tempfile
import time
import uuid


def log(message):
    print('prepare-ui-test-files: ' + message, flush=True)


if len(sys.argv) != 2:
    raise SystemExit('usage: prepare-ui-test-files.sh <booted-simulator-udid>')
udid = sys.argv[1]
if str(uuid.UUID(udid)).upper() != udid.upper():
    raise SystemExit('invalid UDID')
repo = pathlib.Path.cwd()

# Device sets: the normal one, or the "testing" set that holds parallel-testing clones.
def device_dir():
    for extra in ([], ['--set', 'testing']):
        listing = json.loads(subprocess.check_output(['xcrun', 'simctl', *extra, 'list', 'devices', '-j'], text=True))
        for group in listing['devices'].values():
            for d in group:
                if d['udid'].upper() == udid.upper():
                    return pathlib.Path(d['dataPath']).parent, extra
    raise SystemExit('device not found: ' + udid)


base, simset = device_dir()


def storage_root():
    groups = base / 'data/Containers/Shared/AppGroup'
    if not groups.is_dir():
        return None
    for group in groups.iterdir():
        metadata = group / '.com.apple.mobile_container_manager.metadata.plist'
        if group.is_symlink() or not metadata.is_file():
            continue
        with metadata.open('rb') as handle:
            if plistlib.load(handle).get('MCMMetadataIdentifier') == 'group.com.apple.FileProvider.LocalStorage':
                root = group / 'File Provider Storage'
                root.mkdir(exist_ok=True)
                return root
    return None


root = storage_root()
if root is None:
    # A freshly created simulator has no local storage until the Files app ran once.
    log('local Files storage missing: starting the Files app once to create it')
    subprocess.run(['xcrun', 'simctl', *simset, 'launch', udid, 'com.apple.DocumentsApp'], check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(60):
        time.sleep(1)
        root = storage_root()
        if root is not None:
            break
    subprocess.run(['xcrun', 'simctl', *simset, 'terminate', udid, 'com.apple.DocumentsApp'], check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if root is None:
        raise SystemExit('prepare-ui-test-files: local Files storage did not appear')

# 1. Leftovers of earlier runs: every name here is made unique per run by a test (UUID/nonce suffix).
LEFTOVER_PREFIXES = ('AndBible OS ', 'AndBible Save ', 'AndBible UI Probe ', 'F MyDocs ', 'F Prompts ', 'F-page-',
                     'F-prompt-', 'F-prompts-', 'F-OS-', 'create-writeback-', 'delivery-save-', 'untitled folder')
removed = 0


def remove(path):
    global removed
    if path.is_dir() and not path.is_symlink():
        shutil.rmtree(path)
    else:
        path.unlink()
    removed += 1


for entry in list(root.iterdir()):
    if entry.name.startswith(LEFTOVER_PREFIXES):
        remove(entry)
fixtures = root / 'H2 Fixtures'
backup = root / 'H2 Backup'
for folder in (fixtures, backup):
    if folder.is_symlink() or (folder.exists() and not folder.is_dir()):
        raise SystemExit('prepare-ui-test-files: unsafe owned folder ' + str(folder))
    folder.mkdir(exist_ok=True)
for entry in list(fixtures.iterdir()):
    if entry.name.startswith('h2-bookmarks-roundtrip-'):
        remove(entry)
for entry in list(backup.iterdir()):
    if entry.name.startswith(('Database-', 'Documents-')):
        remove(entry)
log(f'removed {removed} leftovers of earlier UI runs')

# 2. Fixtures, replaced when their bytes differ.
generated = pathlib.Path(tempfile.mkdtemp(prefix='ui-fixtures-'))
subprocess.run([sys.executable, '-I', str(repo / 'iosApp/scripts/generate-files-test-fixtures.py'),
                '--output-dir', str(generated)], check=True, stdout=subprocess.DEVNULL)
wanted = {
    'h2-kjv.zip': repo / 'tmp/h2-files/h2-kjv.zip',
    'db-backup.abdb.zip': repo / 'app/src/test/resources/l1b-g2/old-release/db-backup.abdb.zip',
    'h2-bookmarks.csv': generated / 'h2-bookmarks.csv',
}
for name, source in wanted.items():
    target = fixtures / name
    if not source.is_file():
        log(f'MISSING source for H2 Fixtures/{name}: {source.relative_to(repo) if source.is_relative_to(repo) else source}')
        continue
    if target.is_file() and not target.is_symlink() and filecmp.cmp(source, target, shallow=False):
        continue
    if target.exists() or target.is_symlink():
        target.unlink()
    shutil.copyfile(source, target)
    log(f'seeded H2 Fixtures/{name}')
shutil.rmtree(generated)
log('Files storage ready: ' + str(root))
PY
