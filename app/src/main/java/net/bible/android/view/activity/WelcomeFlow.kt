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

import net.bible.android.platform.AndroidBackupHost

import android.app.Activity
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedui.app.welcome.WelcomeHostPlatform

/**
 * The first-run welcome's Android orchestration (slice 8 §4), moved from `StartupComposeActivity`
 * (`StartupComposeActivity.kt:70-232` at spec HEAD) into the one host. Download / EasyStart / Redownload are
 * in-graph navigates and Import is [NavHostComposeActivity.openInstallZip]; the host's destination listener
 * sees each leave WELCOME beneath it and re-checks when the graph comes back to WELCOME
 * ([NavHostComposeActivity.recheckOnReturnToWelcome], which owns [awaitingReturn]). Restore returns through an external picker, so it
 * re-checks directly. The re-check itself is the host's gate (b), [NavHostComposeActivity.welcomeAfterFlow].
 */
internal class WelcomeFlow(private val host: NavHostComposeActivity) : WelcomeHostPlatform {

    // A restored WELCOME host may not have passed StartupActivity's database open.
    private val docsDao get() = DatabaseContainer.openForUse().repoDb.swordDocumentInfoDao()
    private val core by lazy {
        net.bible.sharedui.app.welcome.WelcomeFlowCore(
            versionText = { host.getString(R.string.version_text, CommonUtils.applicationVersionName) },
            supportedFormatsText = {
                val zip = host.getString(R.string.format_zip, host.getString(startupWelcomeShortAppNameRes()))
                host.getString(R.string.supported_formats,
                    "$zip, ${host.getString(R.string.format_mybible)}, ${host.getString(R.string.format_mysword)}, ${host.getString(R.string.format_epub)}")
            },
            language = { Locale.getDefault().language }, isDiscrete = { CommonUtils.isDiscrete },
            knownInstalled = { docsDao.getKnownInstalled() }, megabytesFree = { CommonUtils.megabytesFree },
            refuseDownload = { Dialogs.showErrorMsg(host.getString(R.string.storage_space_warning)) { host.finish() } },
            initialise = { host.initialiseLeavingWelcome() },
            ensureSyncLoaded = {
                org.koin.java.KoinJavaComponent.get<net.bible.service.cloudsync.documents.DocumentSyncSettings>(
                    net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java).ensureLoaded()
            },
            navigate = host::navigateInGraph,
            bookLabel = { host.getString(R.string.something_with_parenthesis, it.name, it.language) },
        )
    }
    suspend fun previousInstallDetected(): Boolean = core.previousInstallDetected()
    suspend fun refreshInfo() = core.refreshInfo()
    private val controllerLazy = lazy {
        core.controller.also {
            host.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { refreshInfo() }
        }
    }
    override val controller: StartupWelcomeController get() = controllerLazy.value
    val controllerIfCreated: StartupWelcomeController?
        get() = if (controllerLazy.isInitialized()) controllerLazy.value else null

    /** Derived by the host's back-stack listener, including after recreation. */
    var awaitingReturn: Boolean = false

    // StartupComposeActivity had no android:label, so its window title was the application label.
    override val windowTitle: String get() = host.applicationInfo.loadLabel(host.packageManager).toString()

    override val appName: String get() = host.getString(startupWelcomeAppNameRes())

    override val logo: @Composable () -> Painter? = { painterResource(startupWelcomeLogoRes()) }

    override fun onOpenHomepage() = openUrl(R.string.homepage)

    override fun onOpenGithub() = openUrl(R.string.github_page)

    @Composable
    override fun Dialogs() = core.RedownloadDialog(host.lifecycleScope)

    internal fun leaveFor(route: String) {
        host.lifecycleScope.launch { core.leaveFor(route) }
    }

    override fun onDownload() { host.lifecycleScope.launch { core.onDownload() } }
    override fun onEasyStart() { host.lifecycleScope.launch { core.onEasyStart() } }
    override fun onImport() {
        host.lifecycleScope.launch { core.initialiseThen { host.openInstallZip() } }
    }

    override fun onRestore() {
        host.lifecycleScope.launch {
            val result = host.awaitIntent(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/*" })
            CurrentActivityHolder.activate(host)
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@launch
                val backupHost = AndroidBackupHost { host }
                try {
                    if (org.koin.java.KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
                            .restoreAppDatabaseFromSourceWithUI(backupHost, AndroidBackupHost.fromUri(uri))) {
                        host.welcomeAfterFlow()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    backupHost.showErrorReport(e.message ?: host.getString(R.string.error_occurred), e)
                }
            }
        }
    }

    override fun onRedownload() {
        host.lifecycleScope.launch { core.onRedownload() }
    }

    private fun openUrl(@StringRes urlRes: Int) {
        try {
            host.startActivity(Intent(Intent.ACTION_VIEW, host.getString(urlRes).toUri()))
        } catch (e: Exception) { /* no browser -- ignore, parity with a dead link */ }
    }


}

/**
 * F62: the welcome header's app name under discrete mode, mirroring `bibleToolbarIconRes()`
 * (F58, `ComposeReadingViewHost.kt`) and `StartupActivity.onCreate`'s own spinner swap (:199-205).
 * A plain function rather than an inline `getString` argument so `StartupWelcomeDiscreteChromeTest`
 * can assert the CHOICE -- the rendered string is not usefully comparable in a unit test, and the
 * choice is the behaviour.
 */
@StringRes
internal fun startupWelcomeAppNameRes(): Int =
    if (CommonUtils.isDiscrete) R.string.app_name_calculator else R.string.app_name_long

/** F62: the welcome header's logo under discrete mode -- same rationale as [startupWelcomeAppNameRes]. */
@DrawableRes
internal fun startupWelcomeLogoRes(): Int =
    if (CommonUtils.isDiscrete) R.drawable.ic_calculator_color else R.drawable.ic_logo

/**
 * F62 fix round 1: the "Supported formats" zip line built by `loadInfo()` must be discrete-aware
 * too, not just the header -- it used to spell the real app name unconditionally.
 *
 * A SEPARATE helper from [startupWelcomeAppNameRes], not a reuse of it: the non-discrete branch
 * intentionally differs. `format_zip`'s `%s` is documented (`strings.xml`'s comment above it) to
 * take [R.string.app_name_andbible] ("AndBible"), the short form -- not [R.string.app_name_long]
 * ("AndBible: Bible Study"), which would read oddly inline in "Zip file containing ... created by
 * AndBible: Bible Study". Discrete mode has only one calculator-identity string
 * ([R.string.app_name_calculator]), so both helpers converge on it there.
 */
@StringRes
internal fun startupWelcomeShortAppNameRes(): Int =
    if (CommonUtils.isDiscrete) R.string.app_name_calculator else R.string.app_name_andbible
