package cn.wq.myandroidtoolspro.recyclerview.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.wq.myandroidtoolspro.R;
import cn.wq.myandroidtoolspro.component.ComponentListLoader;
import cn.wq.myandroidtoolspro.helper.Utils;
import cn.wq.myandroidtoolspro.model.ActionEntry;
import cn.wq.myandroidtoolspro.recyclerview.base.SearchRecyclerListFragment;

public class ActionRecyclerListFragment extends SearchRecyclerListFragment
        implements LoaderManager.LoaderCallbacks<List<ActionEntry>> {
    private ActionAdapter actionAdapter;
    private static boolean isAllShown;
    private Context mContext;
    private boolean viewReady;
    private boolean started;

    @Override public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (actionAdapter == null) actionAdapter = new ActionAdapter(mContext);
        setAdapter(actionAdapter);
        viewReady = true;
        started = false;
        startWhenVisible();
    }

    @Override public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        if (!visible) {
            started = false;
            if (viewReady && isAdded() && getLoaderManager().getLoader(0) != null) {
                getLoaderManager().getLoader(0).cancelLoad();
            }
        } else if (viewReady && !started) {
            startWhenVisible();
        }
    }

    @Override public void onResume() {
        super.onResume();
        startWhenVisible();
    }

    @Override public void onPause() {
        started = false;
        super.onPause();
    }

    @Override public void onDestroyView() {
        viewReady = false;
        started = false;
        super.onDestroyView();
    }

    private void init() {
        if (!viewReady || !isAdded() || !getUserVisibleHint()) return;
        started = true;
        setListShown(false, true);
        if (getLoaderManager().getLoader(0) == null) getLoaderManager().initLoader(0, null, this);
        else getLoaderManager().restartLoader(0, null, this);
    }

    private void startWhenVisible() {
        if (getView() != null) getView().post(() -> {
            if (viewReady && !started && getUserVisibleHint()) init();
        });
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, R.id.component_refresh, 0, R.string.component_refresh);

        final MenuItem searchMenuItem=menu.findItem(R.id.search);
        searchMenuItem.setVisible(isAllShown);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        searchView.setQueryHint(getString(R.string.search_by_action_name));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (actionAdapter != null) {
                    actionAdapter.getFilter().filter(newText);
                }
                return true;
            }
        });

        MenuItem item=menu.add(0, 0, 1, R.string.see_all_actions);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        MenuItemCompat.setActionView(item, R.layout.toggle_name);

        TextView textView=(TextView) MenuItemCompat.getActionView(item);
        textView.setText(R.string.see_all_actions);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                isAllShown = !isAllShown;
                searchMenuItem.setVisible(isAllShown);
                init();
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.component_refresh) { init(); return true; }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<List<ActionEntry>> onCreateLoader(int arg0, Bundle arg1) {
        return new ActionsLoader(mContext, isAllShown);
    }

    @Override
    public void onLoadFinished(Loader<List<ActionEntry>> arg0,
                               List<ActionEntry> list) {
        if (!viewReady || getView() == null) return;
        if (list == null) {
            setEmptyText(getString(R.string.component_load_failed));
            actionAdapter.setList(java.util.Collections.emptyList(), isAllShown);
        } else {
            setEmptyText(getString(R.string.empty));
            actionAdapter.setList(list,isAllShown);
        }
        if (isResumed()) {
            setListShown(true,true);
        } else {
            setListShown(true,false);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<ActionEntry>> arg0) {
        actionAdapter.setList(null,isAllShown);
    }

    private static class ActionsLoader extends AsyncTaskLoader<List<ActionEntry>> {
        private List<ActionEntry> actions;
        private final boolean all;

        ActionsLoader(Context context, boolean all) {
            super(context.getApplicationContext());
            this.all = all;
        }

        @Override public List<ActionEntry> loadInBackground() {
            try {
                return ComponentListLoader.get(getContext()).loadActions(all, this::isLoadInBackgroundCanceled);
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception failure) {
                Utils.err("ReceiverActions", "Cannot build receiver actions", failure);
                return null;
            }
        }

        @Override public void deliverResult(List<ActionEntry> result) {
            if (isReset()) return;
            actions = result;
            if (isStarted()) super.deliverResult(result);
        }

        @Override protected void onStartLoading() {
            if (actions != null) deliverResult(actions);
            if (actions == null || takeContentChanged()) forceLoad();
        }

        @Override protected void onStopLoading() { cancelLoad(); }
        @Override protected void onReset() { super.onReset(); cancelLoad(); actions = null; }
    }
    private class ActionAdapter extends RecyclerView.Adapter<VHolder> implements Filterable {
        private List<ActionEntry> list;
        private String[] actions;
        private boolean isAllShown;
        private List<ActionEntry> originalData;
        private final Object mLock = new Object();
        private ActionFilter mFilter;

        public ActionAdapter(Context context) {
            super();
            list = new ArrayList<>();
            actions = context.getResources().getStringArray(
                    R.array.ALL_ACTIONS_SUMMARY);
            originalData = new ArrayList<>();
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new ActionFilter();
            }
            return mFilter;
        }

        private class ActionFilter extends Filter{
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                final FilterResults results = new FilterResults();
                List<ActionEntry> tempList;

                if (TextUtils.isEmpty(constraint)) {
                    synchronized (mLock) {
                        tempList = new ArrayList<>(originalData);
                    }
                    results.values = tempList;
                    results.count = tempList.size();
                }else{
                    synchronized (mLock) {
                        tempList = new ArrayList<>(originalData);
                    }
                    final List<ActionEntry> newValues = new ArrayList<>();
                    String lowercaseQuery = constraint.toString().toLowerCase();
                    for (ActionEntry entry : tempList) {
                        if (entry.actionName.toLowerCase().contains(lowercaseQuery)) {
                            newValues.add(entry);
                        }
                    }

                    results.values = newValues;
                    results.count = newValues.size();
                }

                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                list = (List<ActionEntry>) results.values;
                notifyDataSetChanged();
            }
        }

        public void setList(List<ActionEntry> list,boolean isAllShown) {
            this.isAllShown=isAllShown;

            this.list.clear();
            if (list != null) {
                this.list.addAll(list);
            }

            synchronized (mLock) {
                originalData.clear();
                if (list != null) {
                    originalData.addAll(list);
                }
            }
            notifyDataSetChanged();
        }

        public void updateLine(int position,ActionEntry entry){
            list.set(position, entry);
            for (ActionEntry actionEntry : originalData) {
                if (TextUtils.equals(entry.actionName, actionEntry.actionName)) {
                    actionEntry.disabledNum=entry.disabledNum;
                }
            }
            notifyItemChanged(position);
        }
        public Object getItem(int position) {
            return list.get(position);
        }

        @Override
        public void onBindViewHolder(VHolder holder, int position) {
            ActionEntry entry = list.get(position);

            holder.name.setText(isAllShown?entry.actionName:actions[position]);
            holder.name.setTextSize(isAllShown?14:16);

            holder.totalNum.setText(Integer.toString(entry.totalNum));

            if (entry.disabledNum == 0) {
                holder.disableNum.setVisibility(View.INVISIBLE);
            } else {
                holder.disableNum.setVisibility(View.VISIBLE);
                holder.disableNum.setText(Integer.toString(entry.disabledNum));
            }
        }

        @Override
        public VHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receiver_action_list,parent,false));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

    }

    private class VHolder extends RecyclerView.ViewHolder{
        TextView name;
        TextView totalNum;
        TextView disableNum;

        public VHolder(View itemView) {
            super(itemView);

            name = (TextView) itemView.findViewById(R.id.name);
            totalNum = (TextView) itemView
                    .findViewById(R.id.total_num);
            disableNum = (TextView) itemView
                    .findViewById(R.id.disabled_num);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int position=getAdapterPosition();
                    if (position == RecyclerView.NO_POSITION || position >= actionAdapter.getItemCount()) return;
                    ActionEntry action = (ActionEntry) actionAdapter.getItem(position);
                    if (action.totalNum == 0) {
                        return;
                    }

                    FragmentTransaction ft = getActivity().getSupportFragmentManager()
                            .beginTransaction();

                    ft.replace(R.id.content,
                            ReceiverWithActionParentFragment.newInstance(action.actionName));
                    ft.setTransition(FragmentTransaction.TRANSIT_NONE);
                    ft.addToBackStack(null);
                    ft.commit();
                }
            });

        }
    }

}
