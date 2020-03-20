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

package net.bible.android.view.activity;

import net.bible.android.view.activity.page.MainBibleActivity;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger module to create MainBibleActivity related dependencies
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@Module
public class MainBibleActivityModule {

	private final MainBibleActivity mainBibleActivity;

	public MainBibleActivityModule(MainBibleActivity mainBibleActivity) {
		this.mainBibleActivity = mainBibleActivity;
	}

	@Provides @MainBibleActivityScope
	MainBibleActivity provideMainBibleActivity() {
		return mainBibleActivity;
	}
}
