package net.bible.android.view.activity.base.toolbar;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;

/** Manages a set of buttons suitable for the standard screens
 * 
 * @author denha1m
 *
 */
public class DefaultToolbarConfig implements ToolbarConfig {

	private List<ToolbarButton> buttons;
	
	public DefaultToolbarConfig(Context context) {
		buttons = new ArrayList<ToolbarButton>();
		buttons.add(new QuickCommentaryButton(context));
	}
	
	@Override
	public List<ToolbarButton> getButtons() {
		return buttons;
	}

}
