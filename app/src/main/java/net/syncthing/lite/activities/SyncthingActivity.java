package net.syncthing.lite.activities;

import android.app.AlertDialog;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import net.syncthing.lite.BuildConfig;
import net.syncthing.lite.R;
import net.syncthing.lite.databinding.DialogLoadingBinding;
import net.syncthing.lite.utils.LibraryHandler;
import net.syncthing.lite.utils.UpdateIndexTask;

import org.slf4j.impl.HandroidLoggerAdapter;

import java.util.Date;

import it.anyplace.sync.bep.FolderBrowser;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.configuration.ConfigurationService;

public abstract class SyncthingActivity extends AppCompatActivity {

    private static final String TAG = "SyncthingActivity";

    private static int mActivityCount = 0;
    private static LibraryHandler mLibraryHandler;

    private AlertDialog mLoadingDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG;
        mActivityCount++;
        if (mLibraryHandler == null) {
            initLibrary();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mActivityCount--;
        new Thread(() -> {
            if (mActivityCount == 0) {
                mLibraryHandler.destroy();
                mLibraryHandler = null;
            }
        }).start();
    }

    private void initLibrary() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                onLoadingLibrary();
            }

            @Override
            protected Void doInBackground(Void... voidd) {
                mLibraryHandler = new LibraryHandler();
                mLibraryHandler.init(SyncthingActivity.this);
                return null;
            }

            @Override
            protected void onPostExecute(Void voidd) {
                mLoadingDialog.cancel();
                mLibraryHandler.setOnIndexUpdatedListener(new LibraryHandler.OnIndexUpdatedListener() {
                    @Override
                    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
                        onIndexUpdateProgress(folder, percentage);
                    }

                    @Override
                    public void onIndexUpdateComplete() {
                        onIndexUpdateComplete();
                    }
                });
                onLibraryLoaded();
            }
        }.execute();
    }

    public SyncthingClient getSyncthingClient() {
        return mLibraryHandler.getSyncthingClient();
    }

    public ConfigurationService getConfiguration() {
        return mLibraryHandler.getConfiguration();
    }

    public FolderBrowser getFolderBrowser() {
        return mLibraryHandler.getFolderBrowser();
    }

    @SuppressWarnings("unused")
    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
    }

    @SuppressWarnings("unused")
    public void onIndexUpdateComplete() {
    }

    public void onLoadingLibrary() {
        DialogLoadingBinding binding = DataBindingUtil.inflate(
                getLayoutInflater(), R.layout.dialog_loading, null, false);
        binding.loadingText.setText("loading config, starting syncthing client");
        mLoadingDialog = new android.app.AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.getRoot())
                .show();
    }

    public void onLibraryLoaded() {
        Date lastUpdate;
        long lastUpdateMillis = PreferenceManager.getDefaultSharedPreferences(this)
                .getLong(UpdateIndexTask.LAST_INDEX_UPDATE_TS_PREF, -1);
        if (lastUpdateMillis < 0) {
            lastUpdate = null;
        } else {
            lastUpdate = new Date(lastUpdateMillis);
        }
        //trigger update if last was more than 10mins ago
        if (lastUpdate == null || new Date().getTime() - lastUpdate.getTime() > 10 * 60 * 1000) {
            Log.d(TAG, "trigger index update, last was " + lastUpdate);
            new UpdateIndexTask(this, getSyncthingClient()).updateIndex();
        }
    }
}
