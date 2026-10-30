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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAppDirectoriesTest {
    private val home: Path = Paths.get("/home/ada")

    private fun linux(vararg env: Pair<String, String>) = DesktopPaths(env.toMap()::get, "Linux", home)

    @Test fun linuxDefaultsUnderHome() {
        val p = linux()
        assertEquals(Paths.get("/home/ada/.local/share/andbible"), p.data)
        assertEquals(Paths.get("/home/ada/.config/andbible"), p.config)
        assertEquals(Paths.get("/home/ada/.cache/andbible"), p.cache)
        assertEquals(Paths.get("/home/ada/.local/share/andbible/sword"), p.sword)
    }

    @Test fun xdgVariablesAreHonoured() {
        val p = linux("XDG_DATA_HOME" to "/d", "XDG_CONFIG_HOME" to "/c", "XDG_CACHE_HOME" to "/k")
        assertEquals(Paths.get("/d/andbible"), p.data)
        assertEquals(Paths.get("/c/andbible"), p.config)
        assertEquals(Paths.get("/k/andbible"), p.cache)
        assertEquals(Paths.get("/d/andbible/sword"), p.sword)
    }

    @Test fun relativeAndBlankXdgValuesAreIgnored() {
        val p = linux("XDG_DATA_HOME" to "relative/dir", "XDG_CONFIG_HOME" to "  ", "XDG_CACHE_HOME" to "")
        assertEquals(Paths.get("/home/ada/.local/share/andbible"), p.data)
        assertEquals(Paths.get("/home/ada/.config/andbible"), p.config)
        assertEquals(Paths.get("/home/ada/.cache/andbible"), p.cache)
    }

    @Test fun windowsUsesAppDataAndLocalAppData() {
        val env = mapOf("APPDATA" to "C:\\Users\\ada\\AppData\\Roaming", "LOCALAPPDATA" to "D:\\Local")
        val p = DesktopPaths(env::get, "Windows 11", home)
        val fs = home.fileSystem
        assertEquals(fs.getPath("C:\\Users\\ada\\AppData\\Roaming").resolve("AndBible"), p.data)
        assertEquals(p.data.resolve("config"), p.config)
        assertEquals(fs.getPath("D:\\Local").resolve("AndBible").resolve("cache"), p.cache)
    }

    @Test fun windowsFallsBackToHomeWhenVariablesAreMissing() {
        val p = DesktopPaths({ null }, "Windows 10", home)
        assertEquals(Paths.get("/home/ada/AppData/Roaming/AndBible"), p.data)
        assertEquals(Paths.get("/home/ada/AppData/Local/AndBible/cache"), p.cache)
    }

    @Test fun windowsIgnoresXdgVariables() {
        val p = DesktopPaths(mapOf("XDG_DATA_HOME" to "/x")::get, "Windows 10", home)
        assertTrue(p.data.toString().endsWith("AndBible"))
    }

    @Test fun directoriesAreCreatedWhenRead() {
        val root = Files.createTempDirectory("desktop-dirs")
        val dirs = DesktopAppDirectories(DesktopPaths({ null }, "Linux", root))
        val modules = dirs.modulesDir.toNioPath()
        assertEquals(root.resolve(".local/share/andbible/sword"), modules)
        assertTrue(Files.isDirectory(modules))
        assertTrue(Files.isDirectory(dirs.manualReadingPlanDir.toNioPath()))
        assertTrue(Files.isDirectory(dirs.manualInstallDir2.toNioPath()))
        assertTrue(Files.isDirectory(dirs.internalFilesDir.toNioPath()))
        assertTrue(Files.isDirectory(dirs.manualInstallDir.toNioPath()))
    }

    @Test fun windowsPathsWithSpacesAndUmlauts() {
        val roaming = "C:\\Users\\Jänis Ö\\AppData\\Roaming"
        val local = "C:\\Users\\Jänis Ö\\AppData\\Local"
        val p = DesktopPaths(mapOf("APPDATA" to roaming, "LOCALAPPDATA" to local)::get, "Windows 11", home)
        val fs = home.fileSystem
        assertEquals(fs.getPath(roaming).resolve("AndBible"), p.data)
        assertEquals(p.data.resolve("config"), p.config)
        assertEquals(fs.getPath(local).resolve("AndBible").resolve("cache"), p.cache)
        assertEquals(p.data.resolve("sword"), p.sword)
        assertEquals(p.cache.resolve("cef"), p.cefCache)
        assertTrue(p.cefCache.toString().contains("Jänis Ö"))

        // Same layout on the real file system, in directories with a space and an umlaut: the module install folder
        // (the Sword path) and the CEF cache are created and usable.
        val base = Files.createTempDirectory("Jänis Ö")
        val real = DesktopPaths(
            mapOf("APPDATA" to base.resolve("Roaming").toString(), "LOCALAPPDATA" to base.resolve("Local").toString())::get,
            "Windows 11", base,
        )
        val dirs = DesktopAppDirectories(real)
        val modules = Paths.get(dirs.modulesDir.toString())
        assertEquals(real.sword, modules)
        assertTrue(Files.isDirectory(modules) && modules.toString().contains("Jänis Ö"))
        Files.createDirectories(real.cefCache)
        Files.writeString(real.cefCache.resolve("Cookies"), "x")
        assertTrue(Files.isRegularFile(real.cefCache.resolve("Cookies")))
    }
}
