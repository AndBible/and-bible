# Accrescent Build Guide

This guide explains how to build APK sets for publishing to [Accrescent](https://accrescent.app).

## Prerequisites

- GPG installed (`sudo apt install gnupg` on Debian/Ubuntu)
- Android keystore file
- Bundletool Gradle plugin (already configured)

## Initial Setup

### 1. Configure local.properties

Add non-sensitive signing information to `local.properties`:

```properties
accrescent.storeFile=/path/to/your/keystore.jks
accrescent.keyAlias=yourKeyAlias
```

⚠️ **DO NOT** put passwords in `local.properties`!

### 2. Create Encrypted Credentials File

Create a file with your signing passwords:

```bash
# Copy the example file
cp accrescent-signing.env.example accrescent-signing.env

# Edit it with your actual passwords
nano accrescent-signing.env
```

The file should contain:

```bash
export ACCRESCENT_STORE_PASSWORD="your_keystore_password"
export ACCRESCENT_KEY_PASSWORD="your_key_password"
```

### 3. Encrypt the Credentials

Encrypt the file with GPG:

```bash
gpg -c accrescent-signing.env
```

You'll be prompted to create a passphrase. **Remember this passphrase!**

### 4. Delete the Plaintext File

**IMPORTANT:** Delete the unencrypted file immediately:

```bash
rm accrescent-signing.env
```

### 5. Commit the Encrypted File (Optional)

The encrypted file `accrescent-signing.env.gpg` is safe to commit:

```bash
git add accrescent-signing.env.gpg
git commit -m "Add encrypted Accrescent signing credentials"
```

## Building APK Sets

### Quick Build (Recommended)

Use the Makefile shortcut:

```bash
# Build release APK set
make accrescent

# Build debug APK set (for testing)
make accrescent-debug
```

You'll be prompted for your GPG passphrase. The script will:
1. Decrypt your credentials
2. Build the APK set
3. Clear credentials from memory
4. Show the output location

### Manual Build

If you prefer, you can run the script directly:

```bash
# Release build
./scripts/build-accrescent.sh standardAccrescentRelease

# Debug build
./scripts/build-accrescent.sh standardAccrescentDebug

# Custom variant
./scripts/build-accrescent.sh discreteAccrescentRelease
```

### Output Location

The build script automatically copies the APK set to the release directory:

```
app/standardAccrescent/release/app-<variant>.apks
```

For example:
```
app/standardAccrescent/release/app-standardAccrescentRelease.apks
```

The original build output remains at:
```
app/build/outputs/apkset/<variant>/app-<variant>.apks
```

## Testing the APK Set

Before uploading to Accrescent, test the APK set locally:

```bash
# Install on connected device/emulator
bundletool install-apks --apks=app/standardAccrescent/release/app-standardAccrescentRelease.apks
```

## Publishing to Accrescent

1. Go to [Accrescent Developer Console](https://accrescent.app/developers)
2. Create or select your app
3. Upload the `.apks` file
4. Fill in required metadata
5. Submit for review

## Security Notes

- ✅ **Encrypted file (.gpg)**: Safe to commit to version control
- ❌ **Plaintext file (.env)**: Never commit, delete immediately after encryption
- ❌ **local.properties**: Never commit (already in .gitignore)
- 🔒 **Passwords**: Only stored encrypted, never in plaintext on disk

## Troubleshooting

### "Cannot read passwords interactively"

This means the build script couldn't access the console. Solution:
- Run from terminal, not from IDE
- Use the provided `make accrescent` command
- Or set environment variables manually

### "Failed to decrypt"

Check:
- Is `accrescent-signing.env.gpg` present?
- Did you enter the correct GPG passphrase?
- Was the file encrypted correctly?

### "Signing configuration incomplete"

Check:
- `local.properties` has `accrescent.storeFile` and `accrescent.keyAlias`
- Keystore file exists at the specified path
- Environment variables are properly exported in the encrypted file

## CI/CD Integration

For CI/CD, you can use environment variables directly:

```bash
export ACCRESCENT_STORE_PASSWORD="password"
export ACCRESCENT_KEY_PASSWORD="password"
./gradlew buildApksStandardAccrescentRelease --no-daemon
```

Store these as encrypted secrets in your CI/CD system (GitHub Secrets, GitLab CI Variables, etc.).

## Requirements

And-Bible meets all Accrescent requirements:

- ✅ Target SDK: 35
- ✅ No debuggable flag
- ✅ No testOnly flag
- ✅ Cleartext traffic disabled (HTTPS only)
- ✅ No self-update mechanism
- ✅ No sensitive permissions (except READ_EXTERNAL_STORAGE ≤ API 28)
- ✅ Production signing key (not debug)

## Further Information

- [Accrescent Documentation](https://accrescent.app/docs)
- [Bundletool Documentation](https://developer.android.com/studio/command-line/bundletool)
- [App Signing Best Practices](https://developer.android.com/studio/publish/app-signing)
