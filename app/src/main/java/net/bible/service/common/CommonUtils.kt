/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.common

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.Html
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.LayoutDirection
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.bible.android.BibleApplication
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.BuildConfig
import net.bible.android.activity.BuildConfig.BUILD_TYPE
import net.bible.android.activity.BuildConfig.BuildDate
import net.bible.android.activity.BuildConfig.FLAVOR_appearance
import net.bible.android.activity.BuildConfig.FLAVOR_distchannel
import net.bible.android.activity.BuildConfig.GitHash
import net.bible.android.activity.R
import net.bible.android.activity.SpeakWidgetManager
import net.bible.android.common.toV11n
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.android.database.bookmarks.BookmarkType
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.LabelType
import net.bible.android.database.json
import net.bible.android.view.activity.ActivityComponent
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.buyDevelopmentLink
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.speak.TextToSpeechNotificationManager
import net.bible.service.download.DownloadManager
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.addManuallyInstalledEpubBooks
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mybible.addManuallyInstalledMyBibleBooks
import net.bible.service.sword.mysword.addManuallyInstalledMySwordBooks
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.util.IOUtil
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.GenBookBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.Passage
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseKey
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.system.Versifications
import org.jdom2.input.SAXBuilder
import org.jdom2.xpath.XPathFactory
import org.spongycastle.util.io.pem.PemReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.IndexOutOfBoundsException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.util.*
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ArrayBlockingQueue
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@Suppress("DEPRECATION")
fun htmlToSpan(html: String?): Spanned {
    val spanned = SpannableString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    } else {
        Html.fromHtml(html)
    })

    class MyURLSpan(url: String): URLSpan(url) {
        override fun onClick(widget: View) {
            CommonUtils.openLink(url)
        }
    }

    val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
    for (s in urlSpans) {
        val start = spanned.getSpanStart(s)
        val end = spanned.getSpanEnd(s)
        spanned.removeSpan(s)
        val newSpan = MyURLSpan(s.url)
        spanned.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return spanned
}

fun PreferenceFragmentCompat.getPreferenceList(p_: Preference? = null, list_: ArrayList<Preference>? = null): ArrayList<Preference> {
    val p = p_?: preferenceScreen
    val list = list_?: ArrayList()
    if (p is PreferenceCategory || p is PreferenceScreen) {
        val pGroup: PreferenceGroup = p as PreferenceGroup
        val pCount: Int = pGroup.preferenceCount
        for (i in 0 until pCount) {
            getPreferenceList(pGroup.getPreference(i), list) // recursive call
        }
    } else {
        list.add(p)
    }
    return list
}

const val promoAndNewFeaturesPlaylistAutostart = "https://www.youtube.com/watch?v=f2cf6-7liMo&list=PLD-W_Iw-N2MlOXgRTLQqoXZpQxkqf119a&index=1"
// "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlOXgRTLQqoXZpQxkqf119a" // What's new 4.0 playlist

const val windowsAndWorkspacesPlaylist = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ" // 4.0 (Window's and workspaces playlist)
const val labelsAndBookmarksPlaylist = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mnv8aYRK3QbZBjE3ZMmrJZ7" // 4.0 (Labels and Bookmarks playlist)
const val bookmarksMyNotesPlaylist = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MlzNt0Zpna-QoTBpEpWSden" // 4.0 (playlist for bookmarking & my notes tutorials)
const val notesAndStudyPadsPlayList= "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m" // 4.0 (playlist for notes & study pads)
const val speakPlayList = "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Ml4arSb_fDBYqgiYtVPmjFo" // playlist for speak related tutorials

const val textDisplaySettingsVideo = windowsAndWorkspacesPlaylist
const val windowPinningVideo = windowsAndWorkspacesPlaylist
const val studyPadsVideo = notesAndStudyPadsPlayList
const val workspacesVideo = windowsAndWorkspacesPlaylist

const val betaIntroVideo = "https://youtu.be/XdKmoe95KhI" //promoAndNewFeaturesPlaylistAutostart
const val newFeaturesIntroVideo = "https://youtu.be/bf33j4tLbxQ" // promoAndNewFeaturesPlaylistAutostart

const val speakHelpVideo = speakPlayList
const val automaticSpeakBookmarkingVideo = speakPlayList

val BookmarkEntities.Label.displayName get() =
    when {
        isSpeakLabel -> application.getString(R.string.speak)
        isUnlabeledLabel -> application.getString(R.string.label_unlabelled)
        else -> name
    }


open class CommonUtilsBase {
    @Inject lateinit var windowControl: WindowControl
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bibleTraverser: BibleTraverser
}

enum class BibleViewSwipeMode {CHAPTER, PAGE, NONE}

class Ref<T>(var value: T? = null)

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */

object AdvancedSpeakSettings {
    var autoBookmark: Boolean
        get() = CommonUtils.settings.getBoolean("speak_autoBookmark", false)
        set(value) {
            CommonUtils.settings.setBoolean("speak_autoBookmark", value)
        }

    var synchronize: Boolean
        get() = CommonUtils.settings.getBoolean("speak_synchronize", true)
        set(value) {
            CommonUtils.settings.setBoolean("speak_synchronize", value)
        }

    var replaceDivineName: Boolean
        get() = CommonUtils.settings.getBoolean("speak_replaceDivineName", false)
        set(value) {
            CommonUtils.settings.setBoolean("speak_replaceDivineName", value)
        }

    var restoreSettingsFromBookmarks: Boolean
        get() = CommonUtils.settings.getBoolean("speak_restoreSettingsFromBookmarks", false)
        set(value) {
            CommonUtils.settings.setBoolean("speak_restoreSettingsFromBookmarks", value)
        }

    fun reset() {
        autoBookmark = false
        synchronize = true
        replaceDivineName = false
        restoreSettingsFromBookmarks = false
    }
}

object CommonUtils : CommonUtilsBase() {
    private const val COLON = ":"
    private const val DEFAULT_MAX_TEXT_LENGTH = 250
    private const val ELLIPSIS = "..."

	val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val TAG = "CommonUtils"
    var isAndroid = true
        private set

    // Backup of old databases will be stored here for 30 days
    val dbBackupPath get() = File(SharedConstants.internalFilesDir, "database_backups").apply { mkdirs() }

    val applicationNameMedium get() = application.getString(R.string.app_name_medium)

    val applicationVersionName: String
        get() {
            var versionName: String
            try {
                val manager = application.packageManager
                val info = manager.getPackageInfo(application.packageName, 0)
                versionName = info.versionName ?: throw NameNotFoundException()
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Error getting package name.", e)
                versionName = "Error"
            }

            return "$versionName#$GitHash $FLAVOR_distchannel $FLAVOR_appearance $BUILD_TYPE (built $BuildDate)"
        }

    val mainVersion: String get() {
        val verFull = applicationVersionName
        val numbers = verFull.split(".")
        return "${numbers[0]}.${numbers[1]}"
    }

    val isRtl get(): Boolean {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == LayoutDirection.RTL
    }

    val mainVersionFloat: Float get() {
        return mainVersion.toFloat()
    }

    val isBeta: Boolean get() {
        val verFull = applicationVersionName
        val ver = verFull.split("#")[0]
        return ver.endsWith("-beta") || ver.endsWith("-alpha") || application.packageName.endsWith(".next") || isDebugMode
    }

    val applicationVersionNumber: Int
        get() {
            // TODO we have to change this to Long if we one day will have very long version numbers.
            var versionNumber: Int
            try {
                val manager = application.packageManager
                val info = manager.getPackageInfo(application.packageName, 0)
                versionNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.longVersionCode.toInt()
                } else info.versionCode
            } catch (e: NameNotFoundException) {
                Log.e(TAG, "Error getting package name.", e)
                versionNumber = -1
            }

