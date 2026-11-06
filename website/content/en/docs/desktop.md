# AndBible on Linux and Windows (beta)

AndBible for Linux and Windows is in beta. Download desktop packages from [GitHub Releases](https://github.com/AndBible/and-bible/releases). On Linux x86-64, use the `.deb` package or make the AppImage executable and run it. On Windows x64, the MSI installer is available. Flatpak distribution through Flathub is planned, but is not yet available there.

## Windows reading engine

On Windows x64, AndBible uses the system **Microsoft Edge WebView2 Evergreen Runtime** for document text. Windows normally keeps this runtime up to date independently of AndBible. The MSI includes the small WebView2 loader, not a bundled Chromium browser.

If WebView2 is missing or cannot start, AndBible shows an error with the [WebView2 Runtime download link](https://developer.microsoft.com/microsoft-edge/webview2/) and the log file location, then closes. Install or repair the Evergreen Runtime and try again. The app does not download a browser at startup.

The reading view requires the Direct3D graphics renderer. If the window uses a different renderer, AndBible shows a graphics-specific error instead of an empty reading window. Remove any forced software/OpenGL renderer override and check your graphics driver; installing WebView2 alone does not solve a graphics-renderer error. Diagnostic details are logged in `%APPDATA%\AndBible\logs\andbible.log` and included in bug reports.

Switching workspaces reuses the reading pages. Returning to a workspace restores its StudyPad, Bible and commentary panes without restarting the browser page.

## Linux packages

The regular `andbible` `.deb` includes Chromium (KCEF). It remains the default Linux package, and the AppImage also uses Chromium.

The optional `andbible-webkitgtk` `.deb` uses the system's **WebKitGTK 6.0 (GTK4)** instead. It does not include Chromium's native libraries, so the download is smaller and WebKit security updates come from your Linux distribution. It requires Linux x86-64 and a distribution that provides `libwebkitgtk-6.0-4`; install the `.deb` with your package manager so that dependency is installed too. This variant needs X11, or Xwayland in a Wayland desktop session. It is not published with the current releases while it is being improved; it can be built from source with `./gradlew :desktopWebkitGtk:packageDeb`.

The two `.deb` packages have separate launchers but share the same AndBible data and settings. Only one can use that data at a time. The WebKitGTK variant is a beta alternative; keep the regular Chromium package available if you encounter rendering or input problems.

## Display scaling on Linux

AndBible follows your desktop's display scale: the menus, toolbars and the Bible text get the same size as in other apps. It reads the scale that GNOME, KDE Plasma and Xfce publish for X11 apps (the `Xft.dpi` setting, which also includes GNOME's "Large Text" setting), or `GDK_SCALE` when it is set. On Wayland the app runs through Xwayland, so the scale comes from what the desktop gives Xwayland apps.

If the size is still wrong, set it yourself with the `ANDBIBLE_UI_SCALE` environment variable, from `0.5` to `4` (fractions such as `1.25` work):

```bash
ANDBIBLE_UI_SCALE=1.5 andbible
```

For the Flatpak use `flatpak override --user --env=ANDBIBLE_UI_SCALE=1.5 org.andbible.AndBible`. The chosen scale and where it came from are written at start-up to the log (`~/.local/share/andbible/logs`) as a line starting with `display scale`; please include that line when you report a scaling problem.

## Update checks

Eligible GitHub builds ask whether to check for updates. If you agree, AndBible contacts GitHub at most once a day. GitHub sees your IP address and the app version in the user agent. No cookies or device identifier are sent. You can change your choice using **Check for updates** in Settings. When a newer version is found, AndBible can open its release page; you install it yourself. Flatpak and MSIX use their distribution channels instead and do not offer this check.

## Where your data lives

On Linux, AndBible stores data in `$XDG_DATA_HOME/andbible` (normally `~/.local/share/andbible`), configuration in `$XDG_CONFIG_HOME/andbible` (normally `~/.config/andbible`), and cache in `$XDG_CACHE_HOME/andbible` (normally `~/.cache/andbible`). Flatpak uses its own XDG directories under `~/.var/app/org.andbible.AndBible/`, with `andbible` appended under `data`, `config`, and `cache`.

On Windows, data is in `%APPDATA%\AndBible`, configuration in its `config` subdirectory, and cache in `%LOCALAPPDATA%\AndBible\cache`. Bible documents are stored in the `sword` directory under the data directory.

For what the beta does not do yet, see [Desktop beta: known limitations](desktop-beta.md).
