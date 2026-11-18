# iOS runtime diagnostics

The app records an allowlisted technical event stream separately from its existing
in-memory app log. Events describe app and scene lifecycle, hosts and reading panes;
opaque IDs distinguish objects and runs. They do not contain reading content, notes,
accounts, paths, URLs, arbitrary JavaScript or raw error messages. A live event is not
proof that a page painted, and the stream alone cannot establish why a hang occurred.
The normal report assembles `ios_runtime_diagnostics.txt.gz` alongside the existing
`app_log.txt.gz`; sending remains a user decision.

The application-support directory `AndBible/Diagnostics` contains
`diagnostics.0.ndjson` and `diagnostics.1.ndjson`. Each generation is at most 512 KiB,
for a maximum of 1 MiB on disk. The active generation receives append-only batches with
cumulative checksum checkpoints; rotation writes a complete snapshot into the other
slot, keeping the last complete generation recoverable during an interrupted write.
The original single-footer format remains readable. Rotation compacts to about 256 KiB
to leave headroom, so active distinct history ranges from about 256 to 512 KiB rather
than 1 MiB. Startup rotates before its first append to discard any interrupted tail.
Records older than seven days are physically pruned at initialization and rotation,
not on every event; export always filters expired records. Invalid or unreadable inactive
files are discarded at startup, and rotation overwrites its inactive slot. Failed writes,
including close failures after a complete rotation, force another rotation before appending.
The report attachment includes
only the current and previous retained run (within those seven days), not every launch.
The asynchronous writer admits at most 256 pending records, each at most 4 KiB, and drains
queued pre-snapshot records in one store batch. Queue overflow, oversized records and
new records omitted by batch compaction are indicated by a coalesced dropped-events record;
eviction of previously retained history is not counted as new event loss. The snapshot
barrier preserves ordering without waiting for later
producers, retaining its one-second timeout for genuinely blocked I/O.
Forced termination can lose the queued tail. The directory is marked excluded from
backup at startup, and each generation file is marked once when newly created, not on
every append; rewriting an existing file preserves its resource flag.

## Collecting evidence on a Mac

The following physical iOS-on-Mac container lookup and collection procedure has **not
been validated on a physical Mac**. Do not use simulator container paths for it. Verify
the selected container and bundle ID before copying anything: the release app is
`org.andbible.ios`, the debug app is `org.andbible.ios.dev`. macOS may retain a sandbox
after an uninstall. Treat all collected files as private and share only with consent.

In Terminal, inspect candidate container metadata and choose the matching app container:

```sh
find "$HOME/Library/Containers" -name .com.apple.containermanagerd.metadata.plist -print
# For each candidate, inspect its metadata rather than assuming the directory name:
plutil -p '/path/to/container/.com.apple.containermanagerd.metadata.plist'
```

After identifying the correct container, copy both generations if present, preserving
the originals. Replace the placeholder with that container's actual path:

```sh
container='/path/to/verified/container'
mkdir -p "$HOME/Desktop/andbible-diagnostics"
cp -p "$container/Data/Library/Application Support/AndBible/Diagnostics/diagnostics.0.ndjson" "$HOME/Desktop/andbible-diagnostics/" 2>/dev/null || true
cp -p "$container/Data/Library/Application Support/AndBible/Diagnostics/diagnostics.1.ndjson" "$HOME/Desktop/andbible-diagnostics/" 2>/dev/null || true
```

To collect a process sample while the app is foreground, first identify the exact PID
with Activity Monitor or `ps`. In Terminal start the delayed command below, then switch
back to the app immediately and reproduce the hang before the delay expires. The
background sampling command does not itself activate Terminal at sampling time.
Replace the PID and delay to suit the reproduction:

```sh
pid=12345
(sleep 15; sample "$pid" 10 -file "$HOME/Desktop/andbible-sample.txt") &
```

Samples can contain sensitive process data. Review and transmit them only with consent.

## Privacy manifest assessment

The store calls Okio `FileSystem.metadata` to check the size of its own files; it does
not use their timestamps for retention, which uses timestamps in event records. The
existing File Timestamp declaration includes C617.1 for app-container files. The
metadata call may reach a filesystem stat API covered by that category; no additional
reason is apparent for this app-private use. This is a source-level assessment, not a
fresh Apple binary/API compliance validation. No manifest declaration was changed.

## Verification status

The previously deferred Native and real-app checks ran on 2026-11-17: iPhone/iPad
force-terminate, relaunch and report attachment checks passed, including persisted record
identities across runs. The physical iOS-on-Mac container lookup above remains unvalidated;
the simulator results do not reproduce or explain the original Spaces/sleep hang.

The write-amplification fix has Linux JVM store/writer regression coverage (I/O cost,
coalescing, busy snapshot deadline, interrupted checkpoints and startup pruning). Its
updated `IosRuntimeDiagnosticsTest` still needs a Mac test run; compiling iOS tests on
Linux does not execute them. Do not upload a release or TestFlight build as part of
verification.

The #4148 review follow-up was verified on 2026-11-18 with `:sharedUi:jvmTest`
(1894 tests, 2 skipped, zero failures or errors), `:sharedUi:compileKotlinIosArm64`,
`:sharedUi:compileKotlinIosSimulatorArm64` and `:sharedUi:compileTestKotlinIosSimulatorArm64`.
The two headless skips were the system clipboard round trip and `KcefLiveTest`.
Store/writer regression coverage accounts for 30 passing tests. New failure tests were
observed red before the close-failure, inactive-file cleanup, compaction-loss and malformed
JSON fixes. Mutating the startup rotation default to false made the restart/append/restart
test fail; restoring per-record draining made the virtual-time snapshot deadline test fail.
Legacy fixtures are generated by the writer from `74bb8ead19`, including alternating
near-512-KiB generations. No `:app` verify was selected: the diff is confined to host-side
store logic, JVM tests and developer documentation, with no app or rendering change.
These compile checks do not execute Native tests or establish real-device I/O timings.
