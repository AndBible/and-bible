package net.bible.service.download;

import org.crosswire.jsword.book.Book;

public class RepoFactory {
	private CrosswireRepo crosswireRepo = new CrosswireRepo();
	
	private CrosswireAVRepo crosswireAVRepo = new CrosswireAVRepo();

	private XiphosRepo xiphosRepo = new XiphosRepo();

	private BetaRepo betaRepo = new BetaRepo();

	private AndBibleRepo andBibleRepo = new AndBibleRepo();

	private IBTRepo ibtRepo = new IBTRepo();
	
	private static RepoFactory instance = new RepoFactory();
	private RepoFactory() {}
	public static RepoFactory getInstance() {
		return instance;
	}
	

	public RepoBase getRepoForBook(Book document) {
		String repoName = (String)document.getProperty(DownloadManager.REPOSITORY_KEY);

		RepoBase repoForBook;
		if (crosswireRepo.getRepoName().equals(repoName)) {
			repoForBook = crosswireRepo;
		} else if (crosswireAVRepo.getRepoName().equals(repoName)) {
			repoForBook = crosswireAVRepo;
		} else if (xiphosRepo.getRepoName().equals(repoName)) {
			repoForBook = xiphosRepo;
		} else if (andBibleRepo.getRepoName().equals(repoName)) {
			repoForBook = andBibleRepo;
		} else if (betaRepo.getRepoName().equals(repoName)) {
			repoForBook = betaRepo;
		} else if (ibtRepo.getRepoName().equals(repoName)) {
			repoForBook = ibtRepo;
		} else {
			repoForBook = crosswireRepo;
		}
		return repoForBook;
	}
	public CrosswireRepo getCrosswireRepo() {
		return crosswireRepo;
	}
	public CrosswireAVRepo getCrosswireAVRepo() {
		return crosswireAVRepo;
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
}
