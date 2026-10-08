/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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


package net.bible.service.llm.agent

import kotlinx.coroutines.Job

/**
 * F123: the runs [AgentForegroundService] has launched and not yet seen end. A run that returns at once (lost the
 * session race, opened a cached result) must not stop the service while another run is still going: stopping
 * runs `onDestroy`, whose `scope.cancel()` would kill that run too.
 */
internal class LiveRuns {
    private val live = mutableSetOf<Job>()

    @Synchronized fun add(job: Job) { live += job }

    /** Removes [job]; true when no launched run is left, i.e. the service may stop now. */
    @Synchronized fun finish(job: Job): Boolean {
        live -= job
        return live.isEmpty()
    }
}
