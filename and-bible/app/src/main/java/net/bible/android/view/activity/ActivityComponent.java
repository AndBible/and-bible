package net.bible.android.view.activity;

import net.bible.android.control.ControllerComponent;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.navigation.ChooseDocument;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.android.view.activity.page.MenuCommandHandler;

import dagger.Component;

/**
 * Dagger Component to allow injection of dependencies into activities.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@ActivityScope
@Component(dependencies = ControllerComponent.class)
public interface ActivityComponent {
	// Activities that are permitted to be injected
	void inject(ChooseDocument chooseDocument);
	void inject(Download download);
	void inject(MainBibleActivity activity);
	void inject(MenuCommandHandler menuCommandHandler);
}
