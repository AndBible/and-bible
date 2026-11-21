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
package net.bible.android.control.report

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.CrashSafeModeActivity
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * #4176: a crash early in the start (before `Application.onCreate`, in a content provider, during the database open or
 * `initializeApp`) must still lead to a crash report on the next start, and the records must survive until the user
 * acts on them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StartupCrashGuardTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    private val app get() = ApplicationProvider.getApplicationContext<TestBibleApplication>()
    private val originalExit = CrashSafeModeActivity.exitProcess

    @org.junit.Before fun neverEndTheTestJvm() {
        CrashSafeModeActivity.exitProcess = {}
        // FileProvider caches its roots per authority in a static map: an earlier test's (deleted) data directory
        // would refuse this test's files.
        androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    @After fun reset() {
        StartupCrashGuard.resetForTest()
        CrashSafeModeActivity.exitProcess = originalExit
        CommonUtils.realSharedPreferences.edit(commit = true) { remove("app-crashed-count"); remove("app-crashed-time") }
    }

    /** A new process: what `BibleApplication.attachBaseContext` does, without the process-global handler. */
    private fun startProcess(dir: File): Boolean = StartupCrashGuard.start(app, StartupCrashStore(dir), installHandler = false)

    private fun crash(e: Throwable): List<Throwable> {
        val handedOn = mutableListOf<Throwable>()
        StartupCrashGuard.Handler { _, t -> handedOn += t }.uncaughtException(Thread.currentThread(), e)
        return handedOn
    }

    private fun showSafeMode(intent: Intent): org.robolectric.android.controller.ActivityController<CrashSafeModeActivity> {
        val controller = Robolectric.buildActivity(CrashSafeModeActivity::class.java, intent).setup()
        shadowOf(Looper.getMainLooper()).idle()
        val binding = controller.get().binding
        assertEquals("The safe mode must offer the report", app.getString(R.string.startup_crash_lead), binding.lead.text.toString())
        assertTrue(binding.sendReport.isShown)
        assertTrue(binding.backupRestore.isShown)
        assertTrue(binding.tryAgain.isShown)
        return controller
    }

    @Test fun aThrowDuringStartupLeavesThePendingRecordAndTheNextStartShowsTheReportDialog() {
        val dir = tmp.newFolder("crash")
        assertFalse(startProcess(dir))
        val providerCrash = RuntimeException(
            "Unable to get provider androidx.startup.InitializationProvider: java.lang.RuntimeException: " +
                "Failed to create an instance of androidx.work.impl.WorkDatabase",
        )
        assertEquals("The crash goes on to the system's handler", listOf<Throwable>(providerCrash), crash(providerCrash))
        val pending = File(dir, "pending.txt").readText()
        assertTrue(pending, pending.contains("Failed to create an instance of androidx.work.impl.WorkDatabase"))
        assertTrue(pending, pending.contains("startup phase '${StartupCrashGuard.PHASE_APPLICATION}'"))
        assertTrue(pending, pending.contains("R8 map id: "))

        // The provider that crashed the start is off for the next start, or no activity could ever show.
        val provider = ComponentName(app.packageName, "androidx.startup.InitializationProvider")
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, app.packageManager.getComponentEnabledSetting(provider))

        // The next start: the launcher's StartupActivity hands over to the safe mode before anything else.
        assertTrue(startProcess(dir))
        assertFalse("The recorded crash is not counted again as an unfinished start",
            File(dir, "pending.txt").readText().contains("Unfinished start"))
        val startup = Robolectric.buildActivity(net.bible.android.activity.StartupActivity::class.java).create()
        try {
            val next = shadowOf(startup.get()).nextStartedActivity
            assertEquals(CrashSafeModeActivity::class.java.name, next.component?.className)
            assertTrue(startup.get().isFinishing)
            val safeMode = showSafeMode(next)
            safeMode.close()
        } finally {
            startup.close()
        }
    }

    @Test fun aRestoredReadingHostHandsOverToTheSafeModeBeforeInitialisingTheApp() {
        val dir = tmp.newFolder("crash")
        startProcess(dir)
        // initializeApp threw in the last run (a restored task goes straight to the reading host).
        val failure = runCatching {
            StartupCrashGuard.phase(StartupCrashGuard.PHASE_INITIALIZE_APP) { throw IllegalStateException("initializeApp failed") }
        }.exceptionOrNull()!!
        // The phase's breadcrumb is gone by the time the exception reaches the handler; it is still a startup crash.
        crash(failure)
        assertTrue(startProcess(dir))

        CurrentActivityHolder.forgetActivitiesForTest()
        val host = Robolectric.buildActivity(NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(app, NavRoutes.READING)).create(Bundle())
        try {
            val activity = host.get()
            assertTrue(activity.isFinishing)
            assertEquals(CrashSafeModeActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
            assertNull("Nothing of the host's start may run, not even the base activity's", CurrentActivityHolder.currentActivity)
        } finally {
            host.close()
        }
    }

    @Test fun aLeftoverBreadcrumbOffersTheReport() {
        val dir = tmp.newFolder("crash")
        startProcess(dir)
        // The database open died without an exception (a native crash, an out-of-memory kill).
        StartupCrashGuard.markPhase(StartupCrashGuard.PHASE_DATABASES)
        StartupCrashGuard.resetForTest()

        assertTrue(startProcess(dir))
        val pending = File(dir, "pending.txt").readText()
        assertTrue(pending, pending.contains("did not finish startup phase '${StartupCrashGuard.PHASE_DATABASES}'"))
        val safeMode = showSafeMode(CrashSafeModeActivity.intent(app))
        safeMode.close()
    }

    @Test fun theRecordsStayUntilTheUserActsInTheSafeMode() {
        val dir = tmp.newFolder("crash")
        startProcess(dir)
        crash(RuntimeException("Unable to get provider androidx.startup.InitializationProvider: boom"))
        assertTrue(startProcess(dir))
        val provider = ComponentName(app.packageName, "androidx.startup.InitializationProvider")

        var exited = false
        CrashSafeModeActivity.exitProcess = { exited = true }
        val safeMode = showSafeMode(CrashSafeModeActivity.intent(app))
        // Shown, but not acted on: a kill now must offer the report again.
        assertTrue(StartupCrashStore(dir).needsReport())
        StartupCrashGuard.resetForTest()
        assertTrue("Still offered on the start after", startProcess(dir))

        safeMode.get().binding.tryAgain.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(StartupCrashStore(dir).needsReport())
        assertFalse(File(dir, "pending.txt").exists())
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, app.packageManager.getComponentEnabledSetting(provider))
        val restart = shadowOf(safeMode.get()).nextStartedActivity
        assertEquals(net.bible.android.activity.StartupActivity::class.java.name, restart.component?.className)
        assertTrue(exited)
        safeMode.close()
    }

    @Test fun sendReportEmailsTheTraceWithTheVersionCodeAndMapId() {
        val dir = tmp.newFolder("crash")
        startProcess(dir)
        crash(IllegalStateException("Failed to create an instance of androidx.work.impl.WorkDatabase"))
        assertTrue(startProcess(dir))
        val safeMode = showSafeMode(CrashSafeModeActivity.intent(app))
        try {
            safeMode.get().binding.sendReport.performClick()
            val chooser = shadowOf(safeMode.get()).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION")
            val email = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            val subject = email.getStringExtra(Intent.EXTRA_SUBJECT)!!
            assertTrue(subject, subject.contains("(${net.bible.android.activity.BuildConfig.VERSION_CODE})"))
            val text = email.getStringExtra(Intent.EXTRA_TEXT)!!
            assertTrue(text, text.contains("R8 map id: "))
            assertTrue(text, text.contains("Failed to create an instance of androidx.work.impl.WorkDatabase"))
            assertEquals(Intent.ACTION_SEND_MULTIPLE, email.action)
            @Suppress("DEPRECATION")
            assertEquals(1, email.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!.size)
            // Sending is acting on it: the next start is a normal one.
            assertFalse(StartupCrashStore(dir).needsReport())
            assertFalse(safeMode.get().isFinishing)
        } finally {
            safeMode.close()
        }
    }

    @Test fun aStartTheUserClosedIsNotACrash() {
        val dir = tmp.newFolder("crash")
        startProcess(dir)
        StartupCrashGuard.markPhase(StartupCrashGuard.PHASE_DATABASES)
        StartupCrashGuard.resetForTest()
        // Swiped away during a long database migration.
        val am = app.getSystemService(android.app.ActivityManager::class.java)
        shadowOf(am).addApplicationExitInfo(
            org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setProcessName(app.packageName)
                .setReason(android.app.ApplicationExitInfo.REASON_USER_REQUESTED)
                .build(),
        )
        assertFalse(startProcess(dir))
        assertFalse(StartupCrashStore(dir).needsReport())
    }

    @Test fun leavingTheSafeModeAfterSendingEndsItsProcess() {
        val dir = tmp.newFolder("crash")
        startProcess(dir)
        crash(RuntimeException("boom"))
        assertTrue(startProcess(dir))
        var exited = false
        CrashSafeModeActivity.exitProcess = { exited = true }
        val safeMode = showSafeMode(CrashSafeModeActivity.intent(app))
        safeMode.get().binding.sendReport.performClick()
        assertFalse(exited)
        safeMode.get().finish() // Back
        safeMode.pause().stop().destroy()
        assertTrue("The uninitialised process must not serve the next start", exited)
    }

    @Test fun withTheCalculatorOnThereIsNoSafeModeAndNoProviderIsSwitchedOff() {
        CommonUtils.realSharedPreferences.edit(commit = true) { putBoolean("show_calculator", true) }
        try {
            val dir = tmp.newFolder("crash")
            assertFalse(startProcess(dir))
            crash(RuntimeException("Unable to get provider androidx.startup.InitializationProvider: boom"))
            val provider = ComponentName(app.packageName, "androidx.startup.InitializationProvider")
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, app.packageManager.getComponentEnabledSetting(provider))
            assertFalse("Nothing may show before the PIN", startProcess(dir))
            // checkCrash offers it after the calculator.
            assertTrue(StartupCrashGuard.needsReport())
        } finally {
            CommonUtils.realSharedPreferences.edit(commit = true) { remove("show_calculator") }
        }
    }

    @Test fun theClassicCrashCheckKeepsTheCounterUntilTheUserAnswers() {
        CommonUtils.realSharedPreferences.edit(commit = true) {
            putInt("app-crashed-count", 2)
            putLong("app-crashed-time", System.currentTimeMillis() - 1000)
        }
        val dialogs = KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java)
        val host = Robolectric.buildActivity(NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(app, NavRoutes.WELCOME)).setup()
        try {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            val job = GlobalScope.launch(Dispatchers.Main) { ErrorReportControl.checkCrash(host.get()) }
            shadowOf(Looper.getMainLooper()).idle()
            val pending = requireNotNull(dialogs.pending.value) { "checkCrash must offer the report" }
            assertEquals("Not reset while the question is open", 2, CommonUtils.realSharedPreferences.getInt("app-crashed-count", 0))
            dialogs.respond(pending.id, AppDialogResult.Cancel)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(job.isCompleted)
            assertEquals(0, CommonUtils.realSharedPreferences.getInt("app-crashed-count", -1))
        } finally {
            dialogs.cancelAll()
            host.close()
        }
    }
}
