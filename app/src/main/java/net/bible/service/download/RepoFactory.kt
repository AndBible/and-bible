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

import androidx.annotation.VisibleForTesting
import net.bible.service.common.CommonUtils
import net.bible.service.sword.AcceptableBookTypeFilter
import org.crosswire.jsword.book.Book

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class RepoFactory(private val downloadManager: DownloadManager) {
    private val andBibleRepo = Repository("AndBible", AcceptableBookTypeFilter(), downloadManager)
    private val andBibleExtraRepo = Repository("AndBible Extra", AcceptableBookTypeFilter(), downloadManager)
    private val andBibleBetaRepo = Repository(
        "AndBible Beta",
        object: AcceptableBookTypeFilter() {
            override fun test(book: Book): Boolean = CommonUtils.isBeta
        }
        , downloadManager
    )

    // see here for info ftp://ftp.xiphos.org/mods.d/
    @VisibleForTesting
    val crosswireRepo = Repository("CrossWire", AcceptableBookTypeFilter(), downloadManager)
    private val lockmanRepo = Repository("Lockman (CrossWire)", AcceptableBookTypeFilter(), downloadManager)
    private val wycliffeRepo = Repository("Wycliffe (CrossWire)", AcceptableBookTypeFilter(), downloadManager)
    private val crosswireBetaRepo = Repository(
        "Crosswire Beta",
        object : AcceptableBookTypeFilter() {
            override fun test(book: Book): Boolean {
                // just Calvin Commentaries for now to see how we go
                //
                // Cannot include Jasher, Jub, EEnochCharles because they are displayed as page per verse for some reason which looks awful.
                if(CommonUtils.isBeta) return true
                return super.test(book) &&
                    book.initials == "CalvinCommentaries"
            }
        }
        , downloadManager
    )

    private val eBibleRepo = Repository("eBible", AcceptableBookTypeFilter(), downloadManager)
    private val stepRepo = Repository("STEP Bible (Tyndale)", AcceptableBookTypeFilter(), downloadManager)
    private val ibtRepo = Repository("IBT", AcceptableBookTypeFilter(), downloadManager)


    private val defaultRepo = andBibleRepo

    // In priority order (if the same version of module is found in many, it will be picked up
    // from the earlier of the repository list).
    private val normalRepositories = listOf(
        defaultRepo, crosswireRepo, eBibleRepo, lockmanRepo, wycliffeRepo, andBibleExtraRepo, ibtRepo, stepRepo,
    )

    private val betaRepositories = listOf(crosswireBetaRepo, andBibleBetaRepo)

    private val customRepositories: List<Repository> get() = downloadManager.customRepositoryDao.all().map {
        Repository(it.name, AcceptableBookTypeFilter(), downloadManager)
    }

    val repositories get() = normalRepositories + betaRepositories + customRepositories

    fun getRepoForBook(document: Book): Repository {
        return getRepo(document.getProperty(DownloadManager.REPOSITORY_KEY))
    }

    private fun getRepo(repoName: String): Repository =
        repositories.find { it.repoName == repoName } ?: defaultRepo
}
