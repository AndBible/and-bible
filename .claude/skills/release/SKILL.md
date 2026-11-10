---
name: release
description: >
  Use when the maintainer wants an internal release of the current HEAD: Google Play internal track and/or a TestFlight
  (internal) build of the iOS app, or a dry rehearsal of it. One command, one YubiKey touch, both stores in parallel.
  Triggers: "tee release", "julkaise internal", "play internal", "internal release", "release both stores",
  "julkaise molempiin kauppoihin", "tee testflight-build", "testflight", "uusi testflight", "tee uusi testflight",
  "upload to TestFlight", "make testflight", "make release-internal".
---

# Internal release (`make release-internal`)

`make release-internal` takes the clean HEAD and ships the same version (`version.properties`) to **Google Play
`internal`** (signed `standardGoogleplayRelease` AAB, release status completed, no store texts) and to **TestFlight
internal** (archive on the remote Mac, IPA checks, upload). `scripts/release/release-internal.sh`:

1. takes the release lock (one run per container), runs the preflight (no secrets; every miss is listed with its fix),
   wipes a stale RAM dir of a killed run, decides artifact reuse;
2. decrypts the secrets bundle **once** into a RAM dir: the maintainer touches the YubiKey at the line
   `secrets: decrypting andbible-release-secrets.tar.gpg: touch the YubiKey ...`;
3. runs the **Android branch** (`android.sh`: Gradle `--no-daemon` with `ANDBIBLE_SIGNING_PROPERTIES`, certificate and
   version checks, `fastlane supply --track internal`) and the **iOS branch** (`ios-build.sh` -> `mac/archive.sh`, the
   proven TestFlight route) in parallel; each uploads as soon as its artifact passed its checks;
4. wipes the RAM dir when both have ended (the Mac key volume is detached by the iOS branch), writes the summary.

About 20 minutes. Variants: `PLATFORM=ios` (TestFlight only; `make testflight` is exactly this), `PLATFORM=android`,
`DEST=export` (both build and check, nothing is uploaded: the rehearsal), `DRY_RUN=1` (preflight and plan only).
Secrets, the bundle and what lives where: `docs/release-secrets.md`. Mac basics: skill `ios-mac`.

Chat with the maintainer in Finnish (AskUserQuestion forms too); this file and the status entry are English.

## 1. Prerequisites

The preflight checks all of these and prints each miss with its fix; the host lines name the container's short name.
When one is missing, ask with AskUserQuestion to run the host command, and wait. Never look for keys elsewhere, and
never print or cat anything from the bundle or the RAM dir.

- Secrets: `~/.release-secrets/andbible-release-secrets.tar.gpg`. Host: `jailbee mount release-secrets <container> --for 2h`.
  No bundle yet: the maintainer packs it once with `scripts/release/secrets-pack.sh` (four touches, announced one by one)
  and runs the host commands it prints (`docs/release-secrets.md`).
- Android: `fastlane` on `PATH`, and egress to `androidpublisher.googleapis.com`, `oauth2.googleapis.com`,
  `www.googleapis.com`. Host, per host: `jailbee net egress add <host> <container>`. Never add these to the shared
  `.jailbee/config.yaml` (`www.googleapis.com` is an exfiltration path).
- iOS: `~/.ssh-mac/config` (host: `jailbee mount mac-ssh <container>`), `scripts/mac-remote.sh check` answers and no
  other container holds the Mac lock (the run never waits for it; `scripts/mac-remote.sh queue` shows who waits).
- Clean tree: everything committed (tracked and untracked, incl. `jsword`/`kdom` gitlinks; `docs/superpowers` and
  `app/src/test/roborazzi` are ignored). No override; commit first.
- `version.properties` already holds the version to ship (bumping is the maintainer's, not this skill's). Play refuses
  a versionCode it already has.

## 2. Approval

The maintainer asking for an internal release (or a TestFlight build) is the approval for **one** run of the current
HEAD to the stores asked for. If the skill was reached from another task, ask with AskUserQuestion first. Never upload
again without a new ask; a retry of a failed branch (section 5) is part of the same approval only when the maintainer
asked for it.

## 3. Run

Start the command as a background command, e.g. `make release-internal` (or `make testflight`, `make release-internal
PLATFORM=android`, `... DEST=export`). Tell the maintainer to touch the YubiKey when the decrypt line appears (a PIN may
be asked); the decrypt waits about 15 s for the touch. The terminal shows one line per step, prefixed `[android]` /
`[ios]`, and `[android] PASS` / `[ios] FAIL (rc n); see <log>` at the end of each branch; the full logs are
`.local/release/<versionCode>/{android,ios}/branch.log` (Gradle: `android/gradle.log`, Play: `android/upload.log`).
Do not kill the run casually: the trap stops both branches, detaches the Mac key volume and wipes the RAM dir, but a
half-done upload is then unknown.

## 4. Result and evidence

The summary (terminal and `.local/release/<versionCode>/summary.txt`) has one line per branch: `PASS`, `FAIL` or
`SKIPPED`, version, iOS build number, duration, artifact paths, a `retry:` line per failed branch and the host
reminders. Exit 0 only when every requested branch passed.

- Android: `.local/release/<versionCode>/android/andbible-<versionName>.aab` and `artifact.env` (commit, versionCode,
  versionName, SHA-256); the AAB is checked against the bundle keystore's certificate and `version.properties` before
  upload.
