package net.bible.android.view.activity.search

import android.app.Activity
import android.content.Intent
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.ReadingSearchRequest
import org.crosswire.jsword.book.Book

/** Opens the reading search sheet, retaining the requested document as its target. */
fun searchIntent(document: Book?, activity: Activity): Intent? =
    NavHostComposeActivity.intentFor(activity, NavRoutes.readingSearch(ReadingSearchRequest(documentId = document?.initials)))
