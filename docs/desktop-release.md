# Desktop release

## Windows

Windows packaging requires Windows x64, JDK 17, Node 24, the Windows SDK desktop tools (MakeAppx and SignTool), and WiX Toolset 3 (3.14 in CI). Build BibleView first with `npm ci && npm run build-production` in `app/bibleview-js`, then run:

```powershell
.\gradlew.bat :desktopApp:packageWindowsZip :desktopApp:packageWindowsMsix :desktopApp:packageWindowsMsi
```

The `packageWindowsMsi` task invokes jpackage with the built app image and the dedicated `packaging/windows/jpackage-resources` directory; the post-image WiX resource assigns `org.andbible.AndBible` to its AndBible shortcuts. The raw icon is staged at `app/resources/andbible.ico` for notification registration. Portable ZIP builds have no installer-created Start menu shortcut, so Windows toast identity and notification delivery are not guaranteed. Verify the installed MSI shortcut and notification delivery on Windows.

The versioned zip, MSIX and MSI are in `desktopApp/build/compose/binaries/main/`. The MSI in `main/msi/AndBible-<version>.msi` is the intermediate unsigned jpackage output, not the signed release copy. Use `-PwindowsPackageVersion=<version>` for a test upgrade build; do not reuse a release version for a different payload.

### Reading engines

The Windows distribution uses only the system WebView2 Evergreen runtime. It does not bundle CEF natives or support the retired `andbible.windows.webEngine=kcef` override. Linux KCEF packaging is unchanged. `WebView2Loader.dll` and its licence (from the pinned Microsoft.Web.WebView2 SDK 1.0.4191.47, fetched and SHA-256-verified by `prepareWebView2Loader`) are staged into `resources/webview2/`; the app never downloads a browser on startup. For an offline build pass `-Pandbible.webview2Archive=<local .nupkg>`; it is verified against the same archive digest. Windows resource preparation deletes any old staged `kcef-natives` directory, and app-image/ZIP assertions reject a leftover CEF payload.

