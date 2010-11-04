package net.bible.android.view.activity.base;

public interface AndBibleActivity {
	public void showErrorMsg(int msgResId);
    public void showErrorMsg(String msg);
 
	public void dialogOnClick(int dialogId, int id);
}
