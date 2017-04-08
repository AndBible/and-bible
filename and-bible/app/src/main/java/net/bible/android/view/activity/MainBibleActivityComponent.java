package net.bible.android.view.activity;

import net.bible.android.control.ApplicationComponent;
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
@Component(modules = MainBibleActivityModule.class, dependencies = ApplicationComponent.class)
@MainBibleActivityScope
public interface MainBibleActivityComponent {
	// Activities that are permitted to be injected

	void inject(MainBibleActivity activity);
	void inject(MenuCommandHandler menuCommandHandler);
}
