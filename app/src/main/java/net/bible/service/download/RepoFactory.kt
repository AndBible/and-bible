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
package net.bible.service.download

import org.crosswire.jsword.book.Book

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class RepoFactory(val downloadManager: DownloadManager) {
    private val defaultRepo = AndBibleRepo()

    // In priority order (if the same version of module is found in many, it will be picked up
    // from the earlier of the repository list).
    val normalRepositories = listOf(
        defaultRepo, CrosswireRepo(), EBibleRepo(), LockmanRepo(), WycliffeRepo(), AndBibleExtraRepo(), IBTRepo(), StepRepo()
    )

    val betaRepositories = listOf(CrosswireBetaRepo(), AndBibleBetaRepo())

    val repositories = normalRepositories + betaRepositories

    init {
        for(r in repositories) {
            r.repoFactory = this
        }
    }

    fun getRepoForBook(document: Book): RepoBase {
        return getRepo(document.getProperty(DownloadManager.REPOSITORY_KEY))
    }

    private fun getRepo(repoName: String): RepoBase =
        repositories.find { it.repoName == repoName } ?: defaultRepo
}
