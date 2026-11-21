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
    internal lateinit var binding: CrashSafeModeBinding
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reportText = savedInstanceState?.getString(STATE_REPORT) ?: StartupCrashGuard.reportText(this)
        binding = CrashSafeModeBinding.inflate(layoutInflater).apply {
            sendReport.setOnClickListener { sendReport() }
            backupRestore.setOnClickListener { restartApp(openBackup = true) }
            tryAgain.setOnClickListener { restartApp(openBackup = false) }
        }
        setContentView(binding.root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_REPORT, reportText)
    }

    /** Opens the email app; this screen stays, so that the user can then try again or open Backup & Restore. */
    private fun sendReport() {
        StartupCrashGuard.resolve(this)
        try {
            startActivity(reportIntent(this, reportText))
        } catch (e: Exception) {
            Log.e(TAG, "Could not send the crash report", e)
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_LONG).show()
        }
    }

    private fun restartApp(openBackup: Boolean) {
        StartupCrashGuard.resolve(this)
        startActivity(restartIntent(this, openBackup))
        finish()
        // This process skipped the app's initialisation: the next start needs a new one.
        exitProcess()
    }

    companion object {
        private const val TAG = "CrashSafeModeActivity"
        private const val STATE_REPORT = "report"
        private const val MAX_BODY_CHARS = 20_000
        private const val ERROR_REPORT_ADDRESS = "errors.andbible@gmail.com"

        /** `StartupActivity` opens Backup & Restore first when started with this extra. */
        const val EXTRA_OPEN_BACKUP = "net.bible.android.openBackupAfterCrash"

        /** Ends the safe-mode process; tests replace it. */
        @Volatile internal var exitProcess: () -> Unit = { Runtime.getRuntime().exit(0) }

        fun intent(context: Context): Intent =
            Intent(context, CrashSafeModeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

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

        /** The email: the report as the text and as an attachment (`filesDir/crash/report.txt`). */
        internal fun reportIntent(context: Context, reportText: String): Intent {
            val dir = File(context.filesDir, StartupCrashStore.DIR_NAME).apply { mkdirs() }
            val file = File(dir, "report.txt").apply { writeText(reportText) }
            val uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file)
            val firstError = reportText.lineSequence()
                .firstOrNull { it.startsWith("java.") || it.startsWith("kotlin.") || it.contains("Exception") || it.contains("Error:") }
                ?.take(120) ?: "startup"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(ERROR_REPORT_ADDRESS))
                putExtra(Intent.EXTRA_SUBJECT, "AndBible ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) startup crash: $firstError")
                // The attachment has it all; a huge extra would not fit the binder transaction.
                putExtra(Intent.EXTRA_TEXT, reportText.take(MAX_BODY_CHARS))
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            return Intent.createChooser(send, context.getString(R.string.send_bug_report_title))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
