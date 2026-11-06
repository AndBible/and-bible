---
name: testflight
description: >
  Use when the maintainer wants a TestFlight (internal) build of the AndBible iOS app uploaded from the
  current HEAD, or a dry rehearsal of it: archive on the remote Mac, IPA checks, upload to App Store Connect.
  Triggers: "tee testflight-build", "testflight", "uusi testflight", "tee uusi testflight", "upload to TestFlight",
  "make testflight".
---

# TestFlight internal build (`make testflight`)

One command builds the clean HEAD on the remote Mac and uploads it: `make testflight` runs
`scripts/release/ios-build.sh`, which decrypts the App Store Connect API key (one YubiKey touch on the host),
streams it onto a RAM disk on the Mac, runs `scripts/release/mac/archive.sh` under the `mac-remote.sh` lock,
detaches the RAM disk right after the run (and again, if needed, whatever happens) and copies the evidence from the run capture. About 20 minutes (archive 900-970 s warm,
upload 60-95 s). `make testflight DEST=export` is the dry rehearsal (archive, export and IPA checks, no upload);
any other `DEST` is refused. Mac basics: skill `ios-mac`.

Chat with the maintainer in Finnish (AskUserQuestion forms too); this file and the status entry are English.

## 1. Prerequisites

Check each one; when one is missing ask with AskUserQuestion to run the host command, and wait. Never look for
keys elsewhere, and never print or cat `asc-api.env`, the key or gpg output.

- Secrets: `~/.release-secrets/` has `asc-api.env` and `AuthKey_<id>.p8.gpg`. Host: `jailbee mount release-secrets <container> --for 2h`
  (env override: `RELEASE_SECRETS_SRC`).
- Mac ssh: `~/.ssh-mac/config` exists. Host: `jailbee mount mac-ssh <container>` (overrides: `MAC_SSH_CONFIG`, `MAC_HOST`).
- `scripts/mac-remote.sh check` works and no other container holds the lock. `ios-build.sh` does not wait: it stops
  at once and names the holder, so wait or ask the orchestrator, then retry. The lock may be free while others are queued: `scripts/mac-remote.sh queue` shows who waits.
- Clean tree: everything committed (tracked and untracked, incl. `jsword`/`kdom` gitlinks; `docs/superpowers` and
  `app/src/test/roborazzi` are ignored). There is no override; commit first.

## 2. Approval

The maintainer asking for a TestFlight build is the approval for one upload of the current HEAD. If the request did
not ask for a build (the skill was reached from another task), ask with AskUserQuestion first. Never upload twice
without a new ask.

## 3. Run

Start `make testflight` (or `make testflight DEST=export`) as a background command; `MAC_TIMEOUT` (default 5400 s)
bounds the Mac run. The first output lines are `build <n>` (UTC `YYYY.MMDD.HHMMSS`), `version <v>`, `commit <c>`:
note them, the status entry needs them. Tell the maintainer to touch the YubiKey when the decrypt starts (PIN may
be asked). Watch with `scripts/mac-remote.sh check` and the background output; the end is `testflight rc=<rc> in <s>s
(destination <dest>)`. An early refusal (dirty tree, lock held, decrypt failure) instead ends with an `ios-build: ...` line and no `testflight rc=` line. Do not kill the run casually: the EXIT trap detaches the Mac RAM disk, a second Ctrl-C is hardened.

## 4. Result and evidence

Evidence is copied from the run capture (`.local/mac/<container>/runs/<id>/run/step1/release/testflight-<build>/`) to
`.local/release/testflight/<build>/` (the `*.log` files, `summary.txt`, `release.entitlements`, `ipa-entitlements.txt`; not
the IPA or export trees). If the capture is missing `mac-remote.sh fetch` is the fallback; if both fail with a good run the rc stays 0 and a WARNING says the evidence is missing. The last line of `summary.txt` is `result=PASS upload`, `result=PASS export` or
`result=FAIL <step>`. Trust that line and `rc`, not the absence of errors. After the run the Mac must show no key
volume: the output prints `mac asckey volumes after detach: ...`; if a WARNING says the detach failed, run the
command it prints (`scripts/mac-remote.sh ssh 'hdiutil detach -force /Volumes/<vol>'`).

## 5. Failures

| Symptom | Fix |
|---|---|
| "Bad PIN" at decrypt | Happened once; ask the maintainer to retry, run again. Rerunning after a decrypt failure is safe (nothing built or sent). A failure after the archive or upload step is never retried automatically. |
| `result=FAIL upload` (network/upload failure after a good export) | Read `upload.log`; ask the maintainer to check App Store Connect whether the build arrived. Never rerun without a new ask (a rerun uses a new build number). |
| Dirty tree refusal | Commit (or stash), rerun. |
| Mac lock held by another container | Wait for it; do not `unlock` another container's lock. |
| Version/commit mismatch with `Version.xcconfig` | Stale sync; rerun (the sync regenerates it). |
| K/N link `OutOfMemoryError` | Memory `ios-release-kn-link-oom` (`iosApp/project.yml` runs the Release link out of process). |
| Missing iCloud entitlements, "Copy failed" (Homebrew rsync), orphan "Created via API" cert | Memory `ios-release-archive-mac-traps`. |
| Nonzero rc after the upload step | Read `upload.log` and `summary.txt` before treating it as a failed upload; the upload may have succeeded (`result=PASS upload`) with the failure only later. |
| ITMS-9xxxx rejection | Read `upload.log` in the evidence directory; fix the app/plist, new commit, new build number. |
| Mac leftovers / simulators | Memory `mac-cleanup-after-use`; run step 7. |

## 6. Status entry

On `result=PASS upload`, add a bullet "N-th manual TestFlight build <date> (internal only)" at the top of the "Next up"
list in `docs/superpowers/status/ios-readiness-status.md`, in the shape of the previous one: version, build number,
and-bible commit; "Since build N-1:" contents from `git log <previous build's commit>..HEAD`; the latest known gate at
that HEAD (look it up by hand in the status and batch notes; "no gate at this HEAD" is valid); archive timing from `summary.txt` and the total from the container's `testflight rc=... in <s>s` line (summary.txt has no total); entitlements check result (`checks=`). Commit in the `docs/superpowers` submodule, then
commit the gitlink bump (`git add docs/superpowers`) in and-bible. Never push.

## 7. Cleanup

`scripts/mac-remote.sh cleanup` (on the shared tree it keeps shared outputs; the run receipts remove only this run's captured `build/release` entries). The run capture in `.local/mac/<container>/runs/<id>/run/step1/` (about 490 MB incl. the xcarchive) is scratch: delete it after the status entry. Remind the
maintainer to run on the host: `jailbee unmount release-secrets <container>`. Keep the `.local/release/testflight/<build>/`
evidence only as long as the status entry needs it (`.local/` is shared scratch).
