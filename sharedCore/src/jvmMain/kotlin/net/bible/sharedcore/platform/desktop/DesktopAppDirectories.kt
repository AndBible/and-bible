/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedcore.platform.desktop

import net.bible.sharedcore.platform.AppDirectories
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.nio.file.Path as NioPath
import java.nio.file.Paths

/**
 * Where the desktop app keeps its files. [env] reads environment variables (`System::getenv` in production), [osName]
 * is `os.name` and [home] the user's home directory; all three are injectable so every platform's layout is testable on
 * Linux.
 *
 * - Linux and other Unix: `$XDG_DATA_HOME/andbible`, `$XDG_CONFIG_HOME/andbible`, `$XDG_CACHE_HOME/andbible`, falling
 *   back to `~/.local/share`, `~/.config` and `~/.cache`. As the XDG specification says, a relative variable is ignored.
 * - Windows: `%APPDATA%\AndBible` (config in its `config` subdirectory) and `%LOCALAPPDATA%\AndBible\cache`.
 * - macOS: `~/Library/Application Support/AndBible` and `~/Library/Caches/AndBible`.
 *
 * Nothing is created here; [DesktopAppDirectories] and the users of each directory create what they need.
 */
class DesktopPaths(
    env: (String) -> String?,
    osName: String,
    home: NioPath,
) {
    /** Application data: databases, Sword modules, logs, the instance lock. */
    val data: NioPath

    /** Device-local configuration (preferences). */
    val config: NioPath

    /** Disposable files. */
    val cache: NioPath

    /** Sword modules: `<data>/sword`. */
    val sword: NioPath

    /** CEF's profile and cache: `<cache>/cef`. */
    val cefCache: NioPath

    init {
        val fs = home.fileSystem
        fun variable(name: String, requireAbsolute: Boolean): NioPath? {
            val value = env(name)?.trim().orEmpty()
            if (value.isEmpty()) return null
            val path = fs.getPath(value)
            return path.takeUnless { requireAbsolute && !path.isAbsolute }
        }
        val os = osName.lowercase()
        when {
            os.startsWith("windows") -> {
                val appData = variable("APPDATA", false) ?: home.resolve("AppData").resolve("Roaming")
                val localAppData = variable("LOCALAPPDATA", false) ?: home.resolve("AppData").resolve("Local")
                data = appData.resolve("AndBible")
                config = data.resolve("config")
                cache = localAppData.resolve("AndBible").resolve("cache")
            }
            os.startsWith("mac") -> {
                val library = home.resolve("Library")
                data = library.resolve("Application Support").resolve("AndBible")
                config = data.resolve("config")
                cache = library.resolve("Caches").resolve("AndBible")
            }
            else -> {
                data = (variable("XDG_DATA_HOME", true) ?: home.resolve(".local").resolve("share")).resolve("andbible")
                config = (variable("XDG_CONFIG_HOME", true) ?: home.resolve(".config")).resolve("andbible")
                cache = (variable("XDG_CACHE_HOME", true) ?: home.resolve(".cache")).resolve("andbible")
            }
        }
        sword = data.resolve("sword")
        cefCache = cache.resolve("cef")
    }

    companion object {
        /** The paths of the running process. */
        fun system(): DesktopPaths =
            DesktopPaths(System::getenv, System.getProperty("os.name") ?: "", Paths.get(System.getProperty("user.home")))
    }
}

/**
 * [AppDirectories] on the desktop: Sword modules in `<data>/sword`, the manual-install folders under `<data>`. Every
 * directory is created when it is read, as on iOS.
 */
class DesktopAppDirectories(private val paths: DesktopPaths) : AppDirectories {
    private fun ensured(path: NioPath): Path {
        Files.createDirectories(path)
        return path.toOkioPath()
    }

    override val internalFilesDir: Path get() = ensured(paths.data)
    override val modulesDir: Path get() = ensured(paths.sword)
    override val manualInstallDir: Path get() = ensured(paths.data.resolve("manual"))
    override val manualInstallDir2: Path get() = ensured(paths.data.resolve("manual2"))
    override val manualReadingPlanDir: Path get() = ensured(paths.data.resolve("manual").resolve("readingplan"))
}
