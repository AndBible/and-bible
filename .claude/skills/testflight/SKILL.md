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
fetches the evidence and detaches the RAM disk whatever happens. About 20 minutes (archive 900-970 s warm,
upload 60-95 s). `make testflight DEST=export` is the dry rehearsal (archive, export and IPA checks, no upload);
any other `DEST` is refused. Mac basics: skill `ios-mac`.

Chat with the maintainer in Finnish (AskUserQuestion forms too); this file and the status entry are English.

## 1. Prerequisites

Check each one; when one is missing ask with AskUserQuestion to run the host command, and wait. Never look for
keys elsewhere.

- Secrets: `~/.release-secrets/` has `asc-api.env` and `AuthKey_<id>.p8.gpg`. Host: `jailbee mount release-secrets <container> --for 2h`
  (env override: `RELEASE_SECRETS_SRC`).
- Mac ssh: `~/.ssh-mac/config` exists. Host: `jailbee mount mac-ssh <container>` (overrides: `MAC_SSH_CONFIG`, `MAC_HOST`).
- `scripts/mac-remote.sh check` works and no other container holds the lock. `ios-build.sh` does not wait: it stops
  at once and names the holder, so wait or ask the orchestrator, then retry.
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
(destination <dest>)`. Do not kill the run casually: the EXIT trap detaches the Mac RAM disk, a second Ctrl-C is hardened.

## 4. Result and evidence

Evidence is fetched to `.local/release/testflight/<build>/` (`archive.log`, `export.log`, `upload.log`,
`bibleview.log`, `summary.txt`). The last line of `summary.txt` is `result=PASS upload`, `result=PASS export` or
`result=FAIL <step>`. Trust that line and `rc`, not the absence of errors. After the run the Mac must show no key
volume: the output prints `mac asckey volumes after detach: ...`; if a WARNING says the detach failed, run the
`hdiutil detach` command it prints.

## 5. Failures

| Symptom | Fix |
|---|---|
| "Bad PIN" at decrypt | Happened once; ask the maintainer to retry, run again. |
| Dirty tree refusal | Commit (or stash), rerun. |
| Mac lock held by another container | Wait for it; do not `unlock` another container's lock. |
| Version/commit mismatch with `Version.xcconfig` | Stale sync; rerun (the sync regenerates it). |
| K/N link `OutOfMemoryError` | Memory `ios-release-kn-link-oom` (`iosApp/project.yml` runs the Release link out of process). |
| Missing iCloud entitlements, "Copy failed" (Homebrew rsync), orphan "Created via API" cert | Memory `ios-release-archive-mac-traps`. |
| ITMS-9xxxx rejection | Read `upload.log` in the evidence directory; fix the app/plist, new commit, new build number. |
| Mac leftovers / simulators | Memory `mac-cleanup-after-use`; run step 7. |

## 6. Status entry

On `result=PASS upload`, add a bullet "N-th manual TestFlight build <date> (internal only)" at the top of the "Next up"
list in `docs/superpowers/status/ios-readiness-status.md`, in the shape of the previous one: version, build number,
and-bible commit; "Since build N-1:" contents from `git log <previous build's commit>..HEAD`; the latest known gate at
that HEAD (look it up by hand in the status and batch notes; "no gate at this HEAD" is valid); archive and total
timings from `summary.txt`; entitlements check result (`checks=`). Commit in the `docs/superpowers` submodule, then
commit the gitlink bump (`git add docs/superpowers`) in and-bible. Never push.

## 7. Cleanup

`scripts/mac-remote.sh cleanup` (also removes the archive and IPA under `build/release/` on the Mac). Remind the
maintainer to run on the host: `jailbee unmount release-secrets <container>`. Keep the `.local/release/testflight/<build>/`
evidence only as long as the status entry needs it (`.local/` is shared scratch).
