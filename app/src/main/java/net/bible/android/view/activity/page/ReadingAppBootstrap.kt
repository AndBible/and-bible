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

package net.bible.android.view.activity.page

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.AlignmentSpan
import android.text.style.ImageSpan
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.service.common.BuildVariant
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.service.common.betaIntroVideo
import net.bible.service.common.htmlToSpan
import net.bible.service.common.newFeaturesIntroVideo
import net.bible.service.common.windowPinningVideo
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.service.sword.SwordDocumentFacade
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.system.exitProcess

const val DEFAULT_SYNC_INTERVAL = 5*60L // 5 minutes

// Bump this when introducing additional sync targets to re-trigger the
// "new sync targets available" notice for users who already dismissed it.
private const val NEW_SYNC_TARGETS_ANNOUNCE_VERSION = 1

/**
 * The scope the cloud-sync calls are launched on. Process-wide and file-private-by-convention
 * (`internal`, same package as its two callers): moved here VERBATIM from `MainBibleActivity.kt`,
 * where it was a top-level `private val` in the same package, so every existing call site
 * (`syncScope.launch { ... }` in `MainBibleActivity`'s `AppToBackgroundEvent` handler) resolves to
 * the same object it always did.
 */
internal val syncScope = CoroutineScope(Dispatchers.IO)

/**
 * The request code `requestSdcardPermission` sends and `MainBibleActivity.onRequestPermissionsResult`
 * reads. It lives at package level rather than in either class because the request half moved here
 * and the result half did not -- see [ReadingAppBootstrap.requestSdcardPermission].
 */
internal const val SDCARD_READ_REQUEST = 2

private const val REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref"

/**
 * The app bootstrap, lifted out of `MainBibleActivity.onCreate` -- reading-host re-typing Task R7
 * (design spec §3.4, `2026-09-16-compose-reading-host-retyping`).
 *
 * §2.3 measured eight responsibilities that `MainBibleActivity.onCreate` performs on behalf of the
 * whole app rather than on behalf of the reading view: `CommonUtils.prepareData()`, the
 * `WindowRepository`'s creation, the first-run notices, the `openLink` deep-link branch, the network
 * callback, the soft-keyboard mode, the sdcard permission request and the cloud-sync loop.
 * `NavHostComposeActivity` had NONE of them. They are extracted ONCE here and called by both hosts,
 * rather than copied into the second one: two copies of the `WindowRepository` creation is exactly
 * the silent duplication §2.3 warns about, because `WindowControl`'s lazy fallback hands out an
 * uninitialised repository instead of crashing. One shared unit cannot duplicate, and when
 * `MainBibleActivity` is deleted its caller simply disappears.
 *
 * **The host type is [ActivityBase], deliberately NOT `ReadingHostActivity`.** An app entry point is
 * not the same thing as a reading host, and nothing here is reading chrome. [ActivityBase] is the
 * nearest common supertype of the two hosts and is what the measured code actually needs: `window`
 * ([setSoftKeyboardMode]), `checkSelfPermission`/`requestPermissions` ([requestSdcardPermission]),
 * a `Context` for `AlertDialog.Builder` (all four notices), `lifecycleScope` (the repository's
 * scope), and -- the binding constraint -- `ErrorReportControl.checkCrash`,
 * `CommonUtils.checkPoorTranslations` and `CloudSync.signIn` all take an `ActivityBase` parameter,
 * so a `ComponentActivity` would not compile without exactly the downcast to the concrete Activity
 * that this batch forbids. No cast is needed anywhere: both hosts ARE `ActivityBase`s.
 *
 * **Order is load-bearing and is preserved by the CALLERS, not by this class.** Nothing here calls
 * anything else here; each member is a step the host's `onCreate` invokes in classic's original
 * sequence. In particular `ReadingHostPresence.setForeground(this)` +
 * `ReadingViewVisibility.setActivityVisible(this, true)` must still run before
 * [prepareData] and long before [openDeepLink] -- see the 14-line comment that survives at the top
 * of `MainBibleActivity.onCreate` for why (`openDeepLink` -> `windowControl.showLink` ->
 * `setKey(addHistoryItem = true)` posts `AddHistoryItem` synchronously, and with the flag false that
 * records a WRONG `IntentHistoryItem` carrying the deep-link intent).
 *
 * **The network callback is balanced (T8a item 4; R7 recorded it as a known leak and deferred the
 * fix).** [networkCallback] is registered by [registerNetworkCallback] and removed by
 * [unregisterNetworkCallback], which BOTH hosts call from their own `onDestroy`. Before T8a there
 * was no `unregisterNetworkCallback` call anywhere in the repository and had never been one; R7 was
 * right to preserve that inside an extraction commit and wrong to leave it after T8b, whose boot
 * handoff keeps `FLAG_ACTIVITY_MULTIPLE_TASK` for `ACTION_VIEW` deep links -- so every deep link
 * would spawn another host, another bootstrap and another registration nothing removed.
 *
 * Unregistering is safe with two live hosts BECAUSE the callback is per-bootstrap and the bootstrap
 * is per-host: [networkCallback] is an instance property of this class, each host constructs its own
 * `ReadingAppBootstrap(this)`, and `ConnectivityManager.unregisterNetworkCallback(cb)` removes the
 * one object it is handed. The `ConnectivityManager` itself is the process-wide system service --
 * which is exactly why it is the callback's IDENTITY, not the manager's, that keeps two hosts apart.
 * `ReadingAppBootstrapTest.destroyingOneHostLeavesTheOtherLiveHostsCallbackRegistered` measures it.
 */
