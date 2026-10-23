# iOS Files-provider test setup

The full, unfiltered iOS UI suite expects real local Files-provider inputs. On each simulator used for the suite, seed the legacy file-provider fixture and the real KJV bootstrap once before running the suite. Tests still exercise the production Files importer and chooser; the suite does not inject fake configuration or application database state. Per-test filesystem presence alone is not proof of installation.

## Prepare the local archives (container)

The project test-module setup already requires the real Sword modules extracted into `~/.sword` (normally from the repository's `.local/testmods.zip`). Package the original KJV module bytes without network access:

```sh
mkdir -p tmp/h2-files
python3 -I iosApp/scripts/package-backup-test-kjv.py \
  --sword-dir "$HOME/.sword" \
  --output tmp/h2-files/h2-kjv.zip
```

The command validates KJV metadata and the eight required regular, non-symlink files, then refuses to replace an existing output. This produces only a transport archive; it does not install the Bible or prove backup/restore acceptance.

Generate the optional second genuine Bible fixture with the existing helper (container):

```sh
python3 -I iosApp/scripts/generate-files-test-fixtures.py --output-dir tmp/h2-files/optional-fixtures
```

`tmp/h2-files/optional-fixtures/f-rawtext.zip` is an optional second Bible prerequisite for the Documents uninstall case: import it through the app's production Files UI before attempting the KJV deletion, because the chooser must retain at least one Bible. It is not preinstalled by test setup and is not a substitute for exercising the UI.

The root `tmp/` directory is gitignored and included in the controller Mac sync. `.local/` and build directories are excluded. After preparing `tmp/h2-files/h2-kjv.zip`, sync the project from the container:

```sh
scripts/mac-remote.sh sync
```

This wrapper syncs the working tree to `~/work/<container-hostname>/and-bible` on the controller Mac. The following `run` commands execute in that Mac checkout (replace the placeholder with the explicit UUID of a booted simulator):

```sh
scripts/mac-remote.sh run 'UDID=<BOOTED-SIMULATOR-UUID>; bash iosApp/scripts/seed-files-provider.sh --clean "$UDID"'
scripts/mac-remote.sh run 'UDID=<BOOTED-SIMULATOR-UUID>; bash iosApp/scripts/seed-files-provider.sh "$UDID" app/src/test/resources/l1b-g2/old-release/db-backup.abdb.zip "H2 Fixtures"'
scripts/mac-remote.sh run 'UDID=<BOOTED-SIMULATOR-UUID>; bash iosApp/scripts/seed-files-provider.sh "$UDID" tmp/h2-files/h2-kjv.zip "H2 Fixtures"'
```

The seed script is mode 0644, so invoke it with `bash`. The script accepts `H2 Backup` or `H2 Fixtures`; `--clean <udid>` removes only those owned folders and recreates `H2 Backup`, while seeding refuses to overwrite an existing destination. Clean only when intentionally repeating setup, before all fixture seeds. Seed both archives afterward without another clean; do not use broad cleanup commands or remove unrelated Files-provider data.

The first seed is the existing legacy fixture, `app/src/test/resources/l1b-g2/old-release/db-backup.abdb.zip`; keep it unchanged alongside the exact basename `h2-kjv.zip` required by the UI test. Both are needed before the full unfiltered suite: another test class may reset installed modules, and tests that need KJV must then be able to reinstall it using the real Files UI. Seeding is not installation or proof of acceptance. After seeding, run the unfiltered suite on the Mac so its actual setup and production UI paths are exercised.
