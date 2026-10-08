/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.control.document.DocumentChanges
import net.bible.android.view.activity.nav.SystemBarSettingChanges
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.device.ScreenSettings
import android.content.res.Resources
import android.util.Log
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakSettingsChanges
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.service.cloudsync.CloudSync
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.common.AiSettings
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.installzip.InstallZipProgress
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.sword.mydocument.MyDocumentBookManager
import org.koin.core.context.GlobalContext

/**
 * Override settings if required
 */

const val TEST_SDK = 33
class TestBibleApplication : BibleApplication() {
    init {
        println("TestBibleApplication BibleApplication subclass being used.")
    }

    override val isRunningTests: Boolean = true

    override fun getLocalizedResources(language: String): Resources = application.resources

    override fun onCreate() {
        super.onCreate()
        CommonUtils.initializeApp()
    }

    /**
     * This is never called in real system (only in tests). See parent documentation.
     */
    override fun onTerminate() {
        CommonUtils.destroy()
        super.onTerminate()
        AgentSessionManager.resetSubscribersForTest()
        SharedActivityState.instance.resetSubscribersForTest()
        GlobalContext.getOrNull()?.getOrNull<WindowStateServiceImpl>()?.resetSubscribersForTest()
        WorkspaceChanges.resetSubscribersForTest()
        AiSettings.resetSubscribersForTest()
        DocumentSync.resetSubscribersForTest()
        CloudSync.resetSubscribersForTest()
        InstallZipProgress.resetSubscribersForTest()
        SpeakChanges.resetSubscribersForTest()
        SpeakSettingsChanges.resetSubscribersForTest()
        DatabaseContainer.resetBookmarksSyncedForTest()
        MyDocumentBookManager.resetSubscribersForTest()
        PassageChangeMediator.resetSubscribersForTest()
        net.bible.service.history.HistoryManager.resetInstanceForTest()
        ScreenSettings.resetSubscribersForTest()
        CurrentActivityHolder.resetSubscribersForTest()
        SystemBarSettingChanges.resetSubscribersForTest()
        DocumentChanges.resetSubscribersForTest()
        DatabaseContainer.resetPhase8StreamsForTest()
        AndBibleAddons.resetSubscribersForTest()
    }
}
