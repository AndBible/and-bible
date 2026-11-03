# AndBible on Linux and Windows (beta)

AndBible for Linux and Windows is in beta. Download desktop packages from [GitHub Releases](https://github.com/AndBible/and-bible/releases). On Linux x86-64, use the `.deb` package or make the AppImage executable and run it. On Windows x64, the MSI installer is available. Flatpak distribution through Flathub is planned, but is not yet available there.

## Windows reading engine

On Windows x64, AndBible uses the system **Microsoft Edge WebView2 Evergreen Runtime** for document text when the graphics renderer supports it. Windows normally keeps this runtime up to date independently of AndBible.

The MSI also includes a Chromium-based fallback. AndBible selects it automatically if WebView2 is unavailable or the window cannot use Direct3D. You do not need to install WebView2 manually to use AndBible, and the app does not download a browser at startup. Both engines are included in the same installer, so using the system runtime does not make this MSI substantially smaller. If neither engine can start, AndBible shows an error message naming the cause and the log file location, then closes.

## Update checks

Eligible GitHub builds ask whether to check for updates. If you agree, AndBible contacts GitHub at most once a day. GitHub sees your IP address and the app version in the user agent. No cookies or device identifier are sent. You can change your choice using **Check for updates** in Settings. When a newer version is found, AndBible can open its release page; you install it yourself. Flatpak and MSIX use their distribution channels instead and do not offer this check.

## Where your data lives

On Linux, AndBible stores data in `$XDG_DATA_HOME/andbible` (normally `~/.local/share/andbible`), configuration in `$XDG_CONFIG_HOME/andbible` (normally `~/.config/andbible`), and cache in `$XDG_CACHE_HOME/andbible` (normally `~/.cache/andbible`). Flatpak uses its own XDG directories under `~/.var/app/org.andbible.AndBible/`, with `andbible` appended under `data`, `config`, and `cache`.

On Windows, data is in `%APPDATA%\AndBible`, configuration in its `config` subdirectory, and cache in `%LOCALAPPDATA%\AndBible\cache`. Bible documents are stored in the `sword` directory under the data directory.

For what the beta does not do yet, see [Desktop beta: known limitations](desktop-beta.md).
