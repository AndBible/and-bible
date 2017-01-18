package net.bible.android.view.activity;

import dagger.Component;

/**
 * Dagger Component to allow injection of dependencies into activities.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@Component(modules = BibleViewModule.class, dependencies = MainBibleActivityComponent.class)
@BibleViewScope
public interface BibleViewComponent {
	// Activities that are permitted to be injected

}
