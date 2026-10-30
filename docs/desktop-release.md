# Desktop release

## Windows

Windows packaging requires Windows x64, JDK 17, Node 24, the Windows SDK desktop tools (MakeAppx and SignTool), and WiX Toolset 3 (3.14 in CI). Build BibleView first with `npm ci && npm run build-production` in `app/bibleview-js`, then run:

```powershell
.\gradlew.bat :desktopApp:packageWindowsZip :desktopApp:packageWindowsMsix :desktopApp:packageWindowsMsi
```

The versioned zip, MSIX and MSI are in `desktopApp/build/compose/binaries/main/`. The MSI in `main/msi/AndBible-<version>.msi` is the intermediate unsigned jpackage output, not the signed release copy. Use `-PwindowsPackageVersion=<version>` for a test upgrade build; do not reuse a release version for a different payload.

### Identity and signing

The MSIX identity `AndBible.Desktop` and publisher `CN=AndBible-Unsigned` are placeholders in the `windowsIdentity` block in `desktopApp/build.gradle.kts`. Before a Store release, reserve the app name in Partner Center, obtain the package identity name and publisher from its app identity page, and replace both values in that block. Verify the resulting manifest against Partner Center and use a certificate whose subject matches the publisher. The Store identity and production certificate are still pending; placeholder packages are not Store-ready. Follow Partner Center's current package submission, certification, and publishing steps after uploading the release package.

Packages are unsigned if both signing variables are absent. For local signing, set `ANDBIBLE_WIN_CERT` to a PFX file path and `ANDBIBLE_WIN_CERT_PASSWORD` to its password. In GitHub Actions, `ANDBIBLE_WIN_CERT` is instead a base64-encoded PFX secret; the workflow decodes it into runner temporary storage, passes its path to Gradle, and removes it in a finally block. Set `ANDBIBLE_WIN_CERT_PASSWORD` as a separate secret. Never commit a certificate or password. Signing requires an RFC 3161 timestamp, by default `http://timestamp.digicert.com`; override it with `-PwindowsTimestampUrl=<url>`. Timestamp failure fails the build. For a disposable self-signed certificate in the isolated test VM only, `-PwindowsTestSigning=true` disables timestamping; never use it for a release. Trust a self-signed test certificate only in that VM.

### Installation and updates

MSIX and MSI are intended to use the real `%APPDATA%\\AndBible` data directory, not an MSIX private LocalCache. Both can be installed simultaneously and have separate Start menu entries, but share that directory and the single-instance lock; only one instance should run at a time. Uninstall removes the program, not the user's data. Verify upgrade, coexistence and uninstall behavior on a Windows VM before release; these are not claims that the live checks have already passed.

Windows reserves the `.ttf` association in MSIX and the SDK rejects it. Install fonts through AndBible's in-app font import instead. Other registered file associations should be verified in Explorer.

Store distribution uses Store updates. The MSI/winget channel needs a separate update path: the D4b update checker has not been merged, so there is currently no in-app installer/winget update checker. Do not promise automatic MSI updates until that integration exists.

### winget submission

`packaging/winget/manifests/a/AndBible/Desktop/6.0.1230/` is an **unpublishable template**: its URL is a future-release placeholder and its all-zero SHA256 is not an installer hash. After publishing the actual signed MSI to a stable public release URL, copy the manifests into the target version directory, update all `PackageVersion` values, replace `InstallerUrl` with that exact release asset URL, and replace `InstallerSha256` with the SHA256 of the downloaded release MSI (`Get-FileHash -Algorithm SHA256 <file>`). Confirm publisher, identifier, license and metadata against the actual release.

Run `winget validate --manifest <version-directory>` on Windows and resolve all errors. Then follow the current microsoft/winget-pkgs contribution instructions to submit the three manifests in a PR; validation alone does not publish anything. No public PR has been made.
