package net.bible.android.view.activity;

import net.bible.android.control.ControllerModule;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.android.view.activity.page.MenuCommandHandler;

import javax.inject.Singleton;

import dagger.Component;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@Singleton
@Component(modules=ControllerModule.class)
public interface ActivityComponent {
	void inject(MainBibleActivity activity);
	void inject(MenuCommandHandler menuCommandHandler);
}
