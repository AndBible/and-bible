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

package net.bible.android.control;

import net.bible.android.common.resource.AndroidResourceProvider;
import net.bible.android.common.resource.ResourceProvider;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.download.DownloadQueue;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;
import net.bible.android.control.page.window.WindowControl;
import net.bible.service.download.RepoFactory;
import net.bible.service.font.FontControl;
import net.bible.service.sword.SwordDocumentFacade;

import java.util.concurrent.Executors;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module to create application scoped dependencies
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@Module
public class ApplicationModule {

	@Provides
	@ApplicationScope
	public DownloadControl provideDownloadControl(SwordDocumentFacade swordDocumentFacade, RepoFactory repoFactory) {
		return new DownloadControl(new DownloadQueue(Executors.newSingleThreadExecutor(), repoFactory), repoFactory, FontControl.getInstance(), swordDocumentFacade);
	}

	@Provides
	@ApplicationScope
	public ActiveWindowPageManagerProvider provideActiveWindowPageManagerProvider(WindowControl windowControl) {
		return windowControl;
	}

	@Provides
	@ApplicationScope
	public ResourceProvider provideResourceProvider(AndroidResourceProvider androidResourceProvider) {
		return androidResourceProvider;
	}

	@Provides
	@ApplicationScope
	public EventManager eventManagerProvider() {
		return ABEventBus.getDefault();
	}
}
