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

import net.bible.service.sword.SwordDocumentFacade;

import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

/**
 * Support initialisation as i) do it now or ii) do this eventually
 */
@ApplicationScope
public class WarmUp {

	private boolean isInitialised = false;
	
	private static final long INITIALISE_DELAY = 3000;

	private SwordDocumentFacade swordDocumentFacade;

	@Inject
	public WarmUp(SwordDocumentFacade swordDocumentFacade) {
		this.swordDocumentFacade = swordDocumentFacade;
	}

	/**
	 * Allow Splash screen to be displayed if starting from scratch, otherwise, if returning to an Activity then ensure all initialisation occurs eventually. 
	 */
	public void warmUpSwordEventually() {
		TimerTask timerTask = new TimerTask() {
			public void run() {
				warmUpSwordNow();
			}
		};

		Timer timer = new Timer();
		timer.schedule(timerTask, INITIALISE_DELAY);
	}

	/**
	 * Call any init routines that must be called at least once near the start of running the app e.g. start HistoryManager
	 */
	public synchronized void warmUpSwordNow() {
		if (!isInitialised) {
	        // force Sword to initialise itself
	        swordDocumentFacade.getBibles();

	        isInitialised = true;
		}
	}
}
