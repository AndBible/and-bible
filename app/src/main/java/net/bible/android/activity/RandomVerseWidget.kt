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

package net.bible.android.activity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.passage.Verse
import java.io.StringReader
import org.crosswire.common.xml.XMLUtil
import org.crosswire.jsword.book.BookData
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class RandomVerseWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive action: ${intent.action}")
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_VERSE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(
                    context,
                    RandomVerseWidget::class.java
                )
            )
            appWidgetIds.forEach { appWidgetId ->
                runBlocking {
                    refreshWidgetData(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.i(TAG, "onUpdate")
        for (appWidgetId in appWidgetIds) {
            // Perform initial setup
            updateAppWidget(context, appWidgetManager, appWidgetId)
            // Trigger the first data load
            runBlocking {
                refreshWidgetData(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.i(TAG, "updateAppWidget for id: $appWidgetId")
        val intent = Intent(context, VerseWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }

        val views = RemoteViews(context.packageName, R.layout.random_verse_widget)
        views.setRemoteAdapter(R.id.verse_list, intent)
        views.setEmptyView(R.id.verse_list, R.id.widget_empty_view)

        val refreshIntent = Intent(context, RandomVerseWidget::class.java).apply {
            action = ACTION_REFRESH_VERSE
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_root_layout, refreshPendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private suspend fun refreshWidgetData(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "Refreshing data for widget: $appWidgetId")
        
        var verseRef = ""
        val versesToStore = mutableListOf<String>()
        var mainVerseIndex = 0

        try {
            CommonUtils.initializeApp()
            val bibleApplication = context.applicationContext as BibleApplication
            net.bible.service.db.DatabaseContainer.initializeDatabase()
            val windowControl = bibleApplication.applicationComponent.windowControl()
            val activeBible = windowControl.defaultBibleDoc() ?: throw Exception("no Bible installed")

            val allVerseKeys = activeBible.globalKeyList.filter { key -> key is Verse }
            if (allVerseKeys.isEmpty()) throw Exception("no verses found")

            val randomNumber = (0 until allVerseKeys.size).random()
            val mainVerse = allVerseKeys[randomNumber] as Verse
            val v11n = activeBible.versification
            val ordinal = v11n.getOrdinal(mainVerse)
            
            val startOrdinal = (ordinal - 2).coerceAtLeast(0)
            val endOrdinal = (ordinal + 2).coerceAtMost(v11n.maximumOrdinal() - 1)

            for (i in startOrdinal..endOrdinal) {
                val v = v11n.decodeOrdinal(i)
                val rawXml = XMLUtil.writeToString(BookData(activeBible, v).saxEventProvider)
                val text = processXml(rawXml, verseNumbers = true)
                
                if (i == ordinal) {
                    mainVerseIndex = versesToStore.size
                    versesToStore.add("<b>$text</b>")
                } else {
                    versesToStore.add(text)
                }
            }

            verseRef = mainVerse.name

        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing widget data", e)
            versesToStore.add("context.getString(R.string.no_bibles_installed)")
            verseRef = "context.getString(R.string.no_bibles_installed)"
        }

        // Store the verse parts for the factory to retrieve
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val editor = prefs.edit()
        editor.putInt("${PREF_PREFIX_KEY}${appWidgetId}_count", versesToStore.size)
        versesToStore.forEachIndexed { index, s ->
            editor.putString("${PREF_PREFIX_KEY}${appWidgetId}_$index", s)
        }
        editor.apply()

        // Create RemoteViews for the update
        val partialViews = RemoteViews(context.packageName, R.layout.random_verse_widget)
        partialViews.setTextViewText(R.id.verse_reference, verseRef)
        
        // On Android 12+, we can explicitly set the scroll position to the main verse
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            partialViews.setScrollPosition(R.id.verse_list, mainVerseIndex)
        }
        
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, partialViews)

        // Notify the ListView to update itself from the factory
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.verse_list)

        Log.i(TAG, "Refreshed widget $appWidgetId with verse: $verseRef")
    }

    companion object {
        private const val ACTION_REFRESH_VERSE = "net.bible.android.activity.action.REFRESH_VERSE"
        private const val TAG = "RandomVerseWidget"
        const val PREFS_NAME = "RandomVerseWidgetPrefs"
        const val PREF_PREFIX_KEY = "verse_text_"
    }
}

class VerseWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return VerseRemoteViewsFactory(this.applicationContext, intent)
    }
}

class VerseRemoteViewsFactory(private val context: Context, private val intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    private val verses = mutableListOf<String>()
    private val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        Log.i("VerseRemoteViewsFactory", "onDataSetChanged for widget: $appWidgetId")
        val prefs = context.getSharedPreferences(RandomVerseWidget.PREFS_NAME, 0)
        verses.clear()
        
        val count = prefs.getInt("${RandomVerseWidget.PREF_PREFIX_KEY}${appWidgetId}_count", 0)
        for (i in 0 until count) {
            val v = prefs.getString("${RandomVerseWidget.PREF_PREFIX_KEY}${appWidgetId}_$i", "")
            if (!v.isNullOrEmpty()) {
                verses.add(v)
            }
        }
        
        if (verses.isEmpty()) {
            verses.add("context.getString(R.string.loading_text)")
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = verses.size

    override fun getViewAt(position: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.random_verse_widget_view_item).apply {
            // FromHtml is needed to render the font and bold tags correctly
            val styledText = Html.fromHtml(verses[position], Html.FROM_HTML_MODE_LEGACY)
            setTextViewText(R.id.verse_text_item, styledText)
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}

private fun processXml(
    xmlInput: String,
    linebreaks: Boolean = false,
    pilcrows: Boolean = true,
    verseNumbers: Boolean = false,
    chevrons: Boolean = false,
    brackets: Boolean = true,
    rawOsis: Boolean = false
): String {
    if (rawOsis) return xmlInput

    val factory = XmlPullParserFactory.newInstance()
    val xpp = factory.newPullParser()
    xpp.setInput(StringReader("<root>$xmlInput</root>")) // Wrapped in root for valid XML
    var eventType = xpp.eventType
    var verseNumber: String? = null
    var pilcrow = false
    var dropText = false
    var spaceBeforeNextText = false
    var para = StringBuilder()
    val paras = mutableListOf(para)

    val addText = { text: String ->
        if (!dropText) {
            if (spaceBeforeNextText) {
                if (para.isNotEmpty()) para.append(' ')
                spaceBeforeNextText = false
            }
            if (verseNumber != null) {
                if (verseNumbers) para.append(verseNumber)
                verseNumber = null
            }
            if (pilcrow && pilcrows) {
                para.append("¶")
                pilcrow = false
            }
            para.append(text)
        }
    }

    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (xpp.name) {
                "verse" -> xpp.getAttributeValue(null, "osisID")?.split('.')?.lastOrNull()?.let { verseNumber = it }
                "milestone" -> if (xpp.getAttributeValue(null, "marker") == "¶") {
                    para = StringBuilder()
                    paras.add(para)
                    pilcrow = true
                }
                "title", "chapter", "note" -> dropText = true
                "transChange" -> if (brackets) addText("[")
            }
            XmlPullParser.END_TAG -> when (xpp.name) {
                "verse" -> if (para.isNotEmpty()) spaceBeforeNextText = true
                "title", "chapter", "note" -> dropText = false
                "transChange" -> if (brackets) addText("]")
            }
            XmlPullParser.TEXT -> addText(xpp.text)
        }
        eventType = xpp.next()
    }

    return paras.joinToString(if (linebreaks) "\n" else " ") { (if (chevrons) "> " else "") + it.toString() }
}
