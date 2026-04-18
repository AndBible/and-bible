# Accrescent Build Guide

This guide explains how to build APK sets for publishing to [Accrescent](https://accrescent.app).

## Prerequisites

- GPG installed (`sudo apt install gnupg` on Debian/Ubuntu)
- Android keystore file (same one used for all non-F-Droid release variants)
- Bundletool Gradle plugin (already configured)

## Initial Setup

Signing credentials for all release variants (including Accrescent) live in a single
GPG-encrypted file at the repo root: `keystore.properties.gpg`.

### 1. Create the credentials file

```bash
# Copy the example and fill in your values
cp keystore.properties.example keystore.properties
nano keystore.properties
```

The file should contain:

```properties
storeFile=/home/youruser/.android/release_keys.jks
storePassword=your_keystore_password
keyAlias=andbible
keyPassword=your_key_password
```

### 2. Encrypt the file

```bash
gpg -e -r <your-gpg-id> keystore.properties
```

This produces `keystore.properties.gpg`.

### 3. Delete the plaintext

```bash
rm keystore.properties
```

The plaintext file is gitignored; only the `.gpg` file is committed.

### 4. Verify

```bash
gpg --decrypt keystore.properties.gpg
```

## Building APK Sets

### Quick Build

```bash
make accrescent         # Release APK set
make accrescent-debug   # Debug APK set (for testing)
```

The Gradle build decrypts `keystore.properties.gpg` in-memory (GPG passphrase / YubiKey
prompt will appear). The plaintext credentials never touch disk.

### Direct Gradle commands

```bash
./gradlew buildApksStandardAccrescentRelease
./gradlew buildApksStandardAccrescentDebug
```

### Output location

```
app/build/outputs/apkset/standardAccrescentRelease/app-standardAccrescentRelease.apks
```

`make accrescent` also copies it to `app/standardAccrescent/release/` for convenience.

## Testing the APK Set

```bash
bundletool install-apks --apks=app/standardAccrescent/release/app-standardAccrescentRelease.apks
```

## Publishing to Accrescent

1. Go to [Accrescent Developer Console](https://accrescent.app/developers)
2. Create or select your app
3. Upload the `.apks` file
4. Fill in required metadata
5. Submit for review

## Security Notes

- ✓ **`keystore.properties.gpg`** — safe to commit (encrypted)
- ✗ **`keystore.properties`** — gitignored, delete immediately after encrypting
- ✗ **`local.properties`** — gitignored; no signing info lives here anymore

## Troubleshooting

### "gpg --decrypt of keystore.properties.gpg failed"

- Is the file present at repo root?
- Did you enter the correct GPG passphrase / touch the YubiKey in time?
- Was the file encrypted to a GPG key you have access to?

### "keystore.properties.gpg not found — release artifacts will be unsigned"

This message is **expected on CI** (GitHub Actions signs APKs externally with `apksigner`).
On a developer machine, it means the encrypted credentials file is missing — see
[Initial Setup](#initial-setup).

## CI/CD Integration

The GitHub Actions `build-apk.yml` workflow signs APKs externally via `apksigner` using
GitHub Secrets. The Gradle build leaves APKs unsigned on CI because
`keystore.properties.gpg` is not present there. No changes needed for CI.

## Requirements

And-Bible meets all Accrescent requirements:

- ✓ Target SDK: 35
- ✓ No debuggable flag
- ✓ No testOnly flag
- ✓ Cleartext traffic disabled (HTTPS only)
- ✓ No self-update mechanism
- ✓ No sensitive permissions (except READ_EXTERNAL_STORAGE ≤ API 28)
- ✓ Production signing key (not debug)

## Further Information

- [Accrescent Documentation](https://accrescent.app/docs)
- [Bundletool Documentation](https://developer.android.com/studio/command-line/bundletool)
- [App Signing Best Practices](https://developer.android.com/studio/publish/app-signing)
