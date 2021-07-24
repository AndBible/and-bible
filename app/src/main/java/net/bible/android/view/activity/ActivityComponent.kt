/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.activity

import dagger.Component
import net.bible.android.activity.SpeakWidgetManager
import net.bible.android.control.ApplicationComponent
import net.bible.android.control.page.ClientBookmark
import net.bible.android.control.readingplan.ReadingStatus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.bookmark.Bookmarks
import net.bible.android.view.activity.bookmark.LabelEditActivity
import net.bible.android.view.activity.bookmark.ManageLabels
import net.bible.android.view.activity.download.DownloadActivity
import net.bible.android.view.activity.download.ProgressStatus
import net.bible.android.view.activity.navigation.*
import net.bible.android.view.activity.navigation.genbookmap.ChooseKeyBase
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.readingplan.DailyReading
import net.bible.android.view.activity.readingplan.DailyReadingList
import net.bible.android.view.activity.readingplan.ReadingPlanSelectorList
import net.bible.android.view.activity.search.Search
import net.bible.android.view.activity.search.SearchIndex
import net.bible.android.view.activity.search.SearchIndexProgressStatus
import net.bible.android.view.activity.search.SearchResults
import net.bible.android.view.activity.settings.ColorSettingsActivity
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.GeneralSpeakActivity
import net.bible.android.view.activity.speak.SpeakSettingsActivity
import net.bible.android.view.activity.workspaces.WorkspaceSelectorActivity
import net.bible.android.view.util.widget.ShareWidget
import net.bible.android.view.util.widget.SpeakTransportWidget
import net.bible.service.common.CommonUtilsBase
import net.bible.service.device.speak.TextToSpeechNotificationManager

/**
 * Dagger Component to allow injection of dependencies into activities.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ActivityScope
@Component(dependencies = [ApplicationComponent::class])
interface ActivityComponent {
    // Activities that are permitted to be injected

    // don't like this but inject is called from ActivityBase and the subclasses
    fun inject(activityBase: ActivityBase)
    fun inject(activity: LabelEditActivity)
    fun inject(activity: TextDisplaySettingsActivity)

    fun inject(colorSettings: ColorSettingsActivity)
    fun inject(activity: WorkspaceSelectorActivity)

    fun inject(startupActivity: StartupActivity)

    fun inject(bookmarks: Bookmarks)
    fun inject(manageLabels: ManageLabels)

    fun inject(gridChoosePassageBook: GridChoosePassageBook)
    fun inject(gridChoosePassageChapter: GridChoosePassageChapter)
    fun inject(gridChoosePassageVerse: GridChoosePassageVerse)
    fun inject(chooseDictionaryWord: ChooseDictionaryWord)
    fun inject(chooseKeyBase: ChooseKeyBase)

    fun inject(chooseDocument: ChooseDocument)
    fun inject(chooseDocument: ShareWidget)
    fun inject(download: DownloadActivity)

    fun inject(speak: GeneralSpeakActivity)
    fun inject(speakBible: BibleSpeakActivity)
    fun inject(speakSettings: SpeakSettingsActivity)
    fun inject(dailyReading: DailyReading)
    fun inject(dailyReadingList: DailyReadingList)
    fun inject(readingPlanSelectorList: ReadingPlanSelectorList)
    fun inject(readingStatus: ReadingStatus)
    fun inject(sel: Selection)
    fun inject(searchIndex: SearchIndex)
    fun inject(w: SpeakTransportWidget)
    fun inject(search: Search)
    fun inject(searchResults: SearchResults)
    fun inject(history: History)

    // Services
    fun inject(m: TextToSpeechNotificationManager)
    fun inject(w: SpeakWidgetManager)
    fun inject(c: CommonUtilsBase)

    // progress status screens
    fun inject(searchIndexProgressStatus: SearchIndexProgressStatus)
    fun inject(progressStatus: ProgressStatus)
    fun inject(clientBookmark: ClientBookmark)
}
