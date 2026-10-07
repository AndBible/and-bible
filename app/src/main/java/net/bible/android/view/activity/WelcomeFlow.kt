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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.json
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedcore.startup.StartupWelcomeInfo
import net.bible.sharedui.components.AbMultiSelectDialog
import net.bible.sharedui.startup.nav.WelcomeNavDeps
import net.bible.sharedui.strings.LocalStrings

/**
 * The first-run welcome's Android orchestration (slice 8 §4), moved from `StartupComposeActivity`
 * (`StartupComposeActivity.kt:70-232` at spec HEAD) into the one host. Download / EasyStart / Redownload are
 * in-graph navigates and Import is [NavHostComposeActivity.openInstallZip]; the host's destination listener
 * sees each leave WELCOME beneath it and re-checks when the graph comes back to WELCOME
 * ([NavHostComposeActivity.recheckOnReturnToWelcome], which owns [awaitingReturn]). Restore returns through an external picker, so it
 * re-checks directly. The re-check itself is the host's gate (b), [NavHostComposeActivity.welcomeAfterFlow].
 */
internal class WelcomeFlow(private val host: NavHostComposeActivity) {

    // A host restored on WELCOME after process death never ran StartupActivity's DB open (fix batch 1 §2.6).
    private val docsDao get() = DatabaseContainer.openForUse().repoDb.swordDocumentInfoDao()
    private val previousInstallDetected: Boolean get() = docsDao.getKnownInstalled().isNotEmpty()

    private val controllerLazy = lazy { StartupWelcomeController(::loadInfo) }

    val controller: StartupWelcomeController get() = controllerLazy.value

    /**
     * The controller only if a WELCOME entry has built it -- for the host's `InstallZipEvent` handler, which runs on
     * every host and must not build one (building runs `loadInfo`, a DB read) just to show a progress line nobody sees.
     */
    val controllerIfCreated: StartupWelcomeController? get() = if (controllerLazy.isInitialized()) controllerLazy.value else null

    /**
     * True from the moment a flow leaves WELCOME for another destination until the graph returns. Set only by
     * the host's destination listener, from the back stack (so it survives a recreate without being saved).
     */
    var awaitingReturn: Boolean = false

    private var redownloadBooks by mutableStateOf<List<SwordDocumentInfo>?>(null)

    private fun loadInfo(): StartupWelcomeInfo {
        // F62 fix round 1: both app-name substitutions are discrete-aware, like the header.
        val zip = host.getString(R.string.format_zip, host.getString(startupWelcomeShortAppNameRes()))
        val formats = host.getString(
            R.string.supported_formats,
            "$zip, ${host.getString(R.string.format_mybible)}, ${host.getString(R.string.format_mysword)}, ${host.getString(R.string.format_epub)}",
        )
        return StartupWelcomeInfo(
            versionText = host.getString(R.string.version_text, CommonUtils.applicationVersionName),
            supportedFormatsText = formats,
            previousInstallDetected = previousInstallDetected,
            easyStartAvailable = Locale.getDefault().language == "en",
            // Batch 6 A6: the Homepage / GitHub buttons open AndBible URLs.
            homepageButtonsVisible = !CommonUtils.isDiscrete,
        )
    }

    /** The destination's deps. Built once per host (`remember` in `setContent`). */
    fun navDeps(): WelcomeNavDeps = WelcomeNavDeps(
        setWindowTitle = { title -> host.setTitle(title) },
        // StartupComposeActivity had no android:label, so its window title was the application label.
        windowTitle = host.applicationInfo.loadLabel(host.packageManager).toString(),
        controller = { controller },
        appName = host.getString(startupWelcomeAppNameRes()),
        logo = { painterResource(startupWelcomeLogoRes()) },
        onDownload = ::onDownload,
        onImport = ::onImport,
        onRestore = ::onRestore,
        onRedownload = ::onRedownload,
        onEasyStart = ::onEasyStart,
        onOpenHomepage = { openUrl(R.string.homepage) },
        onOpenGithub = { openUrl(R.string.github_page) },
        dialogs = { RedownloadDialog() },
    )

    /**
     * Leave WELCOME for [route]. The app is initialised first: these screens used to be separate Activities
     * that initialised it in `ActivityBase.onCreate`, and this host started uninitialised (spec §3.1 rule 2).
     * Internal for `UsableBibleGateTest`, which leaves onto a light stand-in route (Download needs a network).
     */
    internal fun leaveFor(route: String) {
        host.initialiseLeavingWelcome()
        host.navigateInGraph(route)
    }

    private fun onDownload() {
        if (CommonUtils.megabytesFree < SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
            Dialogs.showErrorMsg(host.getString(R.string.storage_space_warning)) { host.finish() }
            return
        }
        leaveFor(NavRoutes.download(firstDownload = true))
    }

    private fun onEasyStart() = leaveFor(NavRoutes.download(firstDownload = true, downloadRecommended = true))

    private fun onImport() {
        host.initialiseLeavingWelcome()
        host.openInstallZip()
    }

    private fun onRestore() {
        host.lifecycleScope.launch {
            val result = host.awaitIntent(Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/*" })
            CurrentActivityHolder.activate(host)
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@launch
                if (BackupControl.restoreAppDatabaseFromUriWithUI(host, uri)) host.welcomeAfterFlow()
            }
        }
    }

    private fun onRedownload() {
        redownloadBooks = docsDao.getKnownInstalled().sortedBy { it.language }
    }

    private fun launchRedownload(books: List<SwordDocumentInfo>) =
        leaveFor(NavRoutes.download(firstDownload = true, documentIds = json.encodeToString(serializer(), books)))

    private fun openUrl(@StringRes urlRes: Int) {
        try {
            host.startActivity(Intent(Intent.ACTION_VIEW, host.getString(urlRes).toUri()))
        } catch (e: Exception) { /* no browser -- ignore, parity with a dead link */ }
    }

    @Composable
    private fun RedownloadDialog() {
        val books = redownloadBooks ?: return
        val strings = LocalStrings.current
        AbMultiSelectDialog(
            title = host.getString(R.string.redownload),
            options = books,
            selectedIds = books.map { it.initials },
            idOf = { it.initials },
            labelOf = { host.getString(R.string.something_with_parenthesis, it.name, it.language) },
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
 * F62 fix round 1: the welcome CARD's two other app-name substitutions (`loadInfo()`'s
 * `welcomeText` and the "Supported formats" zip line) must be discrete-aware too, not just the
 * header -- review caught that `loadInfo()` still spelled the real name unconditionally, so
 * discrete mode's card read "Thank you for downloading AndBible..." even though the header above
 * it correctly showed the calculator identity.
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