- iOS: the TestFlight evidence as before in `.local/release/testflight/<build>/` (`summary.txt` last line `result=PASS
  upload` | `result=PASS export` | `result=FAIL <step>`; trust it and the rc). `ios/artifact.env` records the build
  number of this commit. The iOS log prints `mac asckey volumes after detach: ...`; if a WARNING says the detach
  failed, run the command it prints (`scripts/mac-remote.sh ssh 'hdiutil detach -force /Volumes/<vol>'`).

## 5. Failures and retry

A failed branch does not stop the other. Retry only the failed one, with the summary's line:
`make release-internal PLATFORM=android|ios` (one new touch). The retry **reuses** the artifact when it was built from
the same commit and version: the AAB (re-verified, no Gradle), or the Mac archive of the recorded build number (fresh
export and checks; when the archive is gone, e.g. after `mac-remote.sh cleanup`, it archives again with a new build
number). A new commit or version bump always rebuilds. A branch that already uploaded is not reused (iOS gets a new
build number; Play would refuse the versionCode).

| Symptom | Fix |
|---|---|
| Preflight lists misses | Do what each line says (host commands go to the maintainer via AskUserQuestion), rerun. Nothing was decrypted. |
| `another release run is active in this container` | A run (or `secrets-pack.sh`) is still going; wait for it. |
| Decrypt failed (no touch in time, "Bad PIN") | Nothing was built or sent; rerun and touch in time. |
| `Play already has versionCode <n>` | The version was already uploaded; the maintainer bumps the version (new commit), then rerun. |
| Play OAuth / 401 / 403 in `android/upload.log` | The service account lost access or the JSON is stale: ask the maintainer (Play Console users and permissions); repack the bundle if the JSON changed. |
| `curl` code `000` for a Play host during upload | Egress was removed mid-run; host: `jailbee net egress add <host> <container>`, retry `PLATFORM=android`. |
| Android `the AAB certificate ... is not the release keystore's` | The build was signed with something else: never upload; check `ANDBIBLE_SIGNING_PROPERTIES` handling in `app/build.gradle.kts`. |
| Gradle failed | Read `android/gradle.log`; fix, commit, rerun `PLATFORM=android`. |
| `result=FAIL upload` (iOS) | Read `upload.log`; ask the maintainer whether the build arrived in App Store Connect. A retry reuses the archive. |
| Mac lock held by another container | The iOS branch fails fast; wait, then `PLATFORM=ios`. Do not `unlock` another container's lock. |
| Version/commit mismatch with `Version.xcconfig` | Stale sync; rerun (the sync regenerates it). |
| K/N link `OutOfMemoryError` (`DevirtualizationAnalysis`, Java heap space) | The Release link runs out of process (`iosApp/project.yml`, Release only: `-Pkotlin.native.disableCompilerDaemon=true -Pkotlin.native.jvmArgs=-Xmx8g`); raise `jvmArgs` there, not the daemon heap. Check the Mac's free memory with `scripts/mac-remote.sh check`; shut booted simulators down first (`scripts/mac-remote.sh cleanup`). |
| Missing iCloud entitlements | `archive.sh` ad-hoc re-signs the unsigned archive with the Release-expanded `iosApp.entitlements`; check `ipa-entitlements.txt`. |
| `-exportArchive` "Copy failed" | openrsync spawns Homebrew rsync from PATH; the export must run with `PATH=/usr/bin:/bin:/usr/sbin:/sbin` (`EXPORT_PATH` in `archive.sh`). |
| Xcode blocked by an orphan "Apple Development: Created via API" cert | Do not add a temporary keychain or provisioning flag to the archive path; revoke the cert via the App Store Connect API (ask the maintainer). |
| Nonzero rc after the iOS upload step | Read `upload.log` and `summary.txt` first; the upload may have succeeded (`result=PASS upload`). |
| ITMS-9xxxx rejection | Read `upload.log`; fix the app/plist, new commit, rerun. |

## 6. Status entry

After a run that uploaded, add one bullet at the top of the "Next up" list in
`docs/superpowers/status/ios-readiness-status.md` naming both stores, in the shape of the fifteenth build's entry:
"N-th internal release <date>: Play internal <versionName> (versionCode) + TestFlight build <build> (<version>)",
and-bible commit; "Since build N-1:" contents from `git log <previous build's commit>..HEAD`; the latest known gate at that
HEAD ("no gate at this HEAD" is valid); timings from the summary (per branch and total) and the iOS archive timing from
the evidence `summary.txt`; the entitlements check (`checks=`). A branch that failed or was skipped is named as such.
Commit in the `docs/superpowers` submodule, then the gitlink bump in and-bible. Never push.

## 7. Cleanup and host reminders

- `scripts/mac-remote.sh cleanup` after an iOS run. The run capture in `.local/mac/<container>/runs/<id>/` (about 490 MB
  with the xcarchive) is scratch: delete it after the status entry. Keep `.local/release/testflight/<build>/` only as long
  as the status entry needs it.
- `.local/release/<versionCode>/`: keep the last two versions' AABs (retries need the latest); delete older ones.
- Remind the maintainer of the host lines the summary prints: `jailbee unmount release-secrets <container>` and, after an
  Android run, `jailbee net egress rm <host> <container>` for the three Play hosts.
