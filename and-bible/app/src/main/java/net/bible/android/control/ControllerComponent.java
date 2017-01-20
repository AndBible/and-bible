package net.bible.android.control;

import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.download.DownloadControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.readingplan.ReadingPlanControl;
import net.bible.android.control.speak.SpeakControl;
import net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakActionBarButton;
import net.bible.android.view.activity.speak.actionbarbuttons.SpeakStopActionBarButton;

import dagger.Component;

/**
 * Dagger Component to expose application scoped dependencies.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ApplicationScope
@Component(modules=ControllerModule.class)
public interface ControllerComponent {

	//Exposed to sub-graphs.
	BackupControl backupControl();
	BookmarkControl bookmarkControl();
	DownloadControl downloadControl();
	PageControl pageControl();
	ReadingPlanControl readingPlanControl();

	SpeakControl speakControl();

	SpeakActionBarButton speakActionBarButton();
	SpeakStopActionBarButton speakStopActionBarButton();
	ReadingPlanActionBarManager readingPlanActionBarManager();
}