            return versionNumber
        }

    private val packageInfo: PackageInfo
        get () {
            val manager = application.packageManager
            return manager.getPackageInfo(application.packageName, 0)
        }

    val isFirstInstall get() = packageInfo.firstInstallTime == packageInfo.lastUpdateTime


    val isPortrait: Boolean get() {
        val res = CurrentActivityHolder.currentActivity?.resources?: BibleApplication.application.resources
        return res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    val megabytesFree: Long
        get() {
            val bytesAvailable = getFreeSpace(Environment.getExternalStorageDirectory().path)
            val megAvailable = bytesAvailable / 1048576
            Log.i(TAG, "Megs available on internal memory :$megAvailable")
            return megAvailable
        }

    val booleanSettings get() = DatabaseContainer.instance.settingsDb.booleanSettingDao()
    val longSettings get() = DatabaseContainer.instance.settingsDb.longSettingDao()
    val stringSettings get() = DatabaseContainer.instance.settingsDb.stringSettingDao()
    val doubleSettings get() = DatabaseContainer.instance.settingsDb.doubleSettingDao()

    class AndBibleSettings {
        fun getString(key: String, default: String? = null) = stringSettings.get(key, default)
        fun getLong(key: String, default: Long) = longSettings.get(key, default)
        fun getInt(key: String, default: Int) = longSettings.get(key, default.toLong()).toInt()
        fun getBoolean(key: String, default: Boolean) = if(initialized) booleanSettings.get(key, default) else default
        fun getDouble(key: String, default: Double) = doubleSettings.get(key, default)
        fun getFloat(key: String, default: Float): Float = doubleSettings.get(key, default.toDouble()).toFloat()

        fun setString(key: String, value: String?) = stringSettings.set(key, value)
        fun setLong(key: String, value: Long?) = longSettings.set(key, value)
        fun setInt(key: String, value: Int?) = longSettings.set(key, value?.toLong())
        fun setBoolean(key: String, value: Boolean?) = booleanSettings.set(key, value)
        fun setDouble(key: String, value: Double?) = doubleSettings.set(key, value)
        fun setFloat(key: String, value: Float?) = doubleSettings.set(key, value?.toDouble())

        fun getStringSet(key: String, defValues: Set<String> = emptySet()): Set<String> {
            val s = getString(key, null) ?: return defValues
            return try { json.decodeFromString(serializer(), s) } catch (e: SerializationException) { defValues }
        }

        fun setStringSet(key: String, values: Set<String>?) {
            if(values == null) removeString(key)
            else setString(key, json.encodeToString(serializer(), values))
        }

        fun removeString(key: String) = setString(key, null)
        fun removeDouble(key: String) = setDouble(key, null)
        fun removeLong(key: String) = setLong(key, null)
        fun removeBoolean(key: String) = setBoolean(key, null)

        val monochromeMode: Boolean get() = getBoolean("monochrome_mode", onyxSupport?.isMonochrome == true)
        val disableAnimations: Boolean get() = getBoolean("disable_animations", onyxSupport?.isOnyxDevice == true)
        val fontSizeMultiplier: Int get() = getInt("font_size_multiplier", 100)
        val fontSizeMultiplierFloat: Float get() = getInt("font_size_multiplier", 100) / 100F
        val bibleViewSwipeMode: BibleViewSwipeMode get() = BibleViewSwipeMode.valueOf(getString("bible_view_swipe_mode", "CHAPTER")!!)
    }

    private var _settings: AndBibleSettings? = null
    val settings: AndBibleSettings get() {
        val s = _settings
        if(s != null) return s
        return AndBibleSettings().apply { _settings = this }
    }

    val localePref: String?
        get() = realSharedPreferences.getString("locale_pref", null)

    // Note: use AndBibleSettings always if possible to save preferences. They are persisted in DB.
    val realSharedPreferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)

    val truncatedDate: Date
        get() = Calendar.getInstance().let { date ->
            date.set(Calendar.HOUR_OF_DAY, 0)
            date.set(Calendar.MINUTE, 0)
            date.set(Calendar.SECOND, 0)
            date.set(Calendar.MILLISECOND, 0)
            date.time
        }

    val deviceIdentifier: String get() =
        Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
            ?: realSharedPreferences.getString("android_id", null).let {
                if(it == null) {
                    val id = UUID.randomUUID().toString()
                    realSharedPreferences.edit().putString("android_id", id).apply()
                    id
                } else { it}
            }

    init {
        try {
            if (Build.ID != null) {
                isAndroid = true
            }
        } catch (cnfe: Exception) {
            isAndroid = false
        }

        println("isAndroid:$isAndroid")
    }

    fun buildActivityComponent(): ActivityComponent {
        return DaggerActivityComponent.builder()
                .applicationComponent(application.applicationComponent)
                .build()
    }

    fun getShareableDocumentText(selection: Selection): String {
        return SwordContentFacade.getSelectionText(
            selection,
            showVerseNumbers = settings.getBoolean("share_verse_numbers", true),
            advertiseApp = settings.getBoolean("share_show_add", true),
            abbreviateReference = settings.getBoolean("share_abbreviate_reference", true),
            showNotes = settings.getBoolean("show_notes", true),
            showVersion = settings.getBoolean("share_show_version", true),
            showReference = settings.getBoolean("share_show_reference", true),
            showReferenceAtFront = settings.getBoolean("share_show_reference_at_front", true),
            showSelectionOnly = settings.getBoolean("show_selection_only", true),
            showEllipsis = settings.getBoolean("show_ellipsis", true),
            showQuotes = settings.getBoolean("share_show_quotes", false)
        )
    }

    fun getFreeSpace(path: String): Long {
        val stat = StatFs(path)
        val bytesAvailable = stat.availableBytes
        Log.i(TAG, "Free space :$bytesAvailable")
        return bytesAvailable
    }

    fun limitTextLength(text: String?, maxLength: Int = DEFAULT_MAX_TEXT_LENGTH, singleLine: Boolean = false): String? {
        var text = text
        if (text != null) {
            val origLength = text.length

            if (singleLine) {
                // get first line but limit length in case there are no line breaks
                text = StringUtils.substringBefore(text, "\n")
            }

            if (text!!.length > maxLength) {
                // break on a space rather than mid-word
                val cutPoint = text.indexOf(" ", maxLength)
                if (cutPoint >= maxLength) {
                    text = text.substring(0, cutPoint + 1)
                }
            }

            if (text.length != origLength) {
                text += ELLIPSIS
            }
        }
        return text
    }

    fun ensureDirExists(dir: File) {
        if (!dir.exists() || !dir.isDirectory) {
            dir.mkdirs()
        }
    }

    fun deleteDirectory(path: File): Boolean {
        Log.i(TAG, "Deleting directory:" + path.absolutePath)
        if (path.exists()) {
            if (path.isDirectory) {
                path.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        deleteDirectory(file)
                    } else {
                        file.delete()
                        Log.i(TAG, "Deleted " + file)
                    }
                }
            }
            val deleted = path.delete()
            if (!deleted) {
                Log.w(TAG, "Failed to delete:" + path.absolutePath)
            }
            return deleted
        }
        return false
    }

    fun loadProperties(propertiesFile: File): Properties {
        val properties = Properties()
        if (propertiesFile.exists()) {
            var `in`: FileInputStream? = null
            try {
                `in` = FileInputStream(propertiesFile)
                properties.load(`in`)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading properties", e)
            } finally {
                IOUtil.close(`in`)
            }
        }
        return properties
    }

    fun pause(seconds: Int) {
        pauseMillis(seconds * 1000)
    }

    fun pauseMillis(millis: Int) {
        try {
            Thread.sleep(millis.toLong())
        } catch (e: Exception) {
            Log.e(TAG, "Error sleeping", e)
        }

    }

    fun getSharedPreference(key: String, defaultValue: String): String? = settings.getString(key, defaultValue)
    fun saveSharedPreference(key: String, value: String) = settings.setString(key, value)

    fun getResourceString(resourceId: Int, vararg formatArgs: Any): String {
        return resources.getString(resourceId, *formatArgs)
    }

    fun getResourceInteger(resourceId: Int): Int {
        return resources.getInteger(resourceId)
    }

    fun getResourceBoolean(resourceId: Int): Boolean {
        return resources.getBoolean(resourceId)
    }

    val resources: Resources get() =
        CurrentActivityHolder.currentActivity?.resources?: application.resources


    fun getResourceColor(resourceId: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val theme = CurrentActivityHolder.currentActivity?.theme?: resources.newTheme().apply {
                applyStyle(R.style.AppTheme, true)
            }

            resources.getColor(resourceId, theme)
        } else {
            resources.getColor(resourceId)
        }

    fun getResourceDrawable(resourceId: Int, context: Context? = null): Drawable? {
        val theme = CurrentActivityHolder.currentActivity?.theme?: resources.newTheme().apply {
            applyStyle(R.style.AppTheme, true)
        }

        return ResourcesCompat.getDrawable(context?.resources?:resources, resourceId, theme)
    }

    fun getTintedDrawable(res: Int, color: Int = R.color.grey_500): Drawable {
        val d = getResourceDrawable(res)!!
        d.mutate().setTint(getResourceColor(color))
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        return d
    }

    /**
     * convert dip measurements to pixels
     */
    fun convertDipsToPx(dips: Float): Int {
        // Converts 14 dip into its equivalent px
        val scale = resources.displayMetrics.density
        return (dips * scale + 0.5f).toInt()
    }

    fun convertDipsToPx(dips: Int): Int = convertDipsToPx(dips.toFloat())

    /**
     * convert dip measurements to pixels
     */
    fun convertPxToDips(px: Int): Int {
        val scale = resources.displayMetrics.density
        return Math.round(px / scale)
    }

    /**
     * StringUtils methods only compare with a single char and hence create lots
     * of temporary Strings This method compares with all chars and just creates
     * one new string for each original string. This is to minimise memory
     * overhead & gc.
     *
     * @param str
     * @param removeChars
     * @return
     */
    fun remove(str: String, removeChars: CharArray): String? {
        if (StringUtils.isEmpty(str) || !StringUtils.containsAny(str, *removeChars)) {
            return str
        }

        val r = StringBuilder(str.length)
        // for all chars in string
        for (i in 0 until str.length) {
            val strCur = str[i]

            // compare with all chars to be removed
            var matched = false
            var j = 0
            while (j < removeChars.size && !matched) {
                if (removeChars[j] == strCur) {
                    matched = true
                }
                j++
            }
            // if current char does not match any in the list then add it to the
            if (!matched) {
                r.append(strCur)
            }
        }
        return r.toString()
    }

    /** format seconds duration as h:m:s
     *
     * @param secs duration
     * @return h:m:s
     */
    fun getHoursMinsSecs(secs: Long): String {
        val h = (secs / 3600).toInt()
        val m = (secs / 60 % 60).toInt()
        val s = (secs % 60).toInt()

        val hms = StringBuilder()
        if (h > 0) {
            hms.append(h).append(COLON)
        }

        // add padding for 1 digit mins
        if (m < 10) {
            hms.append(0)
        }
        hms.append(m).append(COLON)

        // add padding for 1 digit secs
        if (s < 10) {
            hms.append(0)
        }
        hms.append(s)
        return hms.toString()
    }

    fun getKeyDescription(key: Key): String {
        var name: String
        try {
            name = key.name

            // do not show verse 0
            if (key is Verse) {
                if (key.verse == 0 && name.endsWith("0")) {
                    val verse0 = "[\\W]0$"
                    name = name.replace(verse0.toRegex(), "")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting key name - could that Versification does not contain book")
            // but this normally works
            name = key.osisRef.replace('.', ' ')
        }

        return name
    }

    fun getWholeChapter(currentVerse: Verse, showIntros: Boolean = true): VerseRange {
        Log.i(TAG, "getWholeChapter (Key) ${currentVerse.osisID}")
        val versification = currentVerse.versification
        val book = currentVerse.book
        val chapter = currentVerse.chapter

        val startChapter = if(showIntros && chapter == 1) 0 else chapter
        val endChapter = if(showIntros && chapter == 0) 1 else chapter

        val targetChapterFirstVerse = Verse(versification, book, startChapter, 0)
        val targetChapterLastVerse = Verse(versification, book, endChapter, versification.getLastVerse(book, endChapter))

        // convert to full chapter before returning because bible view is for a full chapter
        return VerseRange(versification, targetChapterFirstVerse, targetChapterLastVerse)
    }

    fun getWholeChapters(v11n: Versification, book: BibleBook, chapter1: Int, chapter2: Int): VerseRange {
        val targetChapterFirstVerse = Verse(v11n, book, chapter1, 0)
        val targetChapterLastVerse = Verse(v11n, book, chapter2, v11n.getLastVerse(book, chapter2))

        return VerseRange(v11n, targetChapterFirstVerse, targetChapterLastVerse)
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    fun restartApp(callingActivity: Activity) {
        val contentIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
        val pendingIntent = PendingIntent.getActivity(callingActivity, 0, contentIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val mgr = callingActivity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scope.launch {
            CloudSync.waitUntilFinished()
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent)
            exitProcess(2)
        }
    }

    private fun forceStopApp() {
        Log.i(TAG, "forceStopApp!")
        scope.launch {
            CloudSync.waitUntilFinished()
            exitProcess(2)
        }
    }

    private val lastDisplaySettings: List<WorkspaceEntities.TextDisplaySettings.Types> get() {
        val lastDisplaySettingsString = settings.getString("lastDisplaySettings", null)
        var lastTypes = mutableListOf<WorkspaceEntities.TextDisplaySettings.Types>()
        if(lastDisplaySettingsString!= null) {
            try {
                lastTypes = LastTypesSerializer.fromJson(lastDisplaySettingsString).types
            } catch (e: SerializationException) {
                Log.e(TAG, "Could not deserialize $lastDisplaySettingsString")
            }
        }
        return lastTypes
    }

    val lastDisplaySettingsSorted get() = lastDisplaySettings.sortedBy { it.name }

    fun displaySettingChanged(type: WorkspaceEntities.TextDisplaySettings.Types) {
        val lastTypes = lastDisplaySettings.toMutableList()
        lastTypes.remove(type)
        while (lastTypes.size >= 5) {
            lastTypes.removeAt(lastTypes.size - 1)
        }
        lastTypes.add(0, type)
        settings.setString("lastDisplaySettings", LastTypesSerializer(lastTypes).toJson())
    }

    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

    suspend fun unlockDocument(context: AppCompatActivity, book: Book): Boolean {
        class ShowAgain: Exception()
        var repeat = true
        while(repeat) {
            val passphrase: String? = try {suspendCoroutine {
                val name = EditText(context)
                name.text = SpannableStringBuilder(book.unlockKey ?: "")
                name.selectAll()
                name.requestFocus()
                AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setPositiveButton(R.string.okay) { d, _ ->
                        it.resume(name.text.toString())
                    }
                    .setView(name)
                    .setNegativeButton(R.string.cancel) { _, _ -> it.resume(null) }
                    .setNeutralButton(R.string.show_unlock_info) { _, _ -> context.lifecycleScope.launch(Dispatchers.Main) {
                        showAbout(context, book)
                        it.resumeWithException(ShowAgain())
                    } }
                    .setTitle(application.getString(R.string.give_passphrase_for_module, book.initials))
                    .create()
                    .show()
            } } catch (e: ShowAgain) {
                continue
            }
            if (passphrase != null) {
                val success = book.unlock(passphrase)
                if (success) {
                    docDao.getBook(book.initials)?.apply {
                        cipherKey = passphrase
                        docDao.update(this)
                    }
                    return true
                }
            }
            repeat = suspendCoroutine {
                AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes) { d, _ ->
                        it.resume(true)
                    }
                    .setNegativeButton(R.string.no) { _, _ -> it.resume(false) }
                    .setTitle(application.getString(R.string.try_again_passphrase))
                    .create()
                    .show()
            }
        }
        return false
    }

    /** about display is generic so handle it here
     */
    suspend fun showAbout(context: Context, document: Book) {
        var about = "<b>${document.name}</b>\n\n"
        val badDocument = document.bookMetaData.getProperty("BadDocument")
        if(badDocument != null) {
            about += "<b>${application.getString(R.string.warn_bad_document)}</b>\n\n"
        }
        about += document.bookMetaData.getProperty("About") ?: ""
        // either process the odd formatting chars in about
        about = about.replace("\\pard", "")
        about = about.replace("\\par", "\n")

        val shortPromo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_PROMO)

        if(shortPromo != null) {
            about += "\n\n${shortPromo}"
        }

        // Copyright and distribution information
        val shortCopyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_SHORT_COPYRIGHT)
        val copyright = document.bookMetaData.getProperty(SwordBookMetaData.KEY_COPYRIGHT)
        val distributionLicense = document.bookMetaData.getProperty(SwordBookMetaData.KEY_DISTRIBUTION_LICENSE)
        val unlockInfo = document.bookMetaData.getProperty(SwordBookMetaData.KEY_UNLOCK_INFO)
        var copyrightMerged = ""
        if (StringUtils.isNotBlank(shortCopyright)) {
            copyrightMerged += shortCopyright
        } else if (StringUtils.isNotBlank(copyright)) {
            copyrightMerged += "\n\n" + copyright
        }
        if (StringUtils.isNotBlank(distributionLicense)) {
            copyrightMerged += "\n\n" +distributionLicense
        }
        if (StringUtils.isNotBlank(copyrightMerged)) {
            val copyrightMsg = application.getString(R.string.module_about_copyright, copyrightMerged)
            about += "\n\n" + copyrightMsg
        }
        if(unlockInfo != null) {
            about += "\n\n<b>${application.getString(R.string.unlock_info)}</b>\n\n$unlockInfo"
        }

        // add version
        val existingDocument = Books.installed().getBook(document.initials)
        val existingVersion = existingDocument?.bookMetaData?.getProperty("Version")
        val existingVersionDate = existingDocument?.bookMetaData?.getProperty("SwordVersionDate") ?: "-"

        val inDownloadScreen = context is DownloadActivity

        val versionLatest = document.bookMetaData.getProperty("Version")
        val versionLatestDate = document.bookMetaData.getProperty("SwordVersionDate") ?: "-"

        val versionMessageInstalled = if(existingVersion != null)
            application.getString(R.string.module_about_installed_version,
                try {
                    Version(existingVersion).toString()
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing version $existingVersion", e)
                    existingVersion
                }, existingVersionDate)
        else null

        val versionMessageLatest = if(versionLatest != null)
            application.getString((
                if (existingDocument != null)
                    R.string.module_about_latest_version
                else
                    R.string.module_about_installed_version),
                try {Version(versionLatest).toString()} catch(e: Exception) {versionLatest}, versionLatestDate)
        else null

        if(versionMessageLatest != null) {
            about += "\n\n" + versionMessageLatest
            if(versionMessageInstalled != null && inDownloadScreen)
                about += "\n" + versionMessageInstalled
        }

        val history = document.bookMetaData.getValues("History")
        if(history != null) {
            about += "\n\n" + application.getString(R.string.about_version_history, "\n" +
                history.reversed().joinToString("\n"))
        }

        // add versification
        if (document is SwordBook) {
            val versification = document.versification
            val versificationMsg = application.getString(R.string.module_about_versification, versification.name)
            about += "\n\n" + versificationMsg
        }

        // add id
        if (document is SwordBook) {
            val repoName = document.getProperty(DownloadManager.REPOSITORY_KEY)
            val repoMessage = if(repoName != null) application.getString(R.string.module_about_repository, repoName) else ""
            val osisIdMessage = application.getString(R.string.module_about_osisId, document.initials)
            about += """


                $osisIdMessage
                
                $repoMessage
                """.trimIndent()
        }
        about = about.replace("\n", "<br>")
        val spanned = htmlToSpan(about)
        suspendCoroutine<Any?> {
            val d = AlertDialog.Builder(context)
                .setMessage(spanned)
                .setCancelable(false)
                .setPositiveButton(R.string.okay) { dialog, buttonId ->
                    it.resume(null)
                }.create()
            d.show()
            val textView = d.findViewById<TextView>(android.R.id.message)!!
            textView.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    fun showHelp(callingActivity: ActivityBase, filterItems: List<Int>? = null, showVersion: Boolean = false) {
        val app = application
        val versionMsg = app.getString(R.string.version_text, applicationVersionName)

        data class HelpItem(val title: Int, val text: Int, val videoLink: String? = null)

        val help = listOf(
            HelpItem(R.string.help_nav_title, R.string.help_nav_text),
            HelpItem(R.string.help_contextmenus_title, R.string.help_contextmenus_text),
            HelpItem(R.string.help_window_pinning_title, R.string.help_window_pinning_text, windowPinningVideo),
            HelpItem(R.string.help_bookmarks_title, R.string.help_bookmarks_text, bookmarksMyNotesPlaylist), // beta video
            HelpItem(R.string.studypads, R.string.help_studypads_text, studyPadsVideo), // beta video
            HelpItem(R.string.help_search_title, R.string.help_search_text),
            HelpItem(R.string.help_workspaces_title, R.string.help_workspaces_text, workspacesVideo),
            HelpItem(R.string.help_hidden_features_title, R.string.help_hidden_features_text)
        ).run {
            if(filterItems != null) {
                filter { filterItems.contains(it.title) }
            } else this
        }

        val buy = app.getString(R.string.buy_development)
        val support = app.getString(R.string.buy_development2)
        val heartIcon = ImageSpan(getTintedDrawable(R.drawable.baseline_attach_money_24))
        val buyMessage = "<b>$support</b>: <a href=\"$buyDevelopmentLink\">$buy</a>"
        val iconStr = SpannableString("* ")
        iconStr.setSpan(heartIcon, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        val spannedBuy = TextUtils.concat(iconStr, htmlToSpan(buyMessage))

        var htmlMessage = ""

        for(helpItem in help) {
            val videoMessage =
                if(helpItem.videoLink != null) {
                    "<i><a href=\"${helpItem.videoLink}\">${app.getString(R.string.watch_tutorial_video)}</a></i><br>"
                } else ""

            val helpText = app.getString(helpItem.text).replace("\n", "<br>")
            htmlMessage += "<b>${app.getString(helpItem.title)}</b><br>$videoMessage$helpText<br><br>"
        }

        val spanned = TextUtils.concat(htmlToSpan(htmlMessage), spannedBuy, if(showVersion) htmlToSpan("<br><br><i>$versionMsg</i>") else "")

        val d = AlertDialog.Builder(callingActivity)
            .setTitle(R.string.help)
            .setIcon(R.drawable.ic_logo)
            .setMessage(spanned)
            .setPositiveButton(android.R.string.ok) { _, _ ->  }
            .create()

        d.show()
        d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
    }

    fun openLink(link: String, forceAsk: Boolean = false) {
        val activity = CurrentActivityHolder.currentActivity!!
        if (isDiscrete || forceAsk) {
            activity.lifecycleScope.launch(Dispatchers.Main) {
                if(Dialogs.simpleQuestion(activity,
                        message = net.bible.android.view.activity.page.application.getString(R.string.external_link_question, link),
                        title = net.bible.android.view.activity.page.application.getString(R.string.external_link),
                    )
                ) {
                    activity.startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse(link)),
                        ActivityBase.STD_REQUEST_CODE
                    )
                }
            }
        } else {
            activity.startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse(link)),
                ActivityBase.STD_REQUEST_CODE
            )
        }
    }

    fun verifySignature(file: File, signatureFile: File): Boolean {
        // Adapted from https://stackoverflow.com/questions/34066949/verify-digital-signature-on-android
        if(!signatureFile.canRead()) return false
        val reader = PemReader(InputStreamReader(application.resources.openRawResource(R.raw.publickey)))
        val data = file.inputStream()
        val signatureData = signatureFile.inputStream()

        val publicKeyPem = reader.readPemObject()
        val publicKeyBytes: ByteArray = publicKeyPem.content
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec) as RSAPublicKey
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initVerify(publicKey)
        val buffy = ByteArray(16 * 1024)
        var read = -1
        while (data.read(buffy).also { read = it } != -1) {
            signature.update(buffy, 0, read)
        }
        val signatureBytes = ByteArray(publicKey.modulus.bitLength() / 8)
        signatureData.read(signatureBytes)
        return signature.verify(signatureBytes)
    }
    
    private var ttsNotificationManager: TextToSpeechNotificationManager? = null
    private var ttsWidgetManager: SpeakWidgetManager? = null

    var initialized = false
    private var booksInitialized = false
    var onyxSupport: OnyxSupportInterface? = null

    fun initializeApp() {
        if(!initialized) {
            try {
                val pid = android.os.Process.myPid()
                Runtime.getRuntime().exec("logcat -P '$pid'").waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Logcat could not be run")
            }

            DatabaseContainer.ready = true
            DatabaseContainer.instance
            buildActivityComponent().inject(this@CommonUtils)
            ttsNotificationManager = TextToSpeechNotificationManager()
            if(!BuildVariant.Appearance.isDiscrete) {
                ttsWidgetManager = SpeakWidgetManager()
            }
            initializeOnyx()

            addManuallyInstalledMyBibleBooks()
            addManuallyInstalledMySwordBooks()
            addManuallyInstalledEpubBooks()

            // IN practice we don't need to restore this data, because it is stored by JSword in book
            // metadata (persisted by JSWORD to files) too.
            //docDao.getAll().forEach {
            //    Books.installed().getBook(it.initials)?.putProperty(REPOSITORY_KEY, it.repository)
            //}

            initialized = true
        }

        if(!booksInitialized && Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }.isNotEmpty()) {
            if(!application.isRunningTests) {
                for (it in docDao.getUnlocked()) {
                    val book = Books.installed().getBook(it.initials)
                    book.unlock(it.cipherKey)
                }
            }
            booksInitialized = true
        }
    }

    private fun initializeOnyx() {
        if (FLAVOR_distchannel != "fdroid") {
            val adapter = Class.forName("net.bible.service.onyx.OnyxSupport")
            val constructor = adapter.getDeclaredConstructor()
            onyxSupport = constructor.newInstance() as OnyxSupportInterface
        }
    }

    suspend fun initializeAppCoroutine() {
        if(!initialized) {
            try {
                val pid = android.os.Process.myPid()
                Runtime.getRuntime().exec("logcat -P '$pid'").waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Logcat could not be run")
            }

            DatabaseContainer.ready = true
            DatabaseContainer.instance
            withContext(Dispatchers.Main) {
                buildActivityComponent().inject(this@CommonUtils)
                ttsNotificationManager = TextToSpeechNotificationManager()
                if(!BuildVariant.Appearance.isDiscrete) {
                    ttsWidgetManager = SpeakWidgetManager()
                }
            }
            withContext(Dispatchers.IO) {
                addManuallyInstalledMyBibleBooks()
                addManuallyInstalledMySwordBooks()
                addManuallyInstalledEpubBooks()
            }
            initializeOnyx()

            // IN practice we don't need to restore this data, because it is stored by JSword in book
            // metadata (persisted by JSWORD to files) too.
            //docDao.getAll().forEach {
            //    Books.installed().getBook(it.initials)?.putProperty(REPOSITORY_KEY, it.repository)
            //}

            initialized = true
        }

        if(!booksInitialized && Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }.isNotEmpty()) {
            if(!application.isRunningTests) {
                for (it in docDao.getUnlocked()) {
                    Books.installed().getBook(it.initials)?.unlock(it.cipherKey)
                }
            }
            booksInitialized = true
        }
    }

    fun prepareForDestruction() {
        if(!initialized) return
        windowControl.windowRepository.saveIntoDb(false)
    }

    fun destroy() {
        ttsNotificationManager?.destroy()
        ttsWidgetManager?.destroy()
        ttsNotificationManager = null
        ttsWidgetManager = null
        initialized = false
    }

    fun prepareData() {
        val dataVersionNow = 1L
        val dataVersion = settings.getLong("data-version", 0)
        if(dataVersion < 1) {
            prepareExampleBookmarksAndWorkspaces()
        }
        if(dataVersion != dataVersionNow) {
            settings.setLong("data-version", dataVersionNow)
        }
    }

    const val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

    val defaultBible get() = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }[0] as SwordBook
    val defaultVerse: VerseRange get() {
        val (otVerse, ntVerse, psVerse) =
            listOf("Gen.1.1-Gen.1.3", "Joh.3.16-Joh.3.18", "Ps.1.1-Ps.1.3")
                .map {
                    VerseRangeFactory.fromString(KJVA, it).toV11n(defaultBible.versification)
                }
        return when {
            defaultBible.contains(ntVerse.start) -> ntVerse
            defaultBible.contains(otVerse.start) -> otVerse
            else -> psVerse
        }
    }

    private fun prepareExampleBookmarksAndWorkspaces() {
        val bookmarkDao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val hasExistingBookmarks = bookmarkDao.allBookmarks(BookmarkSortOrder.ORDER_NUMBER).isNotEmpty()

        val migratedNotesName = application.getString(R.string.migrated_my_notes)
        var highlightLabels = emptyList<BookmarkEntities.Label>()

        if(bookmarkDao.allLabelsSortedByName().none { !it.name.startsWith("__") && it.name != migratedNotesName }) {
            val redLabel = BookmarkEntities.Label(name = application.getString(R.string.label_red), type = LabelType.HIGHLIGHT, color = Color.argb(255, 255, 0, 0), underlineStyleWholeVerse = false, favourite = true)
            val greenLabel = BookmarkEntities.Label(name = application.getString(R.string.label_green), type = LabelType.HIGHLIGHT, color = Color.argb(255, 0, 255, 0), underlineStyleWholeVerse = false, favourite = true)
            val blueLabel = BookmarkEntities.Label(name = application.getString(R.string.label_blue), type = LabelType.HIGHLIGHT, color = Color.argb(255, 0, 0, 255), underlineStyleWholeVerse = false, favourite = true)
            val underlineLabel = BookmarkEntities.Label(name = application.getString(R.string.label_underline), type = LabelType.HIGHLIGHT, color = Color.argb(255, 255, 0, 255), underlineStyle = true, underlineStyleWholeVerse = true, favourite = true)

            highlightLabels = listOf(
                redLabel,
                greenLabel,
                underlineLabel,
                blueLabel,
            )

            val salvationLabel = BookmarkEntities.Label(name = application.getString(R.string.label_salvation), type = LabelType.EXAMPLE, color = Color.argb(255,  100, 0, 150))
            bookmarkDao.insertLabels(highlightLabels)

            fun getBookmark(verseRange: VerseRange, start: Double, end: Double): BookmarkEntities.BibleBookmarkWithNotes {
                val v1 = verseRange.toVerseArray()[start.toInt()]
                val v2 = verseRange.toVerseArray()[end.toInt()]
                val l1 = SwordContentFacade.getCanonicalText(defaultBible, v1, true).length
                val l2 = SwordContentFacade.getCanonicalText(defaultBible, v2, true).length
                val tr = BookmarkEntities.TextRange(((start - start.toInt())*l1).roundToInt(), ((end-end.toInt()) * l2).roundToInt())
                val v = VerseRange(v1.versification, v1, v2)

                return BookmarkEntities.BibleBookmarkWithNotes(v, textRange = tr, wholeVerse = false, book = defaultBible)
            }

            if(!hasExistingBookmarks) {
                bookmarkDao.insert(salvationLabel)

                // first bookmark, full verses, with underline
                var b = BookmarkEntities.BibleBookmarkWithNotes(defaultVerse, textRange = null, wholeVerse = true, book = defaultBible).apply { primaryLabelId = underlineLabel.id }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, underlineLabel.id))
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, salvationLabel.id))

                // second bookmark, red
                b = getBookmark(defaultVerse, 1.0, 1.5).apply { primaryLabelId = redLabel.id }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, redLabel.id))

                // third bookmark, green
                b = getBookmark(defaultVerse, 1.2, 1.4).apply {
                    primaryLabelId = greenLabel.id
                    notes = lorem
                }
                bookmarkDao.insert(b.bookmarkEntity)
                bookmarkDao.insert(b.noteEntity!!)

                bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(b.id, greenLabel.id))

                val salvationVerses = listOf("Joh.3.3", "Tit.3.3-Tit.3.7", "Rom.3.23-Rom.3.24", "Rom.4.3", "1Tim.1.15", "Eph.2.8-Eph.2.9", "Isa.6.3", "Rev.4.8", "Exo.20.2-Exo.20.17")
                    .mapNotNull { try {VerseRangeFactory.fromString(KJVA, it)} catch (e: NoSuchVerseException) {
                        Log.e("CommonUtils", "NoSuchVerseException for ${it}??!", e)
                        null 
                    }}

                salvationVerses
                    .map {
                        BookmarkEntities.BibleBookmarkWithNotes(it, textRange = null, wholeVerse = true, book = null).apply { type = BookmarkType.EXAMPLE }
                    }.forEach {
                        bookmarkDao.insert(it.bookmarkEntity)
                        bookmarkDao.insert(BookmarkEntities.BibleBookmarkToLabel(it.id, salvationLabel.id))
                    }
            }
        }
        val workspaceDao = DatabaseContainer.instance.workspaceDb.workspaceDao()
        val ws = workspaceDao.allWorkspaces()
        if(ws.isNotEmpty()) {
            workspaceDao.updateWorkspaces(ws)
            settings.setBoolean("first-time", false)
        } else {
            val workspaceSettings = WorkspaceEntities.WorkspaceSettings()
            val workspaceIds = listOf(
                WorkspaceEntities.Workspace(name = application.getString(R.string.workspace_number, 1), workspaceSettings = workspaceSettings),
                WorkspaceEntities.Workspace(name = application.getString(R.string.workspace_number, 2), workspaceSettings = workspaceSettings),
            ).map {
                workspaceDao.insertWorkspace(it)
                it.id
            }
            settings.setString("current_workspace_id", workspaceIds[0].toString())
        }
    }

    fun makeLarger(icon: Drawable, sizeMultiplier: Float = 1.0f): Drawable = LayerDrawable(arrayOf(icon)).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val m = sizeMultiplier
            setLayerSize(0, (icon.intrinsicWidth*m).toInt(), (icon.intrinsicHeight*m).toInt())
        }
    }

    fun iconWithSync(icon: Int, syncOn: Boolean, sizeMultiplier: Float? = null): Drawable {
        var syncColor = R.color.sync_on_green
        var circleColor = R.color.background_color
        if (!syncOn) {
            circleColor = R.color.transparent
            syncColor = R.color.transparent
        }
        val iconDrawable = getTintedDrawable(icon)
        val d1 = getTintedDrawable(R.drawable.ic_workspace_overlay_24dp, syncColor)
        val circleDrawable = getTintedDrawable(R.drawable.ic_baseline_circle_24, circleColor)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            LayerDrawable(arrayOf(iconDrawable, circleDrawable, d1)).apply {
                val s = sizeMultiplier?:1.0F
                val size = (d1.intrinsicWidth * s).toInt()
                val s1 = (d1.intrinsicWidth * 0.7 * s).toInt()
                val s2 = (d1.intrinsicWidth * 0.6 * s).toInt()
                val d = (s1-s2) / 2
                setLayerSize(0, size, size)
                setLayerSize(1, s1, s1)
                setLayerSize(2, s2, s2)
                setLayerGravity(1, Gravity.BOTTOM or Gravity.END)
                setLayerGravity(2, Gravity.BOTTOM or Gravity.END)
                setLayerInsetEnd(1, -d)
                setLayerInsetBottom(1, -d)
                setLayerInsetEnd(2, 0)
                setLayerInsetBottom(2, 0)
            }
        else
            d1
    }

    fun combineIcons(icon: Int, icon2: Int, sizeMultiplier: Float? = null): Drawable {
        val d1 = getTintedDrawable(icon)
        val d2 = getTintedDrawable(icon2)
        val circleDrawable = getTintedDrawable(R.drawable.ic_baseline_circle_24, R.color.background_color)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            LayerDrawable(arrayOf(d1, circleDrawable, d2)).apply {
                val s = sizeMultiplier ?: 1.0F
                val size = (d1.intrinsicWidth * s).toInt()
                val s1 = (d1.intrinsicWidth * 0.7 * s).toInt()
                val s2 = (d1.intrinsicWidth * 0.6 * s).toInt()
                val d = (s1 - s2) / 2
                setLayerSize(0, size, size)
                setLayerSize(1, s1, s1)
                setLayerSize(2, s2, s2)
                setLayerGravity(1, Gravity.BOTTOM or Gravity.END)
                setLayerGravity(2, Gravity.BOTTOM or Gravity.END)
                setLayerInsetEnd(1, -d)
                setLayerInsetBottom(1, -d)
                setLayerInsetEnd(2, 0)
                setLayerInsetBottom(2, 0)
            }
        else
            d1
    }

    fun fixAlertDialogButtons(dialog: AlertDialog) {
        val container = dialog.findViewById<Button>(android.R.id.button1).parent
        if(container is FrameLayout) {
            container.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 2)
        }
        // Some older devices Androids have LinearLayout. But they don't need this hack anyway.
    }

    suspend fun checkPoorTranslations(activity: ActivityBase): Boolean {
        val languageTag = Locale.getDefault().toLanguageTag()
        val languageCode = Locale.getDefault().language

        Log.i(TAG, "Language tag $languageTag, code $languageCode")

        // Transifex as of 6.12.2024
        val goodLanguages = "en,af,fi,fr,de,it,pt-BR,ro,sk,sl,tr,kk,uk,cz,lt,yue,zh-Hans-CN,zh-Hant-TW,es,ta,cs,hu,nl,sr,te,pl,hr,bn".split(",")

        // 4.0 list:

        //    listOf(
        //
        //    "af", "cs", "de", "en", "eo", "es", "fi", "fr", "hi", "hu", "it", "kk", "lt", "my", "nl", "pl", "pt", "ro", "ru",
        //    "sk", "sl", "te", "uk", "zh-Hans-CN", "zh-Hant-TW", "he", "iw"
        //    // almost: "ko", "he", "ar" (hebrew, check...)
        //)

        fun checkLanguage(lang: String): Boolean =
            if(lang.length == 2)
                lang == languageCode
            else
                lang == languageTag


        val languageOK = goodLanguages.any {checkLanguage(it)}

        if(languageOK || (
                settings.getString("poor-translations-dismissed", "") == languageTag
                    && settings.getString("poor-translations-dismissed-version", "") == mainVersion))
        {
            return true
        }

        return suspendCoroutine {
            val lang = Locale.getDefault().displayLanguage
            val instr = application.getString(R.string.instructions_for_translators)
            val instructionsUrl = "https://github.com/AndBible/and-bible/wiki/Translating-User-Interface"
            val instructionsLink = "<a href=\"$instructionsUrl\">$instr</a>"
            val msg = htmlToSpan(application.getString(R.string.incomplete_translation, lang, application.getString(R.string.app_name_long), instructionsLink))
            val dlgBuilder = AlertDialog.Builder(activity)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(R.string.proceed_anyway) { _, _ -> it.resume(true) }
                .setNegativeButton(R.string.beta_notice_dismiss_until_update) { _, _ ->
                    settings.setString("poor-translations-dismissed-version", mainVersion)
                    settings.setString("poor-translations-dismissed", languageTag)
                    it.resume(true)
                }
                .setNeutralButton(R.string.close) { _, _ ->
                    it.resume(false)
                    activity.finish()
                }

            val d = dlgBuilder.show()
            d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    suspend fun requestNotificationPermission(activity_: ActivityBase? = null) = withContext(Dispatchers.Main) {
        val activity = activity_?:CurrentActivityHolder.currentActivity?: return@withContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
                var request = true
                if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    val answer = suspendCoroutine {
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.permission_required)
                            .setIcon(R.drawable.ic_logo)
                            .setMessage(R.string.progress_status_permission)
                            .setPositiveButton(R.string.okay) { _, _ -> it.resume(true) }
                            .setNegativeButton(R.string.cancel) { _, _ -> it.resume(false) }
                            .setOnCancelListener { _ -> it.resume(null) }
                            .show()
                    }
                    request = answer == true
                }
                if(request) {
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 999)
                }
            }
        }
    }

    fun changeAppIconAndName() {
        // There's issue on Android 5 that icon simply disappears and calculator does not appear.
        // See https://github.com/AndBible/and-bible/issues/2310
        if (BuildVariant.Appearance.isDiscrete || Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) return
        val discrete = realSharedPreferences.getBoolean("discrete_mode", false)
        val packageName = BuildConfig.APPLICATION_ID
        val allNames = listOf(
            "net.bible.android.activity.StartupActivity",
            "net.bible.android.view.activity.Calculator"
        )

        val activeName = allNames[if(discrete) 1 else 0]
        var settingsChanged = false
        Log.d(TAG, "Changing app icon / name to $activeName")
        for (name in allNames) {
            val value = name == activeName
            Log.d(TAG, "changing $name to $value")
            val component = ComponentName(packageName, name)
            val currentSettings = application.packageManager.getComponentEnabledSetting(component)
            val newSetting =
                if(value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            if(currentSettings != newSetting) {
                application.packageManager.setComponentEnabledSetting(
                    component,
                    newSetting,
                    PackageManager.DONT_KILL_APP
                )
                settingsChanged = true
            }
        }
        if(settingsChanged) {
            forceStopApp()
        }
    }

    fun createDiscreteNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = application.getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CALC_NOTIFICATION_CHANNEL,
                application.getString(R.string.app_name_calculator), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    val isCloudSyncAvailable get() = !(
        BuildVariant.Appearance.isDiscrete
            || BuildVariant.DistributionChannel.isFdroid
            || Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1
        )

    val isCloudSyncEnabled: Boolean get () =
        if(!isCloudSyncAvailable) false
        else SyncableDatabaseDefinition.ALL.any { it.syncEnabled }
    val isDiscrete get() = BuildVariant.Appearance.isDiscrete || realSharedPreferences.getBoolean("discrete_mode", false)
    val showCalculator get() = BuildVariant.Appearance.isDiscrete || realSharedPreferences.getBoolean("show_calculator", false)

    fun md5Hash(str: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bigInt = BigInteger(1, md.digest(str.toByteArray(Charsets.UTF_8)))
        return String.format("%032x", bigInt)
    }

    fun appendUrl(u: String, filename: String): String =
        if(u.endsWith("/"))
            "$u$filename"
        else
            "$u/$filename"

    fun unzipFile(zipFile: File, destinationDir: File, filePrefix: String = "") =
        unzipInputStream(zipFile.inputStream(), destinationDir, filePrefix)

    fun unzipInputStream(inputStream: InputStream, destinationDir: File, filePrefix: String = "") {
        val buffer = ByteArray(8192)
        ZipInputStream(inputStream).use { zIn ->
            var zipEntry = zIn.nextEntry
            while (zipEntry != null) {
                if (!zipEntry.isDirectory) {
                    val filePath = zipEntry.name.replace('\\', '/')
                    val file = File(destinationDir, filePrefix + filePath)
                    Log.i(TAG, "Writing $file")
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fOut ->
                        var count = zIn.read(buffer)
                        while (count != -1) {
                            fOut.write(buffer, 0, count)
                            count = zIn.read(buffer)
                        }
                    }
                }
                zipEntry = zIn.nextEntry
            }
        }
    }

    val isDebugMode get() = (0 != application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)

    val tmpDir: File get() {
        val file = File(application.filesDir, "tmp")
        file.mkdirs()
        return file
    }

    val tmpFile: File get() = File.createTempFile("andbible-", ".tmp", tmpDir)

    fun gzipFile(sourceFile: File, destinationFile: File) {
        destinationFile.outputStream().use {
            GZIPOutputStream(it).use {
                sourceFile.inputStream().use { input ->
                    input.copyTo(it)
                }
            }
        }
    }

    fun gunzipFile(sourceFile: File, destinationFile: File) {
        sourceFile.inputStream().use {
            GZIPInputStream(it).use {
                destinationFile.outputStream().use { output ->
                    it.copyTo(output)
                }
            }
        }
    }

    suspend fun determineFileType(uri: Uri): BackupControl.AbDbFileType = withContext(Dispatchers.IO) {
        application.contentResolver.openInputStream(uri)?.use { inputStream ->
            val header = ByteArray(16)
            inputStream.read(header)
            val headerString = String(header)
            if (headerString == "SQLite format 3\u0000")
                BackupControl.AbDbFileType.SQLITE3
            else if (headerString.startsWith("PK\u0003\u0004")) {
                BackupControl.AbDbFileType.ZIP
            } else
                BackupControl.AbDbFileType.UNKNOWN
        } ?: BackupControl.AbDbFileType.UNKNOWN
    }

    fun makeAndBibleUrl(
        keyStr: String,
        docInitials: String? = null,
        v11n: String? = null,
        ordinal: Int? = null
    ): String {
        var url = "https://read.andbible.org/$keyStr"
        val queryParameters = mutableListOf<String>()
        if(docInitials != null) {
            queryParameters.add("document=$docInitials")
        }
        if(v11n != null) {
            queryParameters.add("v11n=$v11n")
        }
        if(ordinal != null) {
            queryParameters.add("ordinal=${ordinal}")
        }
        if(queryParameters.isNotEmpty()) {
            url += "?${queryParameters.joinToString("&")}"
        }
        return url
    }

    fun copyToClipboard(clip: ClipData, toastMessage: Int = R.string.text_copied_to_clicpboard) {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
        ABEventBus.post(ToastEvent(application.getString(toastMessage)))
    }

    private val calcPinRegex = Regex("""^(0+)(\d+)$""")

    fun removeLeadingZeroes(s: String): String {
        val match = calcPinRegex.matchEntire(s)
        if(match != null) {
            return match.groupValues[2]
        }
        return s
    }

    suspend fun documentUpgradeConfirmation(context: Activity): Boolean = context.run {
        val warningTitle = getString(R.string.bookmark_warning)
        val warningMessage = getString(R.string.bookmark_warning2)
        val warningRecommendation = getString(R.string.bookmark_warning4)
        val warningQuestion = getString(R.string.bookmark_warning3)
        val warningMsg = "$warningMessage\n\n$warningRecommendation\n\n$warningQuestion"
        withContext(Dispatchers.Main) {
            suspendCoroutine {
                AlertDialog.Builder(this@run)
                    .setTitle(warningTitle)
                    .setMessage(warningMsg)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes) { _, _ -> it.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> it.resume(false) }.create().show()
            }
        }
    }

    fun parseAndBibleReference(uri: Uri): BookAndKey? {
        val urlRegex = Regex("""/(.*)""")
        val docStr = uri.getQueryParameter("document")
        val doc = if (docStr != null) Books.installed().getBook(docStr) else null

        val defV11n = if (doc is SwordBook) doc.versification else KJVA
        val v11nStr = uri.getQueryParameter("v11n")
        val v11n = if (v11nStr == null) defV11n else Versifications.instance().getVersification(v11nStr) ?: defV11n

        val match = urlRegex.find(uri.path.toString()) ?: return null
        var keyStr = match.groups[1]?.value ?: return null
        if (keyStr.contains(":")) {
            keyStr = keyStr.split(":", limit = 2)[1]
        }

        val key: Passage = PassageKeyFactory.instance().getKey(v11n, keyStr)

        val ordinalStr = uri.getQueryParameter("ordinal")
        if(ordinalStr != null) {
            val ord = ordinalStr.toInt()
            return BookAndKey(key, doc, ordinal = OrdinalRange(ord))
        }
        return BookAndKey(key, doc)
    }

    fun parseAndBibleReference(uri: String): BookAndKey?
        = parseAndBibleReference(Uri.parse(uri))

}