class ReadingAppBootstrap(private val host: ActivityBase) : KoinComponent {
    private val windowControl: WindowControl by inject()
    private val preferences get() = CommonUtils.settings
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val TAG get() = host.TAG

    /**
     * The repository this bootstrap created, and the one its host publishes as its own
     * `windowRepository`. It is a settable `var` (not a private-set one) because
     * `MainBibleActivity.windowRepository` is a settable `var` today and seven of the batch's
     * untouchable Robolectric classes assign it directly after `controller.create()`; the Activity's
     * property is a view onto THIS field, so those assignments keep reaching the same one object
     * [synchronize] and [lastTouched] read.
     */
    lateinit var windowRepository: WindowRepository

    /**
     * Whether the host is paused -- [networkCallback]'s `onAvailable` gate, classic's
     * `MainBibleActivity.paused`. The host writes it from `onPause`/`onResume`; it is NOT derived
     * from `host.lifecycle` because the two are not the same instant (classic sets the flag BEFORE
     * `super.onPause()`/`super.onResume()`, and it is `false` from construction, while the lifecycle
     * registry is not `RESUMED` until after `super.onResume()`) and a network callback that arrives
     * inside that window would behave differently.
     */
    var hostPaused = false

    // ——— §2.3 row 2: data preparation ————————————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.onCreate:514`. Genuinely idempotent -- `CommonUtils.prepareData`
     * is guarded by a persisted `"data-version"` setting -- and this is its only call site in the
     * repository, so calling it from two Activities is safe.
     */
    fun prepareData() {
        CommonUtils.prepareData()
    }

