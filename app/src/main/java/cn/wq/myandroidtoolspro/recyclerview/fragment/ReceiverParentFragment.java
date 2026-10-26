package cn.wq.myandroidtoolspro.recyclerview.fragment;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import cn.wq.myandroidtoolspro.R;
import cn.wq.myandroidtoolspro.component.ComponentListLoader;
import cn.wq.myandroidtoolspro.recyclerview.toolbar.BaseFragment;

public class ReceiverParentFragment extends BaseFragment implements ComponentListLoader.Host {
    private ComponentListLoader.Session session;
    private boolean resumedBefore;

    @Override public ComponentListLoader.Session getComponentSession() {
        if (session == null) session = ComponentListLoader.get(getContext()).newSession(1);
        return session;
    }

    @Override public void onDestroyView() {
        if (session != null) session.close();
        session = null;
        super.onDestroyView();
    }

    @Override public void onResume() {
        super.onResume();
        if (resumedBefore && session != null) session.refresh();
        resumedBefore = true;
    }
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
        initActionbar(0,getString(R.string.broadcast_receiver));
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
        View rootView=inflater.inflate(R.layout.fragment_component_parent, container, false);
        resumedBefore = false;
        ViewPager mViewPager = (ViewPager) rootView.findViewById(R.id.view_pager);
        mViewPager.setAdapter(new MyPagerAdapter(this));
        mViewPager.setCurrentItem(1);
        mViewPager.setOffscreenPageLimit(2);

        TabLayout mTabLayout = (TabLayout) rootView.findViewById(R.id.tab_layout);
        mTabLayout.setupWithViewPager(mViewPager);
        return rootView;
	}

	private static class MyPagerAdapter extends FragmentPagerAdapter {
		private String[] titles;

		public MyPagerAdapter(Fragment fragment) {
			super(fragment.getChildFragmentManager());
			titles = fragment.getResources().getStringArray(
					R.array.receiver_fragment_title);
		}

		@Override
		public Fragment getItem(int position) {
			if (position == 0) {
                return new ActionRecyclerListFragment();
            } else {
                return AppForComponentRecyclerFragment.newInstance(position == 2, 1);
            }
		}

		@Override
		public int getCount() {
			return titles.length;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			return titles[position];
		}
	}

	// ------------------------
	//




}
