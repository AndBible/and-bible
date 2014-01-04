package net.bible.android.view.activity.page.toolbar;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.page.MenuCommandHandler;
import android.app.Activity;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class NavigationDrawer {

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;
    
    private NavigationDrawerMenuItem[] navigationDrawerMenuItemArray;
    private MenuCommandHandler menuCommandHandler;
	
	private ActionBar actionBar;

	private PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	private static final String TAG = "NavigationDrawer";
	
	public void addToBar(Activity activity, ActionBar actionBar, MenuCommandHandler menuCommandHandler) {
		this.actionBar = actionBar;
		this.menuCommandHandler = menuCommandHandler;
		
		navigationDrawerMenuItemArray = getMenuItems(activity);

		//mPlanetTitles = getResources().getStringArray(R.array.planets_array);
		mDrawerLayout = (DrawerLayout) activity.findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) activity.findViewById(R.id.left_drawer);

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

		// Set the adapter for the list view
		mDrawerList.setAdapter(new ArrayAdapter<NavigationDrawerMenuItem>(activity, R.layout.drawer_list_item, navigationDrawerMenuItemArray));

		// Set the list's click listener
	    mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

	    mDrawerToggle = new ActionBarDrawerToggle(activity,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */) {

            
/** Called when a drawer has settled in a completely closed state. */

            public void onDrawerClosed(View view) {
                //getActionBar().setTitle(title);
            }

            
/** Called when a drawer has settled in a completely open state. */

            public void onDrawerOpened(View drawerView) {
                //actionBar.setTitle("Open Drawer");
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

	    
	    actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		mDrawerToggle.syncState();
	}
	
	/** allows title click to reveal nav drawer
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		// Pass the event to ActionBarDrawerToggle, if it returns
		// true, then it has handled the app icon touch event
		return mDrawerToggle.onOptionsItemSelected(item);
	}

    /* The click listner for ListView in the navigation drawer */
    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            //selectItem(position);
        	Log.i(TAG, "Menu item selected");
        	NavigationDrawerMenuItem selectedDrawerMenuItem = navigationDrawerMenuItemArray[position];
        	menuCommandHandler.handleMenuRequest(selectedDrawerMenuItem.getId());
        	
        	mDrawerLayout.closeDrawer(mDrawerList);
        }
    }

    protected void onPostCreate() {
        // Sync the toggle state after onRestoreInstanceState has occurred.
    	if (mDrawerToggle!=null) {
    		mDrawerToggle.syncState();
    	}
    }

    private NavigationDrawerMenuItem[] getMenuItems(Activity activity) {
    	Menu menu = new MenuBuilder(activity);
    	activity.getMenuInflater().inflate(R.menu.navigation_drawer, menu);
    	
    	List<NavigationDrawerMenuItem> navigationDrawerMenuItems = new ArrayList<NavigationDrawerMenuItem>();
    	for (int i=0; i<menu.size(); i++) {
        	MenuItem menuItem = menu.getItem(i);
        	NavigationDrawerMenuItem drawerMenuItem = new NavigationDrawerMenuItem(menuItem);
        	navigationDrawerMenuItems.add(drawerMenuItem);
    	}
    	return navigationDrawerMenuItems.toArray(new NavigationDrawerMenuItem[navigationDrawerMenuItems.size()]);
    }

//    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//        mDrawerToggle.onConfigurationChanged(newConfig);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Pass the event to ActionBarDrawerToggle, if it returns
//        // true, then it has handled the app icon touch event
//        if (mDrawerToggle.onOptionsItemSelected(item)) {
//          return true;
//        }
//        // Handle your other action bar items...
//
//        return super.onOptionsItemSelected(item);
//    }
}
