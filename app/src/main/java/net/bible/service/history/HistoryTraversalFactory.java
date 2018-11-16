package net.bible.service.history;

import net.bible.android.control.ApplicationScope;

import javax.inject.Inject;

/**
 * Each Activity must have its own HistoryTraversal instance, and to get it they use this factory.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
public class HistoryTraversalFactory {

	private final HistoryManager historyManager;

	@Inject
	public HistoryTraversalFactory(HistoryManager historyManager) {
		this.historyManager = historyManager;
	}

	public HistoryTraversal createHistoryTraversal(boolean integrateWithHistoryManager) {
		return new HistoryTraversal(historyManager, integrateWithHistoryManager);
	}
}