The actual Compose window must use Direct3D. Missing/unusable WebView2 shows a localized error with the Evergreen download URL and log path; a different renderer shows a distinct graphics error without suggesting a runtime download will fix it. Both paths close instead of presenting blank reading panes. Exercise the latter with `-Dskiko.renderApi=SOFTWARE`, checking the actual `SOFTWARE_FAST` renderer. A missing-runtime test can point `WEBVIEW2_BROWSER_EXECUTABLE_FOLDER` to an empty test folder. Use separate profiles, inspect the visible dialog, and include only app-owned browser processes in cleanup (not Windows widgets' WebView2).

`compose.interop.blending=true` remains an experimental global Compose dependency. The Windows smoke checks the WebView2 engine log, loader presence, absence of CEF natives, readiness and clean exit. Native visual tests still run in an interactive Windows session; SSH-only windows do not establish correct rendering.

### Identity and signing

The MSIX identity `AndBible.Desktop` and publisher `CN=AndBible-Unsigned` are placeholders in the `windowsIdentity` block in `desktopApp/build.gradle.kts`. Before a Store release, reserve the app name in Partner Center, obtain the package identity name and publisher from its app identity page, and replace both values in that block. Verify the resulting manifest against Partner Center and use a certificate whose subject matches the publisher. The Store identity and production certificate are still pending; placeholder packages are not Store-ready. Follow Partner Center's current package submission, certification, and publishing steps after uploading the release package.

CI signs the Windows packages through the [SignPath Foundation](https://signpath.org) program, not with a certificate of its own. The `windows` job in `.github/workflows/desktop.yml` builds and smoke-tests unsigned packages and uploads the zip and MSI as the artifact `andbible-desktop-windows-x64-unsigned`; the `windows-sign` job submits it with `signpath/github-action-submit-signing-request`; SignPath signs `AndBible.exe` inside the zip and the MSI (deep signing) and the MSI itself, and the job then checks that the signatures are present and uploads the signed files as `andbible-desktop-windows-x64`, which the release uses. The MSIX is not sent to SignPath: it is only for the Microsoft Store, which signs submitted packages itself. What is signed is defined in `.signpath/artifact-configuration.xml`; keep it identical to the artifact configuration `windows-desktop` in the SignPath project; the workflow names it explicitly. Only AndBible's own binaries are signed: the bundled JDK runtime and `WebView2Loader.dll` are third-party files (SignPath Foundation policy).

`windows-sign` runs for every non-pull-request run (tags and manual runs) in the GitHub environment `And Bible Builds`; only that job sees the token. The job waits up to three hours for SignPath (malware scan and, for release signing, the approver's manual approval); `scripts/increment-version.sh` approves the environment deployment, reminds you to approve the SignPath request and reports the result. Configure these in that environment:

| Name | Kind | Value |
| --- | --- | --- |
| `SIGNPATH_API_TOKEN` | secret | API token of the SignPath CI user (submitter of the signing policy) |
| `SIGNPATH_ORGANIZATION_ID` | variable | SignPath organization id |
| `SIGNPATH_PROJECT_SLUG` | variable | project slug, default `and-bible` |
| `SIGNPATH_ARTIFACT_CONFIGURATION_SLUG` | variable | artifact configuration slug, default `windows-desktop` |
| `SIGNPATH_SIGNING_POLICY_SLUG` | variable | `test-signing` (default, self-signed test certificate) or `release-signing` once the production certificate exists |
| `WINDOWS_MSIX_PUBLISHER` | repository variable (read by the `windows` build job, not the environment) | optional; the Partner Center publisher (`CN=<GUID>`), becomes the MSIX `Publisher` (`-PwindowsPublisher`) |

The MSIX `Publisher` must match the Partner Center publisher for a Store submission; Microsoft re-signs the package, so it needs no SignPath signature. A sideloaded MSIX would instead need a signature whose certificate subject equals `Publisher`; AndBible does not publish one.

For local signing outside CI, set `ANDBIBLE_WIN_CERT` to a PFX file path and `ANDBIBLE_WIN_CERT_PASSWORD` to its password; packages are unsigned if both are absent. Never commit a certificate or password. Signing requires an RFC 3161 timestamp, by default `http://timestamp.digicert.com`; override it with `-PwindowsTimestampUrl=<url>`. Timestamp failure fails the build. For a disposable self-signed certificate in the isolated test VM only, `-PwindowsTestSigning=true` disables timestamping; never use it for a release. Trust a self-signed test certificate only in that VM.

### Installation and updates

MSIX and MSI are intended to use the real `%APPDATA%\\AndBible` data directory, not an MSIX private LocalCache. Both can be installed simultaneously and have separate Start menu entries, but share that directory and the single-instance lock; only one instance should run at a time. Uninstall removes the program, not the user's data. Verify upgrade, coexistence and uninstall behavior on a Windows VM before release; these are not claims that the live checks have already passed.

Windows reserves the `.ttf` association in MSIX and the SDK rejects it. Install fonts through AndBible's in-app font import instead. Other registered file associations should be verified in Explorer.

Store distribution uses Store updates. Eligible GitHub MSI builds can, with consent, notify users of a newer version and open its GitHub release page. They do not install updates automatically.

### winget submission

`packaging/winget/manifests/a/AndBible/Desktop/6.0.1230/` is an **unpublishable template**: its URL is a future-release placeholder and its all-zero SHA256 is not an installer hash. After publishing the actual signed MSI to a stable public release URL, copy the manifests into the target version directory, update all `PackageVersion` values, replace `InstallerUrl` with that exact release asset URL, and replace `InstallerSha256` with the SHA256 of the downloaded release MSI (`Get-FileHash -Algorithm SHA256 <file>`). Confirm publisher, identifier, license and metadata against the actual release.

Run `winget validate --manifest <version-directory>` on Windows and resolve all errors. Then follow the current microsoft/winget-pkgs contribution instructions to submit the three manifests in a PR; validation alone does not publish anything. No public PR has been made.

## Linux

On Linux x86-64, JDK 17 and Node 24, build the packages with:

```sh
./gradlew -PdesktopReleaseKind=test :desktopApp:packageDeb :desktopApp:desktopTarball :desktopApp:packageAppImage
```

The Flatpak manifest uses the freedesktop 25.08 runtime, OpenJDK 17 and Node 24. Regenerate its pinned npm and Gradle sources using `bash packaging/flatpak/update-sources.sh` after installing the generator and prerequisites described in `packaging/flatpak/README.md`. Review the source changes and validate a clean `--disable-download` build. The manifest application source commit must be updated to the intended release commit; CI overrides it with the exact workflow commit for its own build.

For Flathub, submit the manifest, source lists and packaging files according to the current Flathub contribution instructions. Domain verification is a separate maintainer action: publish the token Flathub provides at `https://andbible.org/.well-known/org.flathub.VerifiedApps.txt`. Neither the CI bundle nor these instructions publish the app to Flathub.

## Release train

A push of `test-<build>`, `beta-<build>` or `production-<build>` runs the desktop workflow. Linux and Windows packages receive `-PdesktopReleaseKind=test`, `beta` or `production` from the tag; ordinary builds default to `dev`. Dev builds do not check for updates. Channels are ordered by stability, test < beta < production, and an installation is offered a newer release only on its own channel or a more stable one: test sees test, beta and production; beta sees beta and production; production sees production only. This selection uses the tag, not GitHub's prerelease flag. The Flatpak bundle is a CI artifact, not a GitHub release asset.

The desktop release job waits up to 30 minutes for `build-apk.yml` to create the release, then uploads exactly these assets (`<tag>` is the full tag):

- `andbible-desktop-<tag>-amd64.deb`
- `andbible-desktop-<tag>-x86_64.AppImage`
- `andbible-desktop-<tag>-linux-x64.tar.gz`
- `andbible-desktop-<tag>-x64.msi`

The update check is available only to eligible GitHub builds, not Flatpak or MSIX. It asks for consent before contacting GitHub, and can be changed in Settings. With consent, it requests GitHub releases at most once per 24 hours; GitHub sees the IP address and `AndBible-Desktop/<version>` user agent. No cookies or device identifier are sent. Failures are silent. A newer release opens a prompt linking to GitHub; installation remains manual.
