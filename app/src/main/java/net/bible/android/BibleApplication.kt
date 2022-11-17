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

package net.bible.android

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import android.widget.Toast
import net.bible.android.activity.R

import net.bible.android.control.ApplicationComponent
import net.bible.android.control.DaggerApplicationComponent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.report.BugReport
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.common.CommonUtils
import net.bible.service.device.ProgressNotificationManager
import net.bible.service.sword.SwordEnvironmentInitialisation
import net.bible.service.sword.myBibleBible
import net.bible.service.sword.myBibleCommentary
import net.bible.service.sword.myBibleDictionary

import org.crosswire.common.util.Language
import org.crosswire.common.util.PropertyMap
import org.crosswire.jsword.book.install.InstallManager
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.bridge.BookIndexer
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.jsword.versification.BibleBook
import java.util.Locale

object MyLocaleProvider: LocaleProvider {
    /**
     * Allow hardcoding exceptions for JSword locales, as
     * it does not support all Android locale variants.
     */
    override fun getUserLocale(): Locale {
        this.override?.run {return this}
        val default = Locale.getDefault()
        if(default.language == "sr" && default.script == "Latn") {
            return Locale.forLanguageTag("sr-LT")
        }
        return default
    }

    var override: Locale? = null
}

/** Main AndBible application singleton object
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class BibleApplication : Application() {
    init {
        // save to a singleton to allow easy access from anywhere
        application = this
    }
    lateinit var applicationComponent: ApplicationComponent
        private set

    var localeOverrideAtStartUp: String? = null
        private set

    open val isRunningTests: Boolean = false

    private val appStateSharedPreferences: SharedPreferences
        get() = getSharedPreferences(saveStateTag, Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref")
    override fun onCreate() {
        Log.i(TAG, "BibleApplication:onCreate, AndBible version ${CommonUtils.applicationVersionName} running on API ${Build.VERSION.SDK_INT}")
        super.onCreate()
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            BugReport.saveScreenshot()
            CommonUtils.realSharedPreferences.edit().putBoolean("app-crashed", true).commit()
            defaultExceptionHandler.uncaughtException(t, e)
        }
        ABEventBus.register(this)
        InstallManager.installSiteMap(
            PropertyMap().apply {
                resources.openRawResource(R.raw.repositories).use { load(it) }
            })
        BookType.addSupportedBookType(myBibleBible)
        BookType.addSupportedBookType(myBibleCommentary)
        BookType.addSupportedBookType(myBibleDictionary)

        LocaleProviderManager.setLocaleProvider(MyLocaleProvider)

        Log.i(TAG, "OS:" + System.getProperty("os.name") + " ver " + System.getProperty("os.version"))
        Log.i(TAG, "Java:" + System.getProperty("java.vendor") + " ver " + System.getProperty("java.version"))
        Log.i(TAG, "Java home:" + System.getProperty("java.home")!!)
        Log.i(TAG, "User dir:" + System.getProperty("user.dir") + " Timezone:" + System.getProperty("user.timezone"))
        logSqliteVersion()

        // This must be done before accessing JSword to prevent default folders being used
        SwordEnvironmentInitialisation.initialiseJSwordFolders()

        // Initialize the Dagger injector ApplicationScope objects
        applicationComponent = DaggerApplicationComponent.builder().build()

        // ideally this would be installed before initialiseJSwordFolders but the listener depends on applicationComponent
        SwordEnvironmentInitialisation.installJSwordErrorReportListener()

        // some changes may be required for different versions
        upgradeSharedPreferences()

        // initialise link to Android progress control display in Notification bar
        ProgressNotificationManager.instance.initialise()

        // various initialisations required every time at app startup

        localeOverrideAtStartUp = LocaleHelper.getOverrideLanguage(this)
    }

    var sqliteVersion = ""

    private fun logSqliteVersion() {
        try {
            val db = SQLiteDatabase.openOrCreateDatabase(":memory:", null)
            val cursor = db.rawQuery("select sqlite_version() AS sqlite_version", null)
            while (cursor.moveToNext()) {
                sqliteVersion += cursor.getString(0)
            }
            cursor.close()
            db.close()
        } catch (e: Throwable) {
            Log.e(TAG, "Couldn't figure out SQLite version due to error: ", e)
        }
        Log.i(TAG, "SQLite version: $sqliteVersion")
    }

    /**
     * Override locale.  If user has selected a different ui language to the devices default language
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun upgradeSharedPreferences() {
        val prefs = CommonUtils.realSharedPreferences
        val prevInstalledVersion = prefs.getInt("version", -1)
        val newInstall = prevInstalledVersion == -1

        val editor = prefs.edit()
        if (prevInstalledVersion < CommonUtils.applicationVersionNumber && !newInstall) {
            // there was a problematic Chinese index architecture before ver 24 so delete any old indexes
            if (prevInstalledVersion < 24) {
                Log.i(TAG, "Deleting old Chinese indexes")
                val chineseLanguage = Language("zh")

                val books = applicationComponent.swordDocumentFacade().documents
                for (book in books) {
                    if (chineseLanguage == book.language) {
                        try {
                            val bookIndexer = BookIndexer(book)
                            // Delete the book, if present
                            if (bookIndexer.isIndexed) {
                                Log.i(TAG, "Deleting index for " + book.initials)
                                bookIndexer.deleteIndex()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error deleting index", e)
                        }

                    }
                }
            }
            // clear old split screen config because it has changed a lot
            if (prevInstalledVersion < 154) {
                editor.remove("screen1_weight")
                editor.remove("screen2_minimized")
                editor.remove("split_screen_pref")
            }

            // clear setting temporarily used for window state
            if (prevInstalledVersion < 157) {
                val appPrefs = appStateSharedPreferences
                if (appPrefs.contains("screenStateArray")) {
                    Log.i(TAG, "Removing screenStateArray")
                    appPrefs.edit()
                            .remove("screenStateArray")
                            .apply()
                }
            }
        }

        if(prevInstalledVersion <= 350 && !newInstall) {
            val oldPrefValue = appStateSharedPreferences.getBoolean("night_mode_pref", false)
            val pref2value = appStateSharedPreferences.getString("night_mode_pref2", "false")
            val pref3value = when(pref2value) {
                "true" -> "manual"
                "false" -> "manual"
                "automatic" -> "automatic"
                else -> "manual"
            }
            val prefValue = when(pref2value) {
                "automatic" -> oldPrefValue
                "true" -> true
                "false" -> false
                else -> oldPrefValue
            }
            editor.putBoolean("night_mode_pref", prefValue).apply()
            editor.putString("night_mode_pref3", pref3value).apply()
        }

        Log.i(TAG, "Finished all Upgrading")
        editor.putInt("version", CommonUtils.applicationVersionNumber).apply()
    }

    open fun getLocalizedResources(language: String): Resources {
        val app = application
        val oldConf = app.resources.configuration
        val newConf = Configuration(oldConf)
        newConf.setLocale(Locale(language))
        return app.createConfigurationContext(newConf).resources
    }

    fun onEventMainThread(ev: ToastEvent) {
        val duration = ev.duration ?: Toast.LENGTH_SHORT
        val message = if (ev.messageId != null) getString(ev.messageId) else ev.message
        try {
            Toast.makeText(this, message, duration).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in showing toast $message", e)
        }
    }

    companion object {
        // this was moved from the MainBibleActivity and has always been called this
        private const val saveStateTag = "MainBibleActivity"

        lateinit var application: BibleApplication
            private set

        private const val TAG = "BibleApplication"
    }
}
