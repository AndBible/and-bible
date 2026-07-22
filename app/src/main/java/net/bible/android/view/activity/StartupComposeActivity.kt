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
package net.bible.android.view.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.DownloadComposeActivity
import net.bible.android.view.activity.installzip.InstallZipEvent
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.startup.StartupWelcomeScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.AbTheme
import java.util.Locale

/**
 * Compose host for the first-run welcome screen — the new-path twin of classic
 * [StartupActivity.showFirstLayout]. Launched (behind `use_compose_ui`) by classic StartupActivity
 * only in the "no bibles" branch, AFTER database init (so the toggle is readable). Owns all Android
 * orchestration; signals success via `RESULT_OK` when bibles exist, and never launches
 * MainBibleActivity itself (classic StartupActivity does that).
 */
class StartupComposeActivity : ActivityBase() {
    override val doNotInitializeApp = true

    private val docsDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val previousInstallDetected: Boolean get() = docsDao.getKnownInstalled().isNotEmpty()

    private val controller by lazy { StartupWelcomeController(::loadInfo) }

    private var redownloadBooks by mutableStateOf<List<SwordDocumentInfo>?>(null)

    private fun loadInfo(): StartupWelcomeInfo {
        val zip = getString(R.string.format_zip, getString(R.string.app_name_andbible))
        val formats = getString(
            R.string.supported_formats,
            "$zip, ${getString(R.string.format_mybible)}, ${getString(R.string.format_mysword)}, ${getString(R.string.format_epub)}",
        )
        return StartupWelcomeInfo(
            welcomeText = getString(R.string.welcome_message, getString(R.string.app_name_long)),
            versionText = getString(R.string.version_text, CommonUtils.applicationVersionName),
            supportedFormatsText = formats,
            redownloadMessage = getString(R.string.redownload_message),
            easyStartMessage = getString(R.string.easy_start_message),
            previousInstallDetected = previousInstallDetected,
            easyStartAvailable = Locale.getDefault().language == "en",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        ABEventBus.register(this) {
            onMain<InstallZipEvent> { e -> controller.setProgress(e.message) }
        }
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val state by controller.state.collectAsState()
                    StartupWelcomeScreen(
                        state = state,
                        onDownload = ::onDownload,
                        onImport = ::onImport,
                        onRestore = ::onRestore,
                        onRedownload = ::onRedownload,
                        onEasyStart = ::onEasyStart,
                        onOpenHomepage = { openUrl(R.string.homepage) },
                        onOpenGithub = { openUrl(R.string.github_page) },
                    )
                    val books = redownloadBooks
                    if (books != null) {
                        val strings = LocalStrings.current
                        AbMultiSelectDialog(
                            title = getString(R.string.redownload),
                            options = books,
                            selectedIds = books.map { it.initials },
                            idOf = { it.initials },
                            labelOf = { getString(R.string.something_with_parenthesis, it.name, it.language) },
                            confirmText = strings.okay,
                            dismissText = strings.no,
                            onConfirm = { selectedIds ->
                                redownloadBooks = null
                                val selected = books.filter { selectedIds.contains(it.initials) }
                                if (selected.isNotEmpty()) launchRedownload(selected)
                            },
                            onDismiss = { redownloadBooks = null },
                            selectAllText = strings.selectAll,
                            selectNoneText = strings.selectNone,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ABEventBus.unregister(this)
        super.onDestroy()
    }

    /** After any flow: bibles exist → RESULT_OK back to classic StartupActivity; else stay + refresh. */
    private fun afterFlow() {
        if (SwordDocumentFacade.bibles.isNotEmpty()) {
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            controller.setProgress(null)
            controller.refresh()
        }
    }

    private fun firstDownloadIntent(): Intent =
        ScreenLauncher.intentFor(this, Screen.FirstDownload)
            .apply { putExtra(DownloadComposeActivity.EXTRA_FIRST_DOWNLOAD, true) }

    private fun onDownload() {
        if (CommonUtils.megabytesFree < SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
            Dialogs.showErrorMsg(getString(R.string.storage_space_warning)) { finish() }
            return
        }
        lifecycleScope.launch {
            awaitIntent(firstDownloadIntent())
            afterFlow()
        }
    }

    private fun onEasyStart() {
        val intent = firstDownloadIntent().apply { putExtra("download-recommended", true) }
        lifecycleScope.launch {
            awaitIntent(intent)
            afterFlow()
        }
    }

    private fun onImport() {
        val intent = ScreenLauncher.intentFor(this, Screen.InstallZip).apply { putExtra("doNotInitializeApp", true) }
        lifecycleScope.launch {
            awaitIntent(intent)
            afterFlow()
        }
    }

    private fun onRestore() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/*" }
        lifecycleScope.launch {
            val result = awaitIntent(intent)
            CurrentActivityHolder.activate(this@StartupComposeActivity)
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@launch
                if (BackupControl.restoreAppDatabaseFromUriWithUI(this@StartupComposeActivity, uri)) {
                    afterFlow()
                }
            }
        }
    }

    private fun onRedownload() {
        redownloadBooks = docsDao.getKnownInstalled().sortedBy { it.language }
    }

    private fun launchRedownload(books: List<SwordDocumentInfo>) {
        val intent = firstDownloadIntent()
            .apply { putExtra(DownloadActivity.DOCUMENT_IDS_EXTRA, json.encodeToString(serializer(), books)) }
        lifecycleScope.launch {
            awaitIntent(intent)
            afterFlow()
        }
    }

    private fun openUrl(@StringRes urlRes: Int) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, getString(urlRes).toUri()))
        } catch (e: Exception) { /* no browser — ignore, parity with a dead link */ }
    }
}
