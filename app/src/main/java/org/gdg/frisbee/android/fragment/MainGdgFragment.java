package org.gdg.frisbee.android.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.ActionBar;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockFragment;
import com.viewpagerindicator.TitlePageIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gdg.frisbee.android.activity.MainActivity;
import org.gdg.frisbee.android.adapter.ChapterAdapter;
import org.gdg.frisbee.android.api.ApiRequest;
import org.gdg.frisbee.android.api.GroupDirectory;
import org.gdg.frisbee.android.api.model.Chapter;
import org.gdg.frisbee.android.api.model.Directory;
import org.gdg.frisbee.android.app.App;
import org.gdg.frisbee.android.cache.ModelCache;
import org.gdg.frisbee.android.utils.ChapterComparator;
import org.gdg.frisbee.android.utils.Utils;
import org.joda.time.DateTime;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import roboguice.inject.InjectView;

public class MainGdgFragment extends RoboSherlockFragment implements ActionBar.OnNavigationListener {

    private static final String HOME_GDG = "mgf.HOME_GDG";
    private static final String LOG_TAG = "GDG-MainGdgFragment";

    @InjectView(R.id.pager)
    private ViewPager mViewPager;

    @InjectView(R.id.titles)
    private TitlePageIndicator mIndicator;

    private ApiRequest mFetchChaptersTask;
    private SharedPreferences mPreferences;
    private LocationManager mLocationManager;
    private GroupDirectory mClient;
    private Chapter homeGdg;

    private ChapterAdapter mSpinnerAdapter;
    private MyAdapter mViewPagerAdapter;
    private ChapterComparator mLocationComparator;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_gdg.xml, null);
        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mPreferences = getActivity().getSharedPreferences("gdg", Context.MODE_PRIVATE);

        mClient = new GroupDirectory();

        mLocationComparator = new ChapterComparator(mPreferences);

        mIndicator.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void onPageSelected(int i) {
                Log.d(LOG_TAG, "onPageSelected()");
                trackViewPagerPage(i);
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        });

        mViewPagerAdapter = new MyAdapter(getActivity(), getSherlockActivity().getSupportFragmentManager());
        mSpinnerAdapter = new ChapterAdapter(getActivity(), android.R.layout.simple_list_item_1);

        mFetchChaptersTask = mClient.getDirectory(new Response.Listener<Directory>() {
                                                      @Override
                                                      public void onResponse(final Directory directory) {
                                                          getSherlockActivity().getSupportActionBar().setListNavigationCallbacks(mSpinnerAdapter, MainGdgFragment.this);
                                                          App.getInstance().getModelCache().putAsync("chapter_list", directory, DateTime.now().plusDays(1), new ModelCache.CachePutListener() {
                                                              @Override
                                                              public void onPutIntoCache() {
                                                                  ArrayList<Chapter> chapters = directory.getGroups();

                                                                  initChapters(chapters);
                                                              }
                                                          });
                                                      }
                                                  }, new Response.ErrorListener() {
                                                      @Override
                                                      public void onErrorResponse(VolleyError volleyError) {
                                                          Crouton.makeText(getActivity(), getString(R.string.fetch_chapters_failed), Style.ALERT).show();
                                                          Log.e(LOG_TAG, "Could'nt fetch chapter list", volleyError);
                                                      }
                                                  }
        );

        if (savedInstanceState == null) {

            if (Utils.isOnline(getActivity())) {
                App.getInstance().getModelCache().getAsync("chapter_list", new ModelCache.CacheListener() {
                    @Override
                    public void onGet(Object item) {
                        Directory directory = (Directory) item;
                        initChapters(directory.getGroups());
                    }

                    @Override
                    public void onNotFound(String key) {
                        mFetchChaptersTask.execute();
                    }
                });
            } else {

                App.getInstance().getModelCache().getAsync("chapter_list", false, new ModelCache.CacheListener() {
                    @Override
                    public void onGet(Object item) {
                        Directory directory = (Directory) item;
                        initChapters(directory.getGroups());
                    }

                    @Override
                    public void onNotFound(String key) {
                        Crouton.makeText(getActivity(), getString(R.string.offline_alert), Style.ALERT).show();
                    }
                });
            }
        } else {

            if (savedInstanceState.containsKey("chapters")) {
                ArrayList<Chapter> chapters = savedInstanceState.getParcelableArrayList("chapters");
                mSpinnerAdapter.clear();
                mSpinnerAdapter.addAll(chapters);

                if (savedInstanceState.containsKey("selected_chapter")) {
                    Chapter selectedChapter = savedInstanceState.getParcelable("selected_chapter");
                    mViewPagerAdapter.setSelectedChapter(selectedChapter);
                    getSherlockActivity().getSupportActionBar().setSelectedNavigationItem(mSpinnerAdapter.getPosition(selectedChapter));
                } else {
                    mViewPagerAdapter.setSelectedChapter(chapters.get(0));
                }

                mViewPager.setAdapter(mViewPagerAdapter);
                mIndicator.setViewPager(mViewPager);
            } else {
                mFetchChaptersTask.execute();
            }
        }

        homeGdg = getArguments().getParcelable(HOME_GDG);
        getSherlockActivity().getSupportActionBar().setSelectedNavigationItem(mSpinnerAdapter.getPosition(homeGdg));
        mViewPagerAdapter.setSelectedChapter(homeGdg);

    }

    private void initChapters(ArrayList<Chapter> chapters) {
        addChapters(chapters);
        Chapter chapter = null;

        if (getIntent().hasExtra("org.gdg.frisbee.CHAPTER")) {
            String chapterId = getIntent().getStringExtra("org.gdg.frisbee.CHAPTER");
            for (Chapter c : chapters) {
                if (c.getGplusId().equals(chapterId)) {
                    chapter = c;
                    break;
                }
            }
            if (chapter == null)
                chapter = chapters.get(0);
        } else {
            chapter = chapters.get(0);
        }

        getSherlockActivity().getSupportActionBar().setSelectedNavigationItem(mSpinnerAdapter.getPosition(chapter));
        mViewPager.setAdapter(mViewPagerAdapter);
        mIndicator.setViewPager(mViewPager);
    }

    private void trackViewPagerPage(int position) {
        if (mViewPager == null || mViewPagerAdapter.getSelectedChapter() == null)
            return;

        Log.d(LOG_TAG, "trackViewPagerPage()");
        String page = "";

        switch (position) {
            case 0:
                page = "News";
                break;
            case 1:
                page = "Info";
                break;
            case 2:
                page = "Events";
                break;
        }
        App.getInstance().getTracker().sendView(String.format("/Main/%s/%s", mViewPagerAdapter.getSelectedChapter().getName().replaceAll(" ", "-"), page));
    }

    private void addChapters(List<Chapter> chapterList) {
        Collections.sort(chapterList, mLocationComparator);
        mSpinnerAdapter.clear();
        mSpinnerAdapter.addAll(chapterList);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume()");
        trackViewPagerPage(mViewPager.getCurrentItem());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mSpinnerAdapter.getCount() > 0)
            outState.putParcelableArrayList("chapters", mSpinnerAdapter.getAll());
        if (mViewPagerAdapter.getSelectedChapter() != null)
            outState.putParcelable("selected_chapter", mViewPagerAdapter.getSelectedChapter());

    }

    @Override
    public boolean onNavigationItemSelected(int position, long l) {
        Chapter previous = mViewPagerAdapter.getSelectedChapter();
        mViewPagerAdapter.setSelectedChapter(mSpinnerAdapter.getItem(position));
        if (previous == null || !previous.equals(mSpinnerAdapter.getItem(position))) {
            Log.d(LOG_TAG, "Switching chapter!");
            mViewPagerAdapter.notifyDataSetChanged();
        }
        return true;
    }

    public static MainGdgFragment newInstance(Chapter homeGdg) {
        MainGdgFragment f  = new MainGdgFragment();
        f.getArguments().putParcelable(HOME_GDG, homeGdg);
        return f;
    }

    public class MyAdapter extends FragmentStatePagerAdapter {
        private Context mContext;
        private Chapter mSelectedChapter;

        public MyAdapter(Context ctx, FragmentManager fm) {
            super(fm);
            mContext = ctx;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            if (mSelectedChapter == null)
                return 0;
            else
                return 3;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return NewsFragment.newInstance(mSelectedChapter.getGplusId());
                case 1:
                    return InfoFragment.newInstance(mSelectedChapter.getGplusId());
                case 2:
                    return EventFragment.newInstance(mSelectedChapter.getGplusId());
            }
            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return mContext.getText(R.string.news);
                case 1:
                    return mContext.getText(R.string.info);
                case 2:
                    return mContext.getText(R.string.events);
            }
            return "";
        }

        public Chapter getSelectedChapter() {
            return mSelectedChapter;
        }

        public void setSelectedChapter(Chapter chapter) {
            if (mSelectedChapter != null)
                trackViewPagerPage(mViewPager.getCurrentItem());

            this.mSelectedChapter = chapter;
        }
    }
}
