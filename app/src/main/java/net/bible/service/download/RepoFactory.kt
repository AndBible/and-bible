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
package net.bible.service.download

import net.bible.android.control.ApplicationScope
import org.crosswire.jsword.book.Book
import javax.inject.Inject

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
open class RepoFactory @Inject constructor() {
    val crosswireRepo = CrosswireRepo()
    val betaRepo = BetaRepo()
    val andBibleRepo = AndBibleRepo()
    val extraRepo = AndBibleExtraRepo()
    val IBTRepo = IBTRepo()
    val eBibleRepo = EBibleRepo()
    val lockmanRepo = LockmanRepo()

    fun getRepoForBook(document: Book): RepoBase {
        return getRepo(document.getProperty(DownloadManager.REPOSITORY_KEY))
    }

    private fun getRepo(repoName: String): RepoBase =
        when (repoName){
            crosswireRepo.repoName -> crosswireRepo
            andBibleRepo.repoName -> andBibleRepo
            extraRepo.repoName -> extraRepo
            betaRepo.repoName -> betaRepo
            IBTRepo.repoName -> IBTRepo
            eBibleRepo.repoName -> eBibleRepo
            lockmanRepo.repoName -> lockmanRepo
            else -> crosswireRepo
        }
}