    // ——— §2.3 row 1: the WindowRepository ————————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.onCreate:530-532`, verbatim including the ORDER of the three
     * statements: construct, publish to [WindowControl], then `initialize()`. `initialize()` ->
     * `loadFromDb` posts events synchronously, and anything that reads `windowControl.windowRepository`
     * while they unwind must already see this instance.
     *
     * `WindowRepository.initialize()` is idempotent per INSTANCE only (`if(initialized ||
     * loadingFromDb) return`, where `initialized` reads that instance's own `_activeWindow`); there
     * is no cross-instance guard at all, and the repository binds to the creating host's
     * `lifecycleScope`. So this creates ONE repository per host -- exactly today's behaviour, where
     * two live `MainBibleActivity` instances already create two. This batch does not change that.
     *
     * **Idempotent per host (R7 fix round 1, review Important 3).** A second call returns the
     * repository this host already has instead of replacing it. Nothing calls it twice today -- each
     * host calls it exactly once, which `ReadingAppBootstrapTest` pins by counting call sites -- but
     * a site count cannot see one site EXECUTED twice, and that is the one shape of this task's
     * silent defect it would miss: the replacement would not crash, it would strand every
     * collaborator holding the first repository on a workspace `WindowControl` no longer publishes.
     */
    fun createWindowRepository(): WindowRepository {
        if (this::windowRepository.isInitialized) return windowRepository
        windowRepository = WindowRepository(host.lifecycleScope)
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()
        return windowRepository
    }

    // ——— §2.3 row 3: the first-run notices ———————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.onCreate:566-581` -- the whole once-per-process gated block, minus
     * its `lifecycleScope.launch(Dispatchers.Main)` wrapper, which stays at the call site so the
     * host keeps deciding when in its own `onCreate` this runs.
     */
    suspend fun showFirstRunNotices() {
        if(!initialized) {
            requestSdcardPermission()
            ErrorReportControl.checkCrash(host)
            if(!CommonUtils.checkPoorTranslations(host)) exitProcess(2)
            showBetaNotice()
            showStableNotice()
            showNewSyncTargetsNotice()
            showFirstTimeHelp()
            if(!CommonUtils.isDiscrete) {
                ABEventBus.post(ToastEvent(windowRepository.name))
            }
            checkDocBackupDBInSync()
        }
        initialized = true
    }

    /**
     * Checks if the list of documents installed matches the list of
     * books in the backup database.
     *
     * Backup database is used to allow user to quickly reinstall all
     * available books if moving to a new device.
     */
    private fun checkDocBackupDBInSync() {
        val docs = SwordDocumentFacade.documents
        val knownInstalled = docDao.getKnownInstalled()
        if (knownInstalled.isEmpty()) {
            Log.i(TAG, "There is at least one Bible, but Bible Backup DB is empty, populate with first time books");
            val allDocs = docs.map {
                SwordDocumentInfo(it.initials, it.name, it.abbreviation, it.language.name, it.getProperty(DownloadManager.REPOSITORY_KEY) ?: "")
            }
            docDao.insert(allDocs)
        } else {
            knownInstalled.forEach {
                Log.i(TAG, "The ${it.name} is installed")
            }
        }
    }

    private suspend fun showFirstTimeHelp()  {
        val pinningHelpShown = preferences.getBoolean("pinning-help-shown", false)
        if(!pinningHelpShown) {
            val save = CommonUtils.isFirstInstall || CommonUtils.mainVersionFloat >= 3.4 || suspendCoroutine<Boolean> {
                val pinningTitle = host.getString(R.string.help_window_pinning_title)
                var pinningText = host.getString(R.string.help_window_pinning_text)

                pinningText += "<br><i><a href=\"$windowPinningVideo\">${host.getString(R.string.watch_tutorial_video)}</a></i><br>"

                val spanned = htmlToSpan(pinningText)

                val d = AlertDialog.Builder(host)
                    .setTitle(pinningTitle)
                    .setMessage(spanned)
                    .setNeutralButton(host.getString(R.string.first_time_help_show_next_time), null)
                    .setPositiveButton(host.getString(R.string.first_time_help_do_not_show_again)) { _, _ ->
                        it.resume(true)
                    }
                    .show()

                d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
            }
            if(save) {
                preferences.setBoolean("pinning-help-shown", true)
            }
        }
    }

    private fun showNewSyncTargetsNotice() {
        val displayedVer = preferences.getInt("new-sync-targets-notice-displayed", 0)
        if (displayedVer >= NEW_SYNC_TARGETS_ANNOUNCE_VERSION) return

        // The notice is only relevant to users who already use device sync. Suppress
        // it for everyone else so that users who enable sync later don't see a stale
        // announcement about targets that aren't new to them.
        if (!CommonUtils.isCloudSyncEnabled) {
            preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
            return
        }

        AlertDialog.Builder(host)
            .setTitle(R.string.new_sync_targets_notice_title)
            .setMessage(R.string.new_sync_targets_notice_message)
            .setCancelable(false)
            .setNegativeButton(R.string.dismiss) { _, _ ->
                preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
            }
            .setPositiveButton(R.string.open_settings) { _, _ ->
                preferences.setInt("new-sync-targets-notice-displayed", NEW_SYNC_TARGETS_ANNOUNCE_VERSION)
                ScreenLauncher.open(host, Screen.SyncSettings)
            }
            .show()
    }

    private suspend fun showStableNotice() = suspendCoroutine<Boolean> {
        if(CommonUtils.isBeta) {
            it.resume(false)
            return@suspendCoroutine
        }

        val ver = CommonUtils.mainVersion
        val displayedVer = preferences.getString("stable-notice-displayed", "")
        Log.i(TAG, "showStableNotice: $displayedVer $ver")

        if(displayedVer != ver) {
            val videoMessage = host.getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
            val appName = host.getString(R.string.app_name_long)
            val par1 = host.getString(R.string.stable_notice_par1, CommonUtils.mainVersion, appName)
            val buy = host.getString(R.string.buy_development)
            val support = host.getString(R.string.buy_development2)
            val heartIcon = ImageSpan(CommonUtils.getTintedDrawable(R.drawable.baseline_attach_money_24))
            val biggerLogoDrawable = CommonUtils.getResourceDrawable(R.drawable.ic_logo, host)!!
            biggerLogoDrawable.setBounds(0, 0, biggerLogoDrawable.intrinsicWidth*2, biggerLogoDrawable.intrinsicHeight*2)
            val logoSpan = ImageSpan(biggerLogoDrawable)
            val centerSpan = AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER)
            val imageStr = SpannableString("*")
            val iconStr = SpannableString("*")
            iconStr.setSpan(heartIcon, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            imageStr.setSpan(logoSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            imageStr.setSpan(centerSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

            val spanned = TextUtils.concat(
                htmlToSpan("$par1<br><br>"),
                if(BuildVariant.Appearance.isDiscrete) "" else imageStr,
                htmlToSpan("<br><br><big><a href=\"$newFeaturesIntroVideo\"><b>$videoMessage</b></a></big>"),
                htmlToSpan("<br><br>"),
                iconStr,
                htmlToSpan("&nbsp;<small><a href=\"$buyDevelopmentLink\">$support ($buy)</a></small>")
            )

            val d = AlertDialog.Builder(host)
                .setTitle(host.getString(R.string.stable_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(host.getString(R.string.dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(host.getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    Log.i(TAG, "showStableNotice: saving $ver")
                    preferences.setString("stable-notice-displayed", ver)
                    it.resume(true)
                }
                .setOnCancelListener {_ -> it.resume(false)}
                .create()
            d.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
        } else {
            it.resume(false)
        }
    }

    private suspend fun showBetaNotice() = suspendCoroutine<Boolean> {
        if(!CommonUtils.isBeta) {
            it.resume(false)
            return@suspendCoroutine
        }

        val announceVersion = 3
        val displayedVer = preferences.getInt("beta-notice-displayed2", 0)

        if(displayedVer < announceVersion) {
            val videoMessage = host.getString(R.string.upgrade_video_message, CommonUtils.mainVersion)
            val videoMessageLink = "<a href=\"${betaIntroVideo}\"><b>$videoMessage</b></a>"

            val par1 = host.getString(R.string.beta_notice_content_1)
            val par2 = host.getString(R.string.beta_notice_content_2,
                 " <a href=\"https://github.com/AndBible/and-bible/issues\">"
                    + "${host.getString(R.string.beta_notice_github_issues)}</a>"
            )
            val par3 = host.getString(R.string.beta_notice_content_3,
                " <a href=\"https://github.com/AndBible/and-bible\">"
                    + "${host.getString(R.string.beta_notice_github)}</a>"

            )
            val extraMessage = """
                |<b>DEVELOPER'S SPECIAL NOTICE FOR BETA TESTERS (April 2026)</b><br><br>
                |Welcome to the 5.1 beta! Many new features have landed, including:<br>
                |<br>
                |• <b>AI assistant</b> with tool calling and support for multiple
                | providers (Claude, Grok, OpenRouter, OpenAI-compatible and more).<br>
                |• <b>Reading &amp; memorization progress tracking</b> with multiple
                | modes: Word Scramble, Word Order, Word Blur and Type It.<br>
                |• <b>My Documents</b>: your own editable, syncable pages.<br>
                |• <b>Multi-translation search</b> across several Bibles at once.<br>
                |• Many more improvements &mdash; see the "What's new" video above.<br>
                |<br>
                |Please test and report any bugs via
                |<a href="https://github.com/AndBible/and-bible/issues/new/choose">GitHub</a>
                | or Main Menu &rarr; Report a bug.<br>
                |<br>
                |Best regards, Tuomas<br><br>
                |P.S. You can support AndBible development financially by
                |<a href="$buyDevelopmentLink">sponsoring development hours</a>.
                | <br><br>
                | (Standard beta notice below)
                | <br><br>
            """.trimMargin()
            val htmlMessage = "$extraMessage$videoMessageLink<br><br>$par1<br><br> $par2<br><br> $par3 <br><br> <i>${host.getString(R.string.version_text, CommonUtils.applicationVersionName)}</i>"

            val spanned = htmlToSpan(htmlMessage)

            val d = AlertDialog.Builder(host)
                .setTitle(host.getString(R.string.beta_notice_title))
                .setMessage(spanned)
                .setIcon(R.drawable.ic_logo)
                .setNeutralButton(host.getString(R.string.dismiss)) { _, _ -> it.resume(false)}
                .setPositiveButton(host.getString(R.string.beta_notice_dismiss_until_update)) { _, _ ->
                    preferences.setInt("beta-notice-displayed2", announceVersion)
                    it.resume(true)
                }
                .setOnCancelListener {_ -> it.resume(false)}
                .create()
            d.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
        } else {
            it.resume(false)
        }
    }

    // ——— §2.3 row 4: the deep link ———————————————————————————————————————————————————————————————

    /**
     * Classic `MainBibleActivity.openLink` (`:2318-2356` at the batch's baseline), reached from the
     * `intent.hasExtra("openLink")` branch `StartupActivity.gotoMainBibleActivity` populates on an
     * `ACTION_VIEW` launch. Renamed from `openLink` on the way out: `CommonUtils.openLink` and
     * `BibleView.openLink` are two different, unrelated functions of that name already.
     */
    fun openDeepLink(uri: Uri) {
        when (uri.host) {
            "read.andbible.org" -> {
                val key = CommonUtils.parseAndBibleReference(uri) ?: return
                windowControl.showLink(key.document, key)
            }
            "stepbible.org" -> {
                val qParam = uri.getQueryParameter("q") ?: return

                val docRegex = Regex("""version=([^&|]+)""")
                val refRegex = Regex("""reference=([^&|]+)""")

                val versionMatch = docRegex.find(qParam)
                val version = if (versionMatch != null) versionMatch.groups[1]?.value else null
                val doc = if (version != null) Books.installed().getBook(version) else null

                val defV11n = if (doc is SwordBook) doc.versification else KJVA
                val v11nStr = uri.getQueryParameter("v11n")
                val v11n = if (v11nStr == null) defV11n else Versifications.instance().getVersification(v11nStr) ?: defV11n

                val refMatch = refRegex.find(qParam) ?: return
                val keyStr = refMatch.groups[1]?.value ?: return

                val key = PassageKeyFactory.instance().getKey(v11n, keyStr)
                windowControl.showLink(doc, key)
            }
            "www.bible.com" -> {
                val urlRegex = Regex("""/(\w+)/bible/(\w+)/([\w\d]+)\.(\d+)\.(\w+)""")
                val match = urlRegex.find(uri.path.toString()) ?: return
                val book = match.groups[3]?.value ?: return
                val chapter = match.groups[4]?.value?.toInt() ?: return
                val docStr = match.groups[5]?.value
                val doc = if (docStr != null) Books.installed().getBook(docStr) else null
                val defV11n = if (doc is SwordBook) doc.versification else KJVA

                val key = VerseFactory.fromString(defV11n, "$book.$chapter")
                windowControl.showLink(doc, key)
            }
        }
    }

    // ——— §2.3 row 5: the network callback ————————————————————————————————————————————————————————

    var networkAvailable: Boolean = false

    val networkCallback = object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            networkAvailable = true
            if (!hostPaused) {
                syncScope.launch { startSync() }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            networkAvailable = false
            stopPeriodicSync()
        }
    }

    /**
     * Whether [registerNetworkCallback] actually registered, so [unregisterNetworkCallback] can be
     * called unconditionally from a host's `onDestroy`.
     *
     * Not cosmetic: `registerDefaultNetworkCallback` is gated on API >= N, a host may be destroyed
     * without ever having bootstrapped a reading route at all (`NavHostComposeActivity` has ~45
     * routes that never call [registerNetworkCallback]), and `unregisterNetworkCallback` throws
     * `IllegalArgumentException` for a callback that was never registered.
     */
    private var networkCallbackRegistered = false

    /** Classic `MainBibleActivity.onCreate`'s network-callback block, which R7 extracted here (so
     *  the range that citation used to name no longer exists in that file); its surviving CALL site
     *  is `MainBibleActivity.kt:604`. Balanced by [unregisterNetworkCallback]. */
    fun registerNetworkCallback() {
        val connManager = host.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    /**
     * The other end of [registerNetworkCallback] (T8a item 4), called from each host's `onDestroy`.
     *
     * Removes THIS bootstrap's own [networkCallback] object and nothing else, which is what makes it
     * safe while a second host is still alive -- see the class KDoc for the per-host/shared analysis
     * and the test that measures it. Idempotent, so a second `onDestroy` (or a host that never
     * registered) is a no-op rather than an `IllegalArgumentException`.
     */
    fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        networkCallbackRegistered = false
        val connManager = host.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connManager.unregisterNetworkCallback(networkCallback)
    }

    // ——— §2.3 rows 6 and 7: keyboard mode and the sdcard permission ——————————————————————————————

    /**
     * Classic `MainBibleActivity.requestSdcardPermission`.
     *
     * **R7 finding, still not fixed here, and REACHABLE since reading-host re-typing T8d:** only
     * `MainBibleActivity` implements the RESULT half (`onRequestPermissionsResult`, matching on
     * [SDCARD_READ_REQUEST]). A host that calls this and does not handle the result asks and then
     * ignores the answer, and `NavHostComposeActivity` is such a host.
     *
     * R7 recorded this as unreachable. It was already half-wrong once the launcher flipped --
     * [showFirstRunNotices] calls this on whichever host bootstraps, ONCE per process -- and T8d
     * made it worse rather than better: the shared settings-return body
     * (`ReadingCommands.preferenceSettingsChanged`) calls this on EVERY return from Settings, on
     * whichever host is running. On API 23..28 with `request_sdcard_permission_pref` on, that means
     * a permission dialog every time the user leaves Settings, and on DENY
     * `turnOffManualInstallFolderSetting()` never runs, so the preference stays on and the dialog
     * comes back next time -- a nag loop classic does not have. On GRANT the manual install folder
     * is not enabled until the next start, but that self-heals
     * (`SwordEnvironmentInitialisation.kt:76` re-checks).
     *
     * Wiring the result half is a behaviour addition, not an extraction, and is deliberately left
     * to the task that owns the `onRequestPermissionsResult` family as a whole.
     */
    fun requestSdcardPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val requestSdCardPermission = preferences.getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false)
            if (requestSdCardPermission && host.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                host.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), SDCARD_READ_REQUEST)
            }
        }
    }

    fun setSoftKeyboardMode() {
        // F59 (T9 walk, spec §3.1.1): the design's "one mode on every API level" was gated on
        // whether `WindowInsetsCompat.Type.ime()` is visible to Compose under `ADJUST_NOTHING` below
        // API 30 (Task B0's gate). It was measured NEGATIVE on a container API 28 emulator: under
        // `adjustResize` (today) the insets listener reports `ime.bottom=685` and
        // `ReadingInsets.imeHeight` becomes 685; under `adjustNothing` the listener is never even
        // DISPATCHED for the IME and both `ReadingInsets.imeHeight` and Compose's own
        // `WindowInsets.ime` stay 0 forever -- `WindowInsetsCompat` below API 30 synthesises
        // `Type.ime()` from the system-window inset, which only carries the keyboard while the
        // framework is actually resizing the window for it, and `ADJUST_NOTHING` stops it resizing.
        //
        // So the scope narrows to API 30+ (`Build.VERSION_CODES.R`), not 35 (`VANILLA_ICE_CREAM`):
        // `ADJUST_NOTHING` stops the framework folding the IME into the system-window insets so
        // `WindowInsets.ime` becomes non-zero, the app supplies the shrink itself through
        // `ReadingInsets` -> `applyImeBottomPadding`, and NavHostComposeActivity's insets listener
        // gate moved from >= 35 to >= 30 to match (`NavHostComposeActivity.kt`'s `onCreate`). Below
        // 30 the framework's own resize is kept exactly as it was, along with the branch that picks
        // between it and `ADJUST_PAN` for multi-window -- both channels a below-30 device still needs
        // since neither the app nor Compose can see the keyboard there.
        //
        // `AndroidManifest.xml`'s windowSoftInputMode on the nav host cannot be per-API and stays
        // `adjustResize`; `NavHostComposeActivity.onCreate` additionally sets `ADJUST_NOTHING`
        // programmatically on SDK >= R, before `setContent`, closing spec §1.3's startup gap (no
        // frame is laid out between `super.onCreate` and that call) -- this bootstrap method's own
        // call (from `bootstrapIfNeeded`, which can run later, e.g. from `onNewIntent`) then repeats
        // the same value on SDK >= R and is the ONLY place that sets it on `MainBibleActivity`, which
        // has no such early onCreate call of its own.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        } else if (windowControl.isMultiWindow) {
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        } else {
            host.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    // ——— §2.3 row 8: the cloud-sync loop —————————————————————————————————————————————————————————

    private var syncJob: Job? = null

    suspend fun startSync() {
        if(CommonUtils.isCloudSyncEnabled) {
            synchronize(true)
            if(syncJob != null) {
                Log.e(TAG, "syncJob already exists")
            } else {
                syncJob = host.lifecycleScope.launch { periodicSync() }
            }
        }
    }

    private suspend fun periodicSync() {
        Log.i(TAG, "Periodic sync starting")
        while (CommonUtils.isCloudSyncEnabled && syncJob?.isCancelled == false) {
            delay(60*1000) // 1 minute
            if(syncJob?.isCancelled == false) synchronize()
        }
    }

    private val lastTouched: Long get() {
        return windowRepository.windowList.mapNotNull { it.bibleView?.lastTouched }.max()
    }

    private val syncInterval get() =
        CommonUtils.settings.getLong("cloud_sync_interval", DEFAULT_SYNC_INTERVAL) * 1000
    private val lastSynchronized get() =
        CommonUtils.settings.getLong("globalLastSynchronized", 0L)

    private val now get() = System.currentTimeMillis()

    suspend fun synchronize(force: Boolean = false) {
        if(CommonUtils.isCloudSyncEnabled && networkAvailable) {
            windowRepository.saveIntoDb(false)
            if (force || (now - max(lastSynchronized, lastTouched) > syncInterval && CloudSync.hasChanges())) {
                Log.i(TAG, "Performing periodic sync")
                if(!CloudSync.signedIn) {
                    CloudSync.signIn(host)
                }
                CloudSync.start()
                CloudSync.waitUntilFinished()
            }
        }
    }

    fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    companion object {
        /**
         * The once-per-process gate on [showFirstRunNotices], classic
         * `MainBibleActivity.Companion.initialized`. Process-wide, not per host: the notices are
         * "have we shown these to the user since the app started", which does not become two
         * questions because there are two Activities.
         */
        var initialized = false
    }
}
