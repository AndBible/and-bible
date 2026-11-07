# Arch Linux package (AUR `andbible-bin`)

`PKGBUILD` repackages the Linux x64 tarball of a GitHub release (`andbible-desktop-<tag>-linux-x64.tar.gz`,
built by `:desktopApp:desktopTarball` in `.github/workflows/desktop.yml`) as a pacman package for Arch Linux and
its derivatives (Omarchy, CachyOS, EndeavourOS, Manjaro). Nothing is compiled: the tarball is a jpackage app
image with its own Java runtime and Chromium (JCEF), the same image the `.deb` and the AppImage contain.

## What the package installs

| Path | Content |
| --- | --- |
| `/opt/andbible/` | the app image as released (`bin/andbible`, `lib/app/*.jar`, `lib/runtime/`, Chromium natives) |
| `/usr/bin/andbible` | symlink to `/opt/andbible/bin/andbible` (the jpackage launcher resolves itself through `/proc/self/exe`) |
| `/usr/share/applications/org.andbible.AndBible.desktop` | `desktopApp/packaging/linux/org.andbible.AndBible.desktop` from the release tag, with `Exec=andbible %F` and `Icon=org.andbible.AndBible` like the AppImage and Flatpak |
| `/usr/share/icons/hicolor/512x512/apps/org.andbible.AndBible.png` | the icon from the tarball |
| `/usr/share/metainfo/org.andbible.AndBible.metainfo.xml` | the AppStream metadata from the release tag |

Pacman's standard hooks refresh the desktop database and icon cache, so the app appears in the launcher
(Omarchy's `walker`, GNOME, KDE, ...) right after `pacman -U` / `yay -S`.

### The package marks itself for the app

`package()` adds `java-options=-Dandbible.systemPackage=aur` to the `[JavaOptions]` section of
`/opt/andbible/lib/app/andbible.cfg`. `detectDistribution(...)` in
`sharedUi/src/jvmMain/kotlin/net/bible/sharedui/desktop/update/DesktopDistribution.kt` turns that property into
`DesktopDistribution.SYSTEM_PACKAGE`, which does not run the app's own GitHub update check: a pacman install is
updated by pacman, the way Flatpak and MSIX installs are updated by their stores. Without the marker the same
`/opt/andbible` layout is detected as `GITHUB_PACKAGE` (the `.deb`), which asks the user whether to check GitHub
for updates and sends them to the release page.

The build fails if the `.cfg` has no `[JavaOptions]` section or the line did not land, so a future change to the
jpackage layout is caught in `makepkg`, not on the user's machine.

### Dependencies

`depends` lists the system libraries the tarball's binaries name in their `NEEDED` entries (the bundled JBR
runtime, `libcef.so`, `jcef_helper`, Skiko, SQLite), mapped to Arch packages, plus `gtk3`, which AWT and Chromium
load at run time with `dlopen`, and `hicolor-icon-theme` for the icon directory. To recheck them after a JBR or
JCEF bump, unpack the tarball and run on an Arch machine:

```bash
find andbible -type f \( -name '*.so*' -o -perm -u+x \) -print0 | xargs -0 -n50 readelf -d 2>/dev/null \
  | grep NEEDED | sed -E 's/.*\[(.*)\]/\1/' | sort -u
```

Drop the libraries the tarball ships itself (`libjvm.so`, `libjawt.so`, `libcef.so`, ...), then `pacman -Qo` the
rest. `namcap <package>.pkg.tar.zst` reports the same from a built package.

## Updating the package to a new release

```bash
GITHUB_TOKEN=$(gh auth token) python3 -I packaging/arch/update-pkgbuild.py production-1300
```

The script reads the release from the GitHub API (version from the release name, the tarball's SHA-256 from the
asset digest GitHub computes), hashes the `.desktop` and metainfo files at that tag, rewrites `_tag`, `pkgver`,
`pkgrel` and `sha256sums` in `PKGBUILD`, and regenerates `.SRCINFO` with `makepkg --printsrcinfo`. `--pkgrel 2`
re-releases the same upstream version after a packaging fix. `python3 -I packaging/arch/test-update-pkgbuild.py`
is its offline test and also checks that `.SRCINFO` agrees with `PKGBUILD`.

Build and install locally to test:

```bash
cd packaging/arch
makepkg -si          # downloads the tarball (~360 MB), builds andbible-bin-<version>-1-x86_64.pkg.tar.zst, installs it
namcap PKGBUILD *.pkg.tar.zst
```

## Hosting on the AUR

The AUR takes a git repository with `PKGBUILD` and `.SRCINFO` at its root, nothing else. This directory is the
source of those two files; the AUR repository is a mirror of it.

1. Create an account on <https://aur.archlinux.org> and add an SSH public key to it.
2. First publication (the package is created by the first push):
   ```bash
   git clone ssh://aur@aur.archlinux.org/andbible-bin.git /tmp/aur-andbible-bin
   cp packaging/arch/PKGBUILD packaging/arch/.SRCINFO /tmp/aur-andbible-bin/
   cd /tmp/aur-andbible-bin && git add PKGBUILD .SRCINFO && git commit -m "Initial import: 6.0.1300" && git push origin master
   ```
3. Every later release: run `update-pkgbuild.py <tag>` here, commit it, then copy the two files to the AUR clone and
   push with a commit message such as `Update to 6.1.1310`. Users get the update with `yay -Syu` (or `paru`).

Channel policy: the AUR package name has no channel in it, so it should follow one channel. `andbible-bin` is for
production releases (`production-*` tags); publish it when the first production desktop release exists. A beta
channel can be a second package, `andbible-beta-bin` (`provides=('andbible')`, `conflicts=('andbible')`, so only one
of them can be installed). Test releases (`test-*`) are developer builds and do not belong on the AUR; this
`PKGBUILD` points at one only until a production or beta desktop release exists, and that is fine for local
`makepkg -si` use.

The AUR has no CI: every push is published as is. The `arch-pkgbuild` job in `.github/workflows/desktop.yml`
checks this directory on every PR instead (`.SRCINFO` in sync with `PKGBUILD`, `namcap` on the `PKGBUILD`, the
offline tests), so what is copied to the AUR has passed that.

## Alternatives considered

- **A `.pkg.tar.zst` attached to each GitHub release** (CI running `makepkg` in an `archlinux` container):
  installs with `pacman -U` but gives no update path, and every tarball download in CI would be repeated per build.
- **Flatpak** (`packaging/flatpak/`): distribution-independent, but Omarchy does not ship Flatpak by default, and the
  Flathub submission is a separate maintainer step.
- **Building from source in the PKGBUILD**: needs JDK 17, Node 24, Android SDK parts and a long Gradle run on the
  user's machine; not reasonable for a `-bin` style app of this size.
