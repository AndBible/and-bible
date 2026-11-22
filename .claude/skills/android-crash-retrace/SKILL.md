---
name: android-crash-retrace
description: >
  Use whenever an Android crash, ANR, logcat, bug-report attachment or Play Console trace from an
  R8-obfuscated AndBible release build has to be read. Triggers: "deobfuscate", "retrace", "stack trace
  is obfuscated", "r8-map-id", "crash from a release build", "logcat from bug report", "deobfuskoi",
  "pura stack trace", "kaatumisloki", "crash-loki", "obfuskoitu".
---

# Retracing crashes from release builds

Background, mapping archive and keep rules: [docs/r8-obfuscation.md](../../../docs/r8-obfuscation.md).

## 1. Recognise an obfuscated trace

Frames look like `at a32.E(r8-map-id-<64 hex>:721)`. The map id is the `# pg_map_id:` of that exact build's
`mapping.txt`, so a trace and its mapping cannot be mismatched. A trace with real `net.bible.*` names and
file names is not obfuscated (F-Droid flavour, debug builds): no retrace needed.

## 2. Get the evidence

- **Device over adb** (the maintainer runs these on the host and drops the files into `.local/<topic>/`):
  - `adb logcat -d -b crash`
  - `adb logcat -d -v threadtime`
  - `adb shell dumpsys dropbox --print data_app_crash` (also `data_app_native_crash`, `data_app_anr`): keeps
    earlier crashes after logcat has rotated. This is what worked for the 6.0.1279 startup crash loop, where
    the in-app handler never ran.
  - `adb shell dumpsys package net.bible.android.activity | grep version` for the installed version.
    Release package id `net.bible.android.activity`; the discrete flavour is `com.app.calculator`.
- **osTicket bug reports**: `ostickethelper --config ~/.osticket/osticket-config.yaml read <id>` (read-only,
  needs no confirmation) downloads the attachments `logcat.txt.gz` / `last_crash_logcat.txt.gz` into
  `.local/osticket/inbox/<id>/`. The ticket subject carries version, commit and the first obfuscated frame.
  Follow the osTicket rules in `~/.claude/CLAUDE.md` (mount, never print the password, confirm writes).
- **Play Console**: crash and ANR reports there are already de-obfuscated with the AAB's mapping.

## 3. Retrace

```bash
scripts/retrace-crash.sh <file>                       # .txt or .gz; mapping picked by map id
scripts/retrace-crash.sh --version-code 1277 <file>   # first fetch the GitHub CI release's mappings (gh)
scripts/retrace-crash.sh --mapping mapping.txt <file> # explicit mapping (.txt/.gz); refused if map id differs
scripts/retrace-crash.sh - < trace.txt                # stdin
```

It searches `$ANDBIBLE_MAPPINGS_DIR` (default `.local/r8-mappings/`) and `.local/release/*/android/`. A log
that mixes several map ids is refused: split it per build. Save the output next to the input as
`<name>.retraced.txt`, e.g. `scripts/retrace-crash.sh x/logcat.txt.gz > x/logcat.retraced.txt`.
`--version-code` takes the number after the last dot (6.0.**1277**) and covers GitHub CI builds only.

## 4. No mapping found

Do not guess names. Identify the channel (table "Where each release's mapping is kept" in the doc):

- GitHub APKs: `--version-code N` fetches `mapping-andbible-<tag>.txt.gz` / `mapping-calculator-<tag>.txt.gz`.
- Google Play: mapping in `.local/release/<versionCode>/android/mapping-<versionName>.txt` or inside the AAB;
  otherwise Play Console, "ReTrace mapping file".
- Accrescent / Samsung / Huawei / Amazon (manual builds): only if `scripts/archive-r8-mappings.sh <variant>` ran
  right after the build (`.local/r8-mappings/<versionCode>/<variant>/`). If it did not, the mapping cannot be
  regenerated. At most rebuild the same commit for an unobfuscated flavour to read the code. Say that
  the trace cannot be retraced instead of inferring.
- F-Droid: not obfuscated, nothing to retrace.

## 5. Reading tips

- A crash inside a ContentProvider or `androidx.startup` initializer happens before `Application.onCreate`, so
  AndBible's crash handler and report dialog never run (issue #4176). No in-app report exists: use the dropbox.
- Room "Failed to create an instance of X": the generated `X_Impl` is missing or not instantiable after R8
  (issue #4175); look at the keep rules, not at X.
- Line numbers are kept; inlined frames are expanded by retrace, so read the whole retraced block.

## 6. After reading

Put the retraced excerpt (the exception and the relevant frames, not the whole log, no user content such as
paths, account names or Bible text) into the GitHub issue (via the `jailbee-issue-management` outbox) or the
child's brief, with version code, flavour and channel.
