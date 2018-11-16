package net.bible.android.view.activity.search.searchresultsactionbar;

import android.app.Activity;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.View.OnClickListener;

import net.bible.android.control.ApplicationScope;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.actionbar.DefaultActionBarManager;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
@ApplicationScope
public class SearchResultsActionBarManager extends DefaultActionBarManager implements ActionBarManager {

    private ScriptureToggleActionBarButton scriptureToggleActionBarButton;

    @Inject
    public SearchResultsActionBarManager(ScriptureToggleActionBarButton scriptureToggleActionBarButton) {
        this.scriptureToggleActionBarButton = scriptureToggleActionBarButton;
    }
    
    public void registerScriptureToggleClickListener(OnClickListener scriptureToggleClickListener) {
        scriptureToggleActionBarButton.registerClickListener(scriptureToggleClickListener);
    }
    
    public void setScriptureShown(boolean isScripture) {
        scriptureToggleActionBarButton.setOn(isScripture);
    }
    
    /* (non-Javadoc)
     * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#prepareOptionsMenu(android.app.Activity, android.view.Menu, android.support.v7.app.ActionBar, net.bible.android.view.activity.page.MenuCommandHandler)
     */
    @Override
    public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
        super.prepareOptionsMenu(activity, menu, actionBar);
        
        scriptureToggleActionBarButton.addToMenu(menu);
    }
    
    /* (non-Javadoc)
     * @see net.bible.android.view.activity.page.actionbar.ActionBarManager#updateButtons()
     */
    @Override
    public void updateButtons() {
        super.updateButtons();
        
        // this can be called on end of speech in non-ui thread
        CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                scriptureToggleActionBarButton.update();
            }
        });
    }
}
