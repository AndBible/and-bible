# Release secrets

How the internal release (`make release-internal`, skill `.claude/skills/release/`) handles the signing and store keys.
Design: `docs/superpowers/specs/2026-11-10-release-internal-one-touch-design.md` (private submodule).

## The bundle

All release secrets live in one GPG file, `andbible-release-secrets.tar.gpg`, encrypted to the maintainer's card key
(the same key `keystore.properties.gpg` is encrypted to). The host keeps it in `~/.config/andbible-release/`; JailBee
mounts that directory read-only as `~/.release-secrets/` only when asked (`jailbee mount release-secrets <container>
--for 2h`).

Format 1 is a tar archive of regular files:

| Member | Content |
|---|---|
| `MANIFEST` | `format=1`, then one `member=<name>` line per member |
| `andbible.p12` | PKCS12 with only the `andbible` alias (the Play app signing key); key password = store password |
| `keystore.properties` | `storeFile=andbible.p12`, `storePassword`, `keyAlias=andbible`, `keyPassword` |
| `play-service-account.json` | Google Play service account key (fastlane `supply`) |
| `AuthKey.p8` | App Store Connect API key |
| `asc-api.env` | `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_TEAM_ID` (identifiers, not secrets; values only in that file) |

The release keystore on the host also holds other apps' keys; the bundle carries only `andbible`, so a release run never
has them in memory.

## Packing (once, and after a key changes)

The maintainer runs `scripts/release/secrets-pack.sh` in a container with the `release-secrets` mount and the GPG agent.
It decrypts `keystore.properties.gpg` (repo root), `release_keys.p12.gpg`, the one `*.json.gpg` and
`AuthKey_<ASC_KEY_ID>.p8.gpg` from the mount: **four touches**, each announced on its own line ("touch the YubiKey now
for ... (n/4)") with a pause between them and one retry after a missed touch. It exports only the `andbible` alias,
checks that its certificate is the Play app signing certificate, test-signs a throwaway jar, writes the bundle to
`.local/release/andbible-release-secrets.tar.gpg` (refusing to overwrite) and prints the host commands: install the
bundle into `~/.config/andbible-release/` (mode 0600) and delete the `.local/` copy. The old separate `.gpg` files are
then no longer read by the pipeline (`release_keys.p12.gpg` may stay for the other apps). Recipient: the key of
`keystore.properties.gpg`, read without decrypting; `--recipient <keyid>` overrides.

## One run

- **One touch.** After the preflight, `secrets_open_bundle` (`scripts/release/lib-secrets.sh`) runs exactly one
  `gpg --decrypt` of the bundle, piped straight into `tar -x` inside a private RAM filesystem (ramfs via `sudo -n mount`,
  tmpfs fallback; neither: the run stops before the decrypt). No intermediate file. The dir is 0700, members 0600, and the
  manifest and every member are checked.
- **Who reads what.** Gradle gets `ANDBIBLE_SIGNING_PROPERTIES=<RAM dir>/keystore.properties` (a path, not a secret) and
  runs with `--no-daemon`, so no daemon keeps the passwords; with the variable set it never runs `gpg`. `keytool` and
  `jarsigner` get passwords only through `:file` options. `fastlane supply` reads the JSON by path (`--json_key`). The Mac
  receives only `AuthKey.p8`, streamed over ssh stdin onto a per-run RAM disk that is detached right after the archive
  run (and again by the exit trap). The keystore and the Play JSON never leave the container's RAM.
- **Never** on argv, in an environment variable's value, in a log, in `/tmp` outside the RAM dir, or on disk.
- **How long.** The RAM dir lives for the run (about 20 minutes): it is shredded and unmounted when both branches have
  ended, after success, a failure, `Ctrl-C` or `kill`. `kill -9` of the top process is the one case no trap covers: the
  next `release-internal` run (which holds the release lock) wipes any stale `release-secrets.*` dir before it decrypts,
  and `scripts/mac-remote.sh cleanup` detaches stale `asckey-*` volumes on the Mac.
- **No session.** A retry is a new run with a new touch; it reuses built artifacts, not decrypted secrets.

## Explicit Gradle signing

Gradle reads signing credentials only when explicitly requested:

- `ANDBIBLE_SIGNING_PROPERTIES=<path>` loads that properties file and never runs GPG, even if
  `-Pandbible.sign=true` is also passed. The internal release pipeline already uses this route.
- Otherwise, `-Pandbible.sign=true` decrypts the repo-root `keystore.properties.gpg` in memory.
  A missing encrypted file is a build error. The manual `make install-prod`, `make bundle`,
  `make accrescent` and `make accrescent-debug` targets pass the flag for you.

Without either opt-in, Gradle never decrypts Android signing credentials and Android release
artifacts are unsigned. Task names containing `Release` (Android, desktop or iOS) do not enable
Android signing; desktop and iOS signing are configured separately.
`ANDBIBLE_RELEASE_KEYSTORE` only overrides the keystore path after credentials are loaded;
it does not enable signing on its own.

## Host commands

| When | Command |
|---|---|
| Before a run | `jailbee mount release-secrets <container> --for 2h` |
| Android branch | `jailbee net egress add <host> <container>` for `androidpublisher.googleapis.com`, `oauth2.googleapis.com`, `www.googleapis.com` (never in the shared `.jailbee/config.yaml`) |
| iOS branch | `jailbee mount mac-ssh <container>` |
| After the run | `jailbee unmount release-secrets <container>`; `jailbee net egress rm <host> <container>` for the three hosts |