const val CALC_NOTIFICATION_CHANNEL = "calc-notifications"

@Serializable
data class LastTypesSerializer(val types: MutableList<WorkspaceEntities.TextDisplaySettings.Types>) {
    fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }

    companion object {
        fun fromJson(jsonString: String): LastTypesSerializer {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}

val firstBibleDoc get() = Books.installed().books.first { it.bookCategory == BookCategory.BIBLE } as SwordBook

fun <T> Cursor.map(f: (c: Cursor) -> T): Collection<T> = use {
    val result = mutableListOf<T>()
    if(!moveToFirst()) return@use result
    do {
        result.add(f.invoke(this))
    } while(moveToNext())
    result
}

fun <T> Cursor.forEach(f: (c: Cursor) -> T) = use {
    if(!moveToFirst()) return
    do {
        f.invoke(this)
    } while(moveToNext())
}

fun <T> Cursor.getFirst(f: (c: Cursor) -> T): T = use {
    if(!moveToFirst()) throw RuntimeException("First item not found")
    f.invoke(this)
}

fun <T> Cursor.getFirstOrNull(f: ((c: Cursor) -> T)? = null): T? = use {
    if(!moveToFirst()) return@use null
    f?.invoke(this)
}

suspend fun <T, V> Collection<T>.asyncMap(action: suspend (T) -> V): Collection<V> = withContext(Dispatchers.IO) {
     map { async { action(it) }}.awaitAll()
}

suspend fun <T, V> Collection<T>.asyncMap(maxThreads: Int, action: suspend (T) -> V): Collection<V> = withContext(Dispatchers.IO) {
    val semaphore = Semaphore(maxThreads)
    map {async {semaphore.withPermit { action(it) } } }.awaitAll()
}

private val builders = ArrayBlockingQueue<SAXBuilder>(32)

fun <R> useSaxBuilder(block: (it: SAXBuilder) -> R): R {
    val builder = builders.poll()?: SAXBuilder().also {
        it.setFeature("http://xml.org/sax/features/external-general-entities", false)
    }
    val rv = block(builder)
    builders.offer(builder)
    return rv
}

private val xPathInstances = ArrayBlockingQueue<XPathFactory>(32)
fun <R> useXPathInstance(block: (it: XPathFactory) -> R): R {
    val xPath = xPathInstances.poll()?: XPathFactory.instance()
    val rv = block(xPath)
    xPathInstances.offer(xPath)
    return rv
}

fun Book.getBookAndKey(keyStr: String, ordinal: Int? = null): BookAndKey? {
    val k = try {getKey(keyStr)} catch (e: NoSuchKeyException) {null} ?: return null
    return BookAndKey(k, this, ordinal = OrdinalRange(ordinal?: ordinalRangeFor(k).first))
}

val BookAndKey.prev: BookAndKey get() {
    val nextKey: Key = when(this.key) {
        is VerseRange -> {
            CommonUtils.bibleTraverser.getPrevVerse(this.document as AbstractPassageBook, this.key.end)

        }
        is Verse -> {
            CommonUtils.bibleTraverser.getPrevVerse(this.document as AbstractPassageBook, this.key)
        }
        else -> {
            val backend = when(val book = this.document!!) {
                is SwordGenBook -> book.backend as AbstractKeyBackend
                is SwordDictionary -> book.backend as AbstractKeyBackend
                else -> throw RuntimeException("Unsupported")
            }
            val idx = backend.indexOf(this.key)
            try {
                backend.get(idx - 1)
            } catch (e: IndexOutOfBoundsException) {
                backend.last()
            }
        }
    }
    return BookAndKey(
        nextKey,
        this.document,
        OrdinalRange(this.document!!.ordinalRangeFor(nextKey).last)
    )
}

val BookAndKey.next: BookAndKey get() {
    val nextKey: Key = when(this.key) {
        is VerseRange -> {
            CommonUtils.bibleTraverser.getNextVerse(this.document as AbstractPassageBook, this.key.end)
        }
        is Verse -> {
            CommonUtils.bibleTraverser.getNextVerse(this.document as AbstractPassageBook, this.key)
        }
        else -> {
            val backend = when(val book = this.document!!) {
                is SwordGenBook -> {
                    if(book.isEpub) {
                        book.backend as AbstractKeyBackend
                    } else {
                        book.backend as GenBookBackend
                    }
                }
                is SwordDictionary -> book.backend as AbstractKeyBackend
                else -> throw RuntimeException("Unsupported")
            }
            when (backend) {
                is AbstractKeyBackend -> {
                    val idx = backend.indexOf(this.key)
                    try {
                        backend.get(idx + 1)
                    } catch (e: IndexOutOfBoundsException) {
                        backend.first()
                    }
                }

                is GenBookBackend -> {
                    val keyList = backend.readIndex().flatten().toList()
                    val idx = keyList.indexOf(this.key)
                    try {
                        keyList[idx + 1]
                    } catch (e: IndexOutOfBoundsException) {
                        keyList.first()
                    }
                }
                else -> throw RuntimeException("Unsupported")
            }

        }
    }
    return BookAndKey(
        nextKey,
        this.document,
        OrdinalRange(this.document!!.ordinalRangeFor(nextKey).first)
    )
}

fun Book.ordinalRangeFor(key: Key): IntRange = SwordContentFacade.ordinalRangeFor(this, key)

val Key.tinyName: String get() =
    if(this is VerseKey<*>)
        synchronized(BookName::class.java) {
            val prevTruncateLength = BookName.getTruncateShortName()
            var length = 5
            var name: String
            do {
                BookName.setTruncateShortName(length--)
                name = this.name
            } while(length > 0 && name.length > 7)
            BookName.setTruncateShortName(prevTruncateLength)
            name
        }
    else name


val Key.shortName: String get() =
    if(this is VerseKey<*>)
        synchronized(BookName::class.java) {
            val oldValue = BookName.isFullBookName()
            BookName.setFullBookName(false)
            val text = name
            BookName.setFullBookName(oldValue)
            return text
        }
    else name

enum class DbType {
    BOOKMARKS, WORKSPACES, READINGPLANS, SETTINGS, REPOSITORIES, MODULES, EPUBS
}
enum class BackupType {
    // Note! We can only trust STUDYPAD_EXPORT, as manifest is not existing before AB version 822.
    STUDYPAD_EXPORT,
    DB_BACKUP,
    MODULE_BACKUP
}

const val ANDBIBLE_BACKUP_MANIFEST_FILENAME = "AndBibleBackupManifest.json"
@Serializable
data class AndBibleBackupManifest(
    val backupType: BackupType,
    val contains: Set<DbType>? = null,
    val manifestVersion: Int = 1,
    val andBibleVersion: Int = CommonUtils.applicationVersionNumber
) {
    fun toJson(): String {
        return CommonUtils.json.encodeToString(serializer(), this)
    }

    fun saveToZip(zipStream: ZipOutputStream) {
        val content = ByteArrayInputStream(toJson().toByteArray())
        val entry = ZipEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME)
        zipStream.putNextEntry(entry)
        content.copyTo(zipStream)
    }
    companion object {
        fun fromJson(jsonString: String): AndBibleBackupManifest {
            return CommonUtils.json.decodeFromString(serializer(), jsonString)
        }

        fun fromUri(uri: Uri): AndBibleBackupManifest? {
            val inputStream = application.contentResolver.openInputStream(uri) ?: return null
            val manifest = ZipInputStream(inputStream).use {
                val entry = it.nextEntry
                if (entry?.name == ANDBIBLE_BACKUP_MANIFEST_FILENAME) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (it.read(buffer).also { len = it } > 0) {
                        out.write(buffer, 0, len)
                    }
                    val outString = out.toString()
                    fromJson(outString)
                } else null
            }
            return manifest
        }
    }
}
