package net.bible.android.control.bookmark;

import android.graphics.Color;

/**
 * How to represent bookmarks
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
public enum BookmarkStyle {
	YELLOW_STAR(Color.argb(0, 255, 255, 255)),
	RED_HIGHLIGHT(Color.argb((int)(255*0.28), 213, 0, 0)),
	YELLOW_HIGHLIGHT(Color.argb((int)(255*0.33), 255, 255, 0)),
	GREEN_HIGHLIGHT(Color.argb((int)(255*0.33), 0, 255, 0)),
	BLUE_HIGHLIGHT(Color.argb((int)(255*0.33), 145, 167, 255));

	private final int backgroundColor;

	BookmarkStyle(int backgroundColor) {
		this.backgroundColor = backgroundColor;
	}

	public int getBackgroundColor() {
		return backgroundColor;
	}
}
