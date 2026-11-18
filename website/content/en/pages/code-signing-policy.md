---
title: Code signing policy
---

Free code signing for the Windows packages provided by [SignPath.io](https://about.signpath.io), certificate by
[SignPath Foundation](https://signpath.org).

## Android

Google Play and the GitHub release APKs use AndBible's own release key, which is also the
Google Play app signing key. F-Droid builds AndBible from source and signs it with F-Droid's
own key: F-Droid and Play/GitHub installations cannot update each other. Amazon, Samsung,
Huawei and Accrescent builds are distributed through their respective stores.

## iPhone, iPad and Mac

App Store and TestFlight builds are signed through Apple's distribution signing.

## Windows

The Windows packages of AndBible are signed:

- the MSI installer (`andbible-desktop-<version>-x64.msi`), and
- the program `AndBible.exe`, inside the MSI and inside the portable zip.

They are built by GitHub Actions on GitHub-hosted runners from the public source code in the
[AndBible/and-bible](https://github.com/AndBible/and-bible) repository, and the build sends them to
SignPath for signing. The workflow is `.github/workflows/desktop.yml`, and the signed files are
listed in `.signpath/artifact-configuration.xml`.

Only AndBible's own files are signed with this certificate. Third-party components shipped inside
the packages, the Java runtime and Microsoft's WebView2 loader, keep their original publishers'
signatures. The Microsoft Store version (MSIX) is signed by Microsoft.

## Linux and GitHub release provenance

The Linux `.deb`, AppImage and tarball are not code-signed. Starting with releases made after
this policy change, the APKs and desktop packages uploaded to GitHub releases carry GitHub
build provenance attestations. These associate each file with this repository's workflow run
for the release tag. Desktop packages are built in earlier jobs of that run, and Windows
packages are signed before the release job; the attestation itself does not independently
prove those build or signing steps. Provenance is
not a publisher's code signature. GitHub's automatically generated source archives are not
covered. Verify an uploaded package with the
[GitHub CLI](https://cli.github.com/):

```sh
gh attestation verify <file> --repo AndBible/and-bible
```

Older releases may not have attestations. APK and Windows signing remains separate from this
provenance verification.

## Team roles

- **Committers and reviewers:** [tuomas2](https://github.com/tuomas2) (Tuomas Airaksinen, maintainer).
  Contributions from anyone else come as pull requests and are reviewed by the maintainer before
  they are merged. The release branches are protected.
- **Approvers:** [tuomas2](https://github.com/tuomas2). Every release build needs the maintainer's
  approval, and every release signing request is approved by hand in SignPath.

All team members use multi-factor authentication for GitHub and SignPath.

## Privacy

This program will not transfer any information to other networked systems unless specifically
requested by the user or the person installing or operating it. The features that do connect to
the network (downloading documents, optional cloud sync, the optional AI assistant, bug reports
and the desktop update check) are described in the [privacy policy](/privacy/).

## Uninstalling

- **MSI installation:** open Windows *Settings → Apps → Installed apps*, choose **AndBible** and
  then **Uninstall**.
- **Portable zip:** close AndBible and delete the folder you extracted.

Uninstalling removes the program but keeps your data in `%APPDATA%\AndBible` and its cache in
`%LOCALAPPDATA%\AndBible`. Delete those folders too if you want to remove your documents,
bookmarks, notes and settings.
