/*
 * Copyright 2013 The GDG Frisbee Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gdg.frisbee.android.activity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.MenuItem;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.games.GamesClient;
import com.viewpagerindicator.TitlePageIndicator;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;
import org.gdg.frisbee.android.Const;
import org.gdg.frisbee.android.adapter.ChapterAdapter;
import org.gdg.frisbee.android.adapter.DrawerAdapter;
import org.gdg.frisbee.android.api.ApiRequest;
import org.gdg.frisbee.android.api.GoogleDevelopersLive;
import org.gdg.frisbee.android.api.model.GdlShow;
import org.gdg.frisbee.android.api.model.GdlShowList;
import org.gdg.frisbee.android.app.App;
import org.gdg.frisbee.android.R;
import org.gdg.frisbee.android.api.GroupDirectory;
import org.gdg.frisbee.android.api.model.Chapter;
import org.gdg.frisbee.android.api.model.Directory;
import org.gdg.frisbee.android.cache.ModelCache;
import org.gdg.frisbee.android.fragment.EventFragment;
import org.gdg.frisbee.android.fragment.InfoFragment;
import org.gdg.frisbee.android.fragment.MainGdgFragment;
import org.gdg.frisbee.android.fragment.NewsFragment;
import org.gdg.frisbee.android.utils.ChapterComparator;
import org.gdg.frisbee.android.utils.GingerbreadLastLocationFinder;
import org.gdg.frisbee.android.utils.PlayServicesHelper;
import org.gdg.frisbee.android.utils.Utils;
import org.gdg.frisbee.android.view.ActionBarDrawerToggleCompat;
import org.joda.time.DateTime;
import roboguice.inject.InjectView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends GdgActivity  {

    private static String LOG_TAG = "GDG-MainActivity";

    public static final int REQUEST_FIRST_START_WIZARD = 100;

    @InjectView(R.id.drawer)
    private DrawerLayout mDrawerLayout;

    @InjectView(R.id.left_drawer)
    private ListView mDrawerContent;

    private DrawerAdapter mDrawerAdapter;

    private ActionBarDrawerToggleCompat mDrawerToggle;


    private boolean mFirstStart = false;

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after 
     * previously being shut down then this Bundle contains the data it most 
     * recently supplied in onSaveInstanceState(Bundle). <b>Note: Otherwise it is null.</b>
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		Log.i(LOG_TAG, "onCreate");
        setContentView(R.layout.activity_main);


        mDrawerAdapter = new DrawerAdapter(this);
        mDrawerContent.setAdapter(mDrawerAdapter);
        mDrawerContent.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                DrawerAdapter.DrawerItem item = (DrawerAdapter.DrawerItem) mDrawerAdapter.getItem(i);

                switch(item.getTitle()) {
                    case R.string.home_gdg:
                        break;
                    case R.string.achievements:
                        if(mPreferences.getBoolean(Const.SETTINGS_SIGNED_IN, false)) {
                            getPlayServicesHelper().getGamesClient(new PlayServicesHelper.OnGotGamesClientListener() {
                                @Override
                                public void onGotGamesClient(GamesClient c) {
                                    startActivityForResult(c.getAchievementsIntent(), 0);
                                }
                            });
                        } else {
                            Toast.makeText(MainActivity.this, getString(R.string.achievements_need_signin), Toast.LENGTH_LONG).show();
                        }
                        break;
                    case R.string.about:
                        startActivity(new Intent(MainActivity.this, AboutActivity.class));
                        break;
                    case R.string.gdl:
                        startActivity(new Intent(MainActivity.this, GdlActivity.class));
                        break;
                    case R.string.pulse:
                        startActivity(new Intent(MainActivity.this, PulseActivity.class));
                        break;
                    case R.string.settings:
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        break;
                }
            }
        });
        getSupportActionBar().setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        getSupportActionBar().setListNavigationCallbacks(mSpinnerAdapter, MainActivity.this);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mDrawerToggle = new ActionBarDrawerToggleCompat(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                //getActionBar().setTitle(mTitle);
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                //getActionBar().setTitle(mDrawerTitle);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        Intent intent = getIntent();
        if(intent != null && intent.getAction() != null && intent.getAction().equals("finish_first_start")) {
                Log.d(LOG_TAG, "Completed FirstStartWizard");

                if(mPreferences.getBoolean(Const.SETTINGS_SIGNED_IN, false)) {
                    mFirstStart = true;
                }

            Chapter homeGdg = getIntent().getParcelableExtra("selected_chapter");
            MainGdgFragment.newInstance(homeGdg);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);
    }

    @Override
    public void onSignInFailed() {
        super.onSignInFailed();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public void onSignInSucceeded() {
        super.onSignInSucceeded();    //To change body of overridden methods use File | Settings | File Templates.

        checkAchievements();
    }

    private void checkAchievements() {
        if(mFirstStart) {
            mFirstStart = false;
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getPlayServicesHelper().getGamesClient(new PlayServicesHelper.OnGotGamesClientListener() {
                        @Override
                        public void onGotGamesClient(GamesClient c) {
                            c.unlockAchievement(Const.ACHIEVEMENT_SIGNIN);
                        }
                    });
                }
            }, 1000);
        }

        if(mPreferences.getInt(Const.SETTINGS_APP_STARTS,0) == 10) {
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getPlayServicesHelper().getGamesClient(new PlayServicesHelper.OnGotGamesClientListener() {
                        @Override
                        public void onGotGamesClient(GamesClient c) {
                            c.unlockAchievement(Const.ACHIEVEMENT_RETURN);
                        }
                    });
                }
            }, 1000);
        }

        if(mPreferences.getInt(Const.SETTINGS_APP_STARTS,0) == 50) {
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getPlayServicesHelper().getGamesClient(new PlayServicesHelper.OnGotGamesClientListener() {
                        @Override
                        public void onGotGamesClient(GamesClient c) {
                            c.unlockAchievement(Const.ACHIEVEMENT_KING_OF_THE_HILL);
                        }
                    });
                }
            }, 1000);
        }
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LOG_TAG, "onStart()");

        if(mPreferences.getBoolean(Const.SETTINGS_FIRST_START, Const.SETTINGS_FIRST_START_DEFAULT)) {
            startActivityForResult(new Intent(this, FirstStartActivity.class), REQUEST_FIRST_START_WIZARD);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume()");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause()");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

    }

}

