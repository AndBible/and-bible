package net.bible.android.view.util;

import android.database.DataSetObserver;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.WrapperListAdapter;

class ListPrefWrapperAdapter implements WrapperListAdapter {
    private ListAdapter mOrigAdapter;

    public ListPrefWrapperAdapter(ListAdapter origAdapter) {
        mOrigAdapter = origAdapter;
    }

    @Override
    public ListAdapter getWrappedAdapter() {
        return mOrigAdapter;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return getWrappedAdapter().areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        return getWrappedAdapter().isEnabled(position);
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        getWrappedAdapter().registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        getWrappedAdapter().unregisterDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        return getWrappedAdapter().getCount();
    }

    @Override
    public Object getItem(int position) {
        return getWrappedAdapter().getItem(position);
    }

    @Override
    public long getItemId(int position) {
        return getWrappedAdapter().getItemId(position);
    }

    @Override
    public boolean hasStableIds() {
        return getWrappedAdapter().hasStableIds();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position==2) {
            convertView.setBackgroundColor(Color.BLUE);
        }
        return getWrappedAdapter().getView(position, convertView, parent);
    }

    @Override
    public int getItemViewType(int position) {
        return getWrappedAdapter().getItemViewType(position);
    }

    @Override
    public int getViewTypeCount() {
        return getWrappedAdapter().getViewTypeCount();
    }

    @Override
    public boolean isEmpty() {
        return getWrappedAdapter().isEmpty();
    }
}