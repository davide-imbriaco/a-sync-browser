package net.syncthing.lite.activities;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;

import net.syncthing.lite.R;
import net.syncthing.lite.databinding.ActivityMainBinding;
import net.syncthing.lite.fragments.DevicesFragment;
import net.syncthing.lite.fragments.FoldersFragment;
import net.syncthing.lite.utils.UpdateIndexTask;

import it.anyplace.sync.core.beans.FolderInfo;

public class MainActivity extends SyncthingActivity {

    private ActivityMainBinding mBinding;
    private ActionBarDrawerToggle mDrawerToggle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        mDrawerToggle = new ActionBarDrawerToggle(
                this, mBinding.drawerLayout, R.string.app_name, R.string.app_name);
        mBinding.drawerLayout.addDrawerListener(mDrawerToggle);
        mBinding.navigation.setNavigationItemSelectedListener(this::onNavigationItemSelectedListener);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onLibraryLoaded() {
        super.onLibraryLoaded();
        setContentFragment(new FoldersFragment());
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

    private boolean onNavigationItemSelectedListener(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.folders:
                setContentFragment(new FoldersFragment());
                break;
            case R.id.devices:
                setContentFragment(new DevicesFragment());
                break;
            case R.id.update_index:
                new UpdateIndexTask(this, getSyncthingClient()).updateIndex();
                break;
            case R.id.clear_index:
                new AlertDialog.Builder(this)
                        .setTitle("clear cache and index")
                        .setMessage("clear all cache data and index data?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, (d, w) -> cleanCacheAndIndex())
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                break;
        }
        mBinding.drawerLayout.closeDrawer(Gravity.START);
        return true;
    }

    private void setContentFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();
    }

    private void cleanCacheAndIndex() {
        getSyncthingClient().clearCacheAndIndex();
        recreate();
    }

    @Override
    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
        mBinding.mainIndexProgressBar.setVisibility(View.VISIBLE);
        mBinding.mainIndexProgressBarLabel.setText("index update, folder "
                + folder.getLabel() + " " + percentage + "% synchronized");
    }

    @Override
    public void onIndexUpdateComplete() {
        mBinding.mainIndexProgressBar.setVisibility(View.GONE);
    }
}
