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

package net.bible.android

import net.bible.service.sword.SwordEnvironmentInitialisation
import org.crosswire.common.util.CWProject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BibleApplication.onCreate] runs `initialiseJSwordFolders` before `startKoin`, and its catch-all swallows any
 * failure. Code reachable from it must therefore never resolve a Koin port (the G1 ports included): the failure would
 * be silent and leave JSword on its default folders. Rerun after every task that touches that path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StartupDoesNotResolveG1PortsTest {
    /**
     * `CWProject` establishes its project home once per process (the first writer wins), so after any earlier test in
     * this JVM `writableProjectDir` is that test's stale directory. The singleton is swapped for a fresh one for the
     * call and the original restored afterwards, so the directory assertion can only be satisfied by this call.
     */
    @Test
    fun initialiseJSwordFoldersReachesItsSuccessPathWithoutKoin() {
        val sword = SwordEnvironmentInitialisation::class.java.getDeclaredField("isSwordLoaded")
        sword.isAccessible = true
        val wasLoaded = sword.getBoolean(SwordEnvironmentInitialisation)
        val instanceField = CWProject::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val originalProject = instanceField.get(null)
        try {
            CWProject::class.java.getDeclaredMethod("reset").apply { isAccessible = true }.invoke(null)
            sword.setBoolean(SwordEnvironmentInitialisation, false)
            GlobalContext.stopKoin()
            SwordEnvironmentInitialisation.initialiseJSwordFolders()
            assertTrue("initialisation did not reach its success path", sword.getBoolean(SwordEnvironmentInitialisation))
            val dir = java.io.File(CWProject.instance().writableProjectDir!!).absolutePath
            assertTrue("$dir not under ${SharedConstants.modulesDir}", dir.startsWith(SharedConstants.modulesDir.absolutePath))
        } finally {
            BibleApplication.application.onTerminate()
            BibleApplication.application.onCreate() // restarts Koin
            sword.setBoolean(SwordEnvironmentInitialisation, wasLoaded)
            instanceField.set(null, originalProject)
            BibleApplication.application.onTerminate()
        }
    }
}
