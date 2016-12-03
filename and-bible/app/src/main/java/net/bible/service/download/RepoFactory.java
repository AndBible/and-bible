package net.bible.service.download;

import org.crosswire.jsword.book.Book;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class RepoFactory {
	private CrosswireRepo crosswireRepo = new CrosswireRepo();
	
	private XiphosRepo xiphosRepo = new XiphosRepo();

	private BetaRepo betaRepo = new BetaRepo();

	private AndBibleRepo andBibleRepo = new AndBibleRepo();

	private IBTRepo ibtRepo = new IBTRepo();
	
	private WycliffeRepo wycliffeRepo = new WycliffeRepo();

	private EBibleRepo eBibleRepo = new EBibleRepo();

	private static RepoFactory instance = new RepoFactory();
	private RepoFactory() {}
	public static RepoFactory getInstance() {
		return instance;
	}

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
		} else if (wycliffeRepo.getRepoName().equals(repoName)) {
			repoForBook = wycliffeRepo;
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
	public WycliffeRepo getWycliffeRepo() {
		return wycliffeRepo;
	}
	public EBibleRepo getEBibleRepo() {
		return eBibleRepo;
	}
}
