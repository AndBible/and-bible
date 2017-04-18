package net.bible.service.download;

import net.bible.android.control.ApplicationScope;

import org.crosswire.jsword.book.Book;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class RepoFactory {
	private CrosswireRepo crosswireRepo = new CrosswireRepo();
	
	private XiphosRepo xiphosRepo = new XiphosRepo();

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
		} else if (xiphosRepo.getRepoName().equals(repoName)) {
			repoForBook = xiphosRepo;
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
	public XiphosRepo getXiphosRepo() {
		return xiphosRepo;
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
