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
for a maximum of 1 MiB on disk. They are alternating recoverable snapshots, so distinct
history is about 512 KiB, not 1 MiB. Records older than seven days are pruned at
initialization and writes. The asynchronous writer admits at most 256 pending records,
each at most 4 KiB; overflow is indicated by a coalesced dropped-events record.
Forced termination can lose the queued tail. The directory and files are marked excluded
from backup when possible.

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

## Deferred Mac verification

The Mac was unavailable during authoring. Xcode compilation, iOS Native execution,
XCUITest execution and the physical container lookup remain pending. Do not infer runtime
success from Linux checks. Do not upload a release or TestFlight build as part of this
verification.
