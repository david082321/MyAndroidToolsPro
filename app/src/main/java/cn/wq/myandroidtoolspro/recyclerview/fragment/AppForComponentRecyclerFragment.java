package cn.wq.myandroidtoolspro.recyclerview.fragment;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.wq.myandroidtoolspro.BuildConfig;
import cn.wq.myandroidtoolspro.BaseActivity;
import cn.wq.myandroidtoolspro.R;
import cn.wq.myandroidtoolspro.component.ComponentListLoader;
import cn.wq.myandroidtoolspro.helper.Utils;
import cn.wq.myandroidtoolspro.model.AppEntry;
import cn.wq.myandroidtoolspro.recyclerview.base.RecyclerListView;
import cn.wq.myandroidtoolspro.recyclerview.base.SearchRecyclerListFragment;

public class AppForComponentRecyclerFragment extends SearchRecyclerListFragment
        implements RecyclerListView.OnRecyclerItemClickListener {
    private boolean isSystem;
    private int type;
    private ComponentAdapter mAdapter;
    private SortChangeReceiver mChangeReceiver;
    private Context mContext;
    private ComponentListLoader.Session session;
    private boolean ownsSession;
    private Runnable unsubscribe;
    private int viewGeneration;
    private String queryBefore = "";
    private Parcelable listState;
    private boolean showIfwCounts;

    @Override public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    public static AppForComponentRecyclerFragment newInstance(boolean isSystem, int type) {
        AppForComponentRecyclerFragment fragment = new AppForComponentRecyclerFragment();
        Bundle args = new Bundle();
        args.putBoolean("isSystem", isSystem);
        args.putInt("type", type);
        fragment.setArguments(args);
        return fragment;
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        isSystem = getArguments().getBoolean("isSystem");
        type = getArguments().getInt("type");
        showIfwCounts = type != 3;
        if (mAdapter == null) mAdapter = new ComponentAdapter();
        setAdapter(mAdapter);
        if (savedInstanceState != null) {
            queryBefore = savedInstanceState.getString("componentQuery", queryBefore);
            listState = savedInstanceState.getParcelable("componentScroll");
        }
        Fragment parent = getParentFragment();
        ownsSession = !(parent instanceof ComponentListLoader.Host);
        session = ownsSession ? ComponentListLoader.get(mContext).newSession(type)
                : ((ComponentListLoader.Host) parent).getComponentSession();
        final int generation = ++viewGeneration;
        setListShown(false, false);
        unsubscribe = session.observe(result -> {
            if (generation != viewGeneration || !isAdded() || getView() == null) return;
            if (result.error != null) {
                Utils.err("ComponentList", "Cannot load component list", result.error);
                setEmptyText(getString(R.string.component_load_failed));
                if (getUserVisibleHint()) Toast.makeText(mContext, R.string.component_load_failed, Toast.LENGTH_LONG).show();
            } else {
                setEmptyText(getString(R.string.empty));
                mAdapter.setData(result.apps(isSystem));
                if (result.partial && getUserVisibleHint()) {
                    Toast.makeText(mContext, R.string.component_load_partial, Toast.LENGTH_LONG).show();
                }
            }
            setListShown(true, isResumed());
            if (listState != null && getRecyclerListView() != null) {
                getRecyclerListView().getLayoutManager().onRestoreInstanceState(listState);
                listState = null;
            }
        });
    }

    @Override public void onResume() {
        super.onResume();
        if (type == 0) {
            if (mChangeReceiver == null) mChangeReceiver = new SortChangeReceiver();
            LocalBroadcastManager.getInstance(mContext).registerReceiver(mChangeReceiver,
                    new IntentFilter(Utils.ACTION_SORT_CHANGE));
        }
    }

    @Override public void onStop() {
        if (mChangeReceiver != null) LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mChangeReceiver);
        super.onStop();
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("componentQuery", queryBefore);
        outState.putParcelable("componentScroll", scrollState());
    }

    private Parcelable scrollState() {
        return getRecyclerListView() == null ? listState
                : getRecyclerListView().getLayoutManager().onSaveInstanceState();
    }

    @Override public void onDestroyView() {
        listState = scrollState();
        viewGeneration++;
        if (unsubscribe != null) unsubscribe.run();
        unsubscribe = null;
        if (ownsSession && session != null) session.close();
        session = null;
        searchView = null;
        super.onDestroyView();
    }

    private class SortChangeReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (Utils.ACTION_SORT_CHANGE.equals(intent.getAction()) && mAdapter != null) mAdapter.resort();
        }
    }

    @Override public boolean onQueryTextChange(String query) {
        queryBefore = query;
        if (mAdapter != null) mAdapter.filter();
        return true;
    }

    @Override public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        String query = queryBefore;
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, R.id.component_refresh, 0, R.string.component_refresh);
        if (type == 0) {
            MenuItem item = menu.add(0, R.id.menu_sort_id, 0, R.string.sort_by);
            item.setIcon(R.drawable.ic_sort_white);
            MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        }
        if (!BuildConfig.isFree) menu.findItem(R.id.search_component_in_all_apps).setVisible(true);
        if (!TextUtils.isEmpty(query) && searchView != null) {
            expandSearchView();
            searchView.setQuery(query, false);
        }
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.component_refresh) {
            if (session != null) {
                listState = scrollState();
                setListShown(false, true);
                session.refresh();
            }
            return true;
        }
        if (item.getItemId() == R.id.menu_sort_id) {
            new SortDialogFragment().show(getChildFragmentManager(), "sort");
            return true;
        }
        if (item.getItemId() == R.id.search_component_in_all_apps) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content, SearchComponentInAllFragment.newInstance(type))
                    .setTransition(FragmentTransaction.TRANSIT_NONE).addToBackStack(null).commit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public static class SortDialogFragment extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final SharedPreferences sp = PreferenceManager
                    .getDefaultSharedPreferences(getActivity());
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.sort_by)
                    .setSingleChoiceItems(R.array.sort_service,
                            sp.getInt("sort_service", 0),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    sp.edit().putInt("sort_service", which).apply();
                                    LocalBroadcastManager.getInstance(getActivity())
                                            .sendBroadcast(
                                                    new Intent(
                                                            Utils.ACTION_SORT_CHANGE));
                                    dialog.dismiss();
                                }
                            })
                    .setNegativeButton(R.string.cancel, null).create();
        }

    }

    @Override
    public void onItemClick(int position, View v) {
        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            searchView.clearFocus();    //bug:20170711 关闭输入法，避免进入详细仍然显示输入法
        }

        // FIXME: 2019-04-23 trick，没办法啊
        if (searchView != null) {
            queryBefore = searchView.getQuery().toString();
        }

        if (position < 0 || position >= mAdapter.getItemCount()) return;
        AppEntry entry = mAdapter.getItem(position);
        try {
            mContext.getPackageManager().getPackageInfo(
                    entry.packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(),
                    R.string.app_has_been_uninstalled, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        Bundle args = new Bundle();
        args.putString("packageName", entry.packageName);
        args.putString("title", entry.label);


        Fragment fragment;
        switch (type) {
            case 0:
                fragment = ServiceRecyclerListFragment.newInstance(args);
                break;
            case 1:
                fragment = ReceiverRecyclerListFragment.newInstance(args);
                break;
            case 2:
                fragment = ActivityRecyclerListFragment.newInstance(args);
                break;
            default:
                fragment = ProviderRecyclerListFragment.newInstance(args);
                break;
        }

        FragmentTransaction ft = getActivity().getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.content, fragment);
        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
        ft.addToBackStack(null);
        ft.commit();
    }

    private class VHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView total;
        final TextView disabled;
        final TextView running;
        final TextView ifw;

        VHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            name = view.findViewById(R.id.name);
            total = view.findViewById(R.id.total_num);
            disabled = view.findViewById(R.id.disabled_num);
            running = view.findViewById(R.id.running_num);
            ifw = view.findViewById(R.id.ifw_num);
            boolean dark = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getInt(BaseActivity.PREFERENCE_THEME, 0) > 0;
            ifw.setTextColor(ContextCompat.getColor(mContext, dark
                    ? R.color.component_ifw_count_dark : R.color.component_ifw_count_light));
            view.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) onItemClick(position, v);
            });
        }
    }

    private class ComponentAdapter extends RecyclerView.Adapter<VHolder> {
        private final List<AppEntry> all = new ArrayList<>();
        private final List<AppEntry> visible = new ArrayList<>();
        private final Handler main = new Handler(Looper.getMainLooper());
        private int dataGeneration;

        void setData(List<AppEntry> entries) {
            dataGeneration++;
            all.clear();
            all.addAll(entries);
            filter();
        }

        void resort() {
            final int version = ++dataGeneration;
            final int viewVersion = viewGeneration;
            final List<AppEntry> sorted = new ArrayList<>(all);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                ComponentListLoader.sortApps(mContext, sorted, type);
                main.post(() -> {
                    if (version != dataGeneration || viewVersion != viewGeneration || getView() == null) return;
                    all.clear();
                    all.addAll(sorted);
                    filter();
                });
            });
        }

        void filter() {
            visible.clear();
            Locale locale = mContext.getResources().getConfiguration().locale;
            String query = queryBefore.toLowerCase(locale);
            for (AppEntry entry : all) {
                if (entry.label.toLowerCase(locale).contains(query)) visible.add(entry);
            }
            notifyDataSetChanged();
        }

        AppEntry getItem(int position) { return visible.get(position); }
        @Override public int getItemCount() { return visible.size(); }

        @Override public VHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new VHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_list_for_component, parent, false));
        }

        @Override public void onBindViewHolder(VHolder holder, int position) {
            AppEntry entry = visible.get(position);
            Utils.loadApkIcon(AppForComponentRecyclerFragment.this, entry.packageName, holder.icon);
            holder.name.setText(entry.label);
            holder.total.setText(Integer.toString(entry.totalNum));
            holder.ifw.setVisibility(showIfwCounts ? entry.ifwNum > 0 ? View.VISIBLE : View.INVISIBLE : View.GONE);
            holder.ifw.setText(entry.ifwNum < 0 ? getString(R.string.component_status_unknown)
                    : Integer.toString(entry.ifwNum));
            holder.ifw.setContentDescription(entry.ifwNum < 0 ? getString(R.string.component_ifw_unknown)
                    : getString(R.string.component_ifw_count_description, entry.ifwNum));
            holder.disabled.setVisibility(entry.disabledNum > 0 ? View.VISIBLE : View.INVISIBLE);
            holder.disabled.setText(entry.disabledNum < 0 ? getString(R.string.component_status_unknown)
                    : Integer.toString(entry.disabledNum));
            holder.running.setVisibility(entry.runningNum == 0 ? View.GONE : View.VISIBLE);
            holder.running.setText(entry.runningNum < 0 ? getString(R.string.component_status_unknown)
                    : Integer.toString(entry.runningNum));
        }
    }
}
