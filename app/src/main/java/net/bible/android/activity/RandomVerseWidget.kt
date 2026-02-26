package net.bible.android.activity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
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
        val bibleApplication = context.applicationContext as BibleApplication
        net.bible.service.db.DatabaseContainer.initializeDatabase()
        val windowControl = bibleApplication.applicationComponent.windowControl()
        val activeBible = windowControl.defaultBibleDoc()

        val verseText: String
        val verseRef: String

        if (activeBible != null) {
            val allKeys = activeBible.globalKeyList
            var randomKey: org.crosswire.jsword.passage.Key
            do {
                val randomNumber = (0 until allKeys.cardinality).random()
                randomKey = allKeys.get(randomNumber)
            } while (randomKey !is Verse || randomKey.verse == 0)

            val mainVerse = randomKey as Verse
            val v11n = activeBible.versification
            val ordinal = v11n.getOrdinal(mainVerse)
            val prevVerse = v11n.decodeOrdinal(ordinal - 1)
            val nextVerse = v11n.decodeOrdinal(ordinal + 1)

            val mainVerseText = processXml(XMLUtil.writeToString(BookData(activeBible, mainVerse).saxEventProvider), verseNumbers=true)

            val prevVerseText = if (prevVerse != null && prevVerse != mainVerse) {
                processXml(XMLUtil.writeToString(BookData(activeBible, prevVerse).saxEventProvider), verseNumbers = true)
            } else {
                ""
            }

            val nextVerseText = if (nextVerse != null && nextVerse != mainVerse) {
                processXml(XMLUtil.writeToString(BookData(activeBible, nextVerse).saxEventProvider), verseNumbers=true)
            } else {
                ""
            }

            // Build the HTML string
            val builder = StringBuilder()
            if (prevVerseText.isNotEmpty()) {
                builder.append("<font color='#808080'>").append(prevVerseText).append("</font>")
            }
            builder.append(mainVerseText)
            if (nextVerseText.isNotEmpty()) {
                builder.append("<font color='#808080'>").append(nextVerseText).append("</font>")
            }

            verseText = builder.toString()
            verseRef = mainVerse.name
        } else {
            verseText = "context.getString(R.string.no_bibles_installed)"
            verseRef = "context.getString(R.string.no_bibles_installed)"
        }

        // Store the verse text for the factory to retrieve
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        prefs.edit().putString("$PREF_PREFIX_KEY$appWidgetId", verseText).apply()

        // Create RemoteViews for the partial update of the reference
        val partialViews = RemoteViews(context.packageName, R.layout.random_verse_widget)
        partialViews.setTextViewText(R.id.verse_reference, verseRef)
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

    private var verseText: CharSequence = "Loading..."
    private val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        Log.i(TAG, "onDataSetChanged for widget: $appWidgetId")
        val prefs = context.getSharedPreferences(RandomVerseWidget.PREFS_NAME, 0)
        val verseHtml = prefs.getString(
            "${RandomVerseWidget.PREF_PREFIX_KEY}$appWidgetId",
            "context.getString(R.string.loading_text)"
        ) ?: "context.getString(R.string.loading_text)"
        // FromHtml is needed to render the font tags correctly
        verseText = Html.fromHtml(verseHtml, Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onDestroy() {}

    override fun getCount(): Int = 1

    override fun getViewAt(position: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.random_verse_widget_view_item).apply {
            setTextViewText(R.id.verse_text_item, verseText)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    companion object {
        private const val TAG = "VerseRemoteViewsFactory"
    }
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
