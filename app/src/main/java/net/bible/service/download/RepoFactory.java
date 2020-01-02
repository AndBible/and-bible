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

package net.bible.service.download;

import net.bible.android.control.ApplicationScope;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
public class RepoFactory {
	private CrosswireRepo crosswireRepo = new CrosswireRepo();
	
	private BetaRepo betaRepo = new BetaRepo();

	private AndBibleRepo andBibleRepo = new AndBibleRepo();

	private IBTRepo ibtRepo = new IBTRepo();
	
	private EBibleRepo eBibleRepo = new EBibleRepo();

	@Inject
	public RepoFactory() {}

	public RepoBase getRepoForBook(Book document) {
		return getRepo(document.getProperty(DownloadManager.REPOSITORY_KEY));
	}

	private RepoBase getRepo(String repoName) {

		RepoBase repoForBook;
		if (crosswireRepo.getRepoName().equals(repoName)) {
			repoForBook = crosswireRepo;
		} else if (andBibleRepo.getRepoName().equals(repoName)) {
			repoForBook = andBibleRepo;
		} else if (betaRepo.getRepoName().equals(repoName)) {
			repoForBook = betaRepo;
		} else if (ibtRepo.getRepoName().equals(repoName)) {
			repoForBook = ibtRepo;
		} else if (eBibleRepo.getRepoName().equals(repoName)) {
			repoForBook = eBibleRepo;
		} else {
			repoForBook = crosswireRepo;
		}
		return repoForBook;
	}

	public CrosswireRepo getCrosswireRepo() {
		return crosswireRepo;
	}
	public BetaRepo getBetaRepo() {
		return betaRepo;
	}
	public AndBibleRepo getAndBibleRepo() {
		return andBibleRepo;
	}
	public IBTRepo getIBTRepo() {
		return ibtRepo;
	}
	public EBibleRepo getEBibleRepo() {
		return eBibleRepo;
	}
}
