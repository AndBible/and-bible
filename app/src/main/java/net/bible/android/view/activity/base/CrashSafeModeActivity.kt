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
package net.bible.android.view.activity.base

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.R
import net.bible.android.activity.databinding.CrashSafeModeBinding
import net.bible.android.control.report.StartupCrashGuard
import net.bible.android.control.report.LAST_CRASH_LOGCAT_FILE
import net.bible.android.control.report.LAST_CRASH_SCREENSHOT_FILE
import net.bible.android.control.report.StartupCrashStore
import java.io.File

/**
 * #4176: the crash safe mode's screen. The app crashed while starting ([StartupCrashGuard]), so nothing of it was
 * initialised: this is a plain [Activity] with platform views (no Koin, no Compose, no databases, no app theme), and
 * it follows the system's dark or light mode by itself.
 *
 * - "Send Report": an email with the build (version code, R8 map id), the device and the recorded stack traces.
 * - "Backup & Restore": starts the app again straight into Backup & Restore, before the databases are opened.
 * - "Try again": starts the app again normally.
 *
 * The crash records are forgotten only when the user picks one of them ([StartupCrashGuard.resolve]); leaving the
 * screen any other way keeps them, so the next start offers the report again.
 */
class CrashSafeModeActivity : Activity() {
    private var reportText: String = ""
    /** When the crashes in [reportText] began: the logcat and screenshot are attached only when as recent. */
    private var crashesSince: Long = 0L
    private var restarting = false
    internal lateinit var binding: CrashSafeModeBinding
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        liveInstances++
        reportText = savedInstanceState?.getString(STATE_REPORT) ?: StartupCrashGuard.reportText(this)
        crashesSince = savedInstanceState?.getLong(STATE_SINCE) ?: StartupCrashGuard.firstCrashTime() ?: 0L
        binding = CrashSafeModeBinding.inflate(layoutInflater).apply {
            sendReport.setOnClickListener { sendReport() }
            backupRestore.setOnClickListener { restartApp(openBackup = true) }
            tryAgain.setOnClickListener { restartApp(openBackup = false) }
        }
        setContentView(binding.root)
    }

    override fun onDestroy() {
        super.onDestroy()
        liveInstances--
        // Left with Back after "Send Report": this process skipped the app's initialisation, so it must not be
        // reused by the next start (which would land here again, without any records). Not while another safe-mode
        // screen of this process still shows (a second entry, from a widget or a notification).
        if (isFinishing && !restarting && !isChangingConfigurations && liveInstances == 0) exitProcess()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_REPORT, reportText)
        outState.putLong(STATE_SINCE, crashesSince)
    }

    /** Opens the email app; this screen stays, so that the user can then try again or open Backup & Restore. */
    private fun sendReport() {
        StartupCrashGuard.resolve(this)
        try {
            startActivity(reportIntent(this, reportText, crashesSince))
        } catch (e: Exception) {
            Log.e(TAG, "Could not send the crash report", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_LONG).show()
        }
    }

    private fun restartApp(openBackup: Boolean) {
        StartupCrashGuard.resolve(this)
        restarting = true
        startActivity(restartIntent(this, openBackup))
        finish()
        // This process skipped the app's initialisation: the next start needs a new one.
        exitProcess()
    }

    companion object {
        private const val TAG = "CrashSafeModeActivity"
        private const val STATE_REPORT = "report"
        private const val STATE_SINCE = "since"
        /** The app's own crash handler saves the logcat and screenshot just before the crash is counted. */
        private const val CRASH_FILES_MARGIN_MS = 5L * 60L * 1000L

        /** The safe-mode screens alive in this process (main thread only). */
        internal var liveInstances = 0
            private set

        internal fun resetForTest() {
            liveInstances = 0
        }
        private const val MAX_BODY_CHARS = 20_000
        private const val ERROR_REPORT_ADDRESS = "errors.andbible@gmail.com"

        /** `StartupActivity` opens Backup & Restore first when started with this extra. */
        const val EXTRA_OPEN_BACKUP = "net.bible.android.openBackupAfterCrash"

        /** Ends the safe-mode process; tests replace it. */
        @Volatile internal var exitProcess: () -> Unit = { Runtime.getRuntime().exit(0) }

        /**
         * The first entry clears the task (a restored task's screens would each need the app); a later one (a widget or
         * notification tap after Home) brings the live screen back (`singleTask`): clearing it would end the process.
         */
        fun intent(context: Context): Intent =
            Intent(context, CrashSafeModeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or (if (liveInstances == 0) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
            )

        /**
         * Hands the start over to the safe mode when this process started in it: starts [CrashSafeModeActivity] and
         * finishes [activity]. Call before anything that needs the app to be initialised.
         */
        fun routeIfSafeMode(activity: Activity): Boolean {
            if (!StartupCrashGuard.safeMode) return false
            activity.startActivity(intent(activity))
            activity.finish()
            return true
        }

        internal fun restartIntent(context: Context, openBackup: Boolean): Intent =
            Intent.makeRestartActivityTask(ComponentName(context, net.bible.android.activity.StartupActivity::class.java)).apply {
                if (openBackup) putExtra(EXTRA_OPEN_BACKUP, true)
            }

        /**
         * The email: the report as the text and as an attachment (`filesDir/crash/report.txt`), with the logcat and
         * screenshot the app's own crash handler saved at its last crash (`filesDir/log`), when they belong to these
         * crashes (saved since [crashesSince], the first recorded one; 0: none recorded, so none attached).
         */
        internal fun reportIntent(context: Context, reportText: String, crashesSince: Long): Intent {
            val dir = File(context.filesDir, StartupCrashStore.DIR_NAME).apply { mkdirs() }
            val file = File(dir, "report.txt").apply { writeText(reportText) }
            val logDir = File(context.filesDir, "log")
            val files = listOf(file) + listOf(LAST_CRASH_LOGCAT_FILE, LAST_CRASH_SCREENSHOT_FILE)
                .map { File(logDir, it) }
                .filter { crashesSince > 0L && it.canRead() && it.lastModified() >= crashesSince - CRASH_FILES_MARGIN_MS }
            val uris = ArrayList(files.map { FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", it) })
            val firstError = reportText.lineSequence()
                .firstOrNull { it.startsWith("java.") || it.startsWith("kotlin.") || it.contains("Exception") || it.contains("Error:") }
                ?.take(120) ?: "startup"
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(ERROR_REPORT_ADDRESS))
                putExtra(Intent.EXTRA_SUBJECT, "AndBible ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) startup crash: $firstError")
                // The attachment has it all; a huge extra would not fit the binder transaction.
                putExtra(Intent.EXTRA_TEXT, reportText.take(MAX_BODY_CHARS))
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(send, context.getString(R.string.send_bug_report_title))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
