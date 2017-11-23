package net.syncthing.lite.utils;

import android.content.Context;
import android.util.Log;

import com.google.common.eventbus.Subscribe;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import it.anyplace.sync.bep.FolderBrowser;
import it.anyplace.sync.bep.IndexHandler;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.beans.IndexInfo;
import it.anyplace.sync.core.configuration.ConfigurationService;
import it.anyplace.sync.core.security.KeystoreHandler;

public class LibraryHandler {

    private static final String TAG = "LibConnectionHandler";

    public interface OnIndexUpdatedListener {
        void onIndexUpdateProgress(FolderInfo folder, int percentage);
        void onIndexUpdateComplete();
    }

    private OnIndexUpdatedListener mOnIndexUpdatedListener;
    private ConfigurationService mConfiguration;
    private SyncthingClient mSyncthingClient;
    private FolderBrowser mFolderBrowser;

    public void init(Context context) {
        mConfiguration = ConfigurationService.newLoader()
                .setCache(new File(context.getExternalCacheDir(), "cache"))
                .setDatabase(new File(context.getExternalFilesDir(null), "database"))
                .loadFrom(new File(context.getExternalFilesDir(null), "config.properties"));
        mConfiguration.edit().setDeviceName(Util.getDeviceName());
        try {
            FileUtils.cleanDirectory(mConfiguration.getTemp());
        } catch (IOException ex) {
            Log.e(TAG, "error", ex);
            destroy();
        }
        KeystoreHandler.newLoader().loadAndStore(mConfiguration);
        mConfiguration.edit().persistLater();
        Log.i(TAG, "loaded mConfiguration = " + mConfiguration.newWriter().dumpToString());
        Log.i(TAG, "storage space = " + mConfiguration.getStorageInfo().dumpAvailableSpace());
        mSyncthingClient = new it.anyplace.sync.client.SyncthingClient(mConfiguration);
        //TODO listen for device events, update device list
        mFolderBrowser = mSyncthingClient.getIndexHandler().newFolderBrowser();
    }

    public void setOnIndexUpdatedListener(OnIndexUpdatedListener onIndexUpdatedListener) {
        mOnIndexUpdatedListener = onIndexUpdatedListener;
        mSyncthingClient.getIndexHandler().getEventBus().register(new Object() {

            @Subscribe
            @SuppressWarnings("unused")
            public void handleIndexRecordAquiredEvent(IndexHandler.IndexRecordAquiredEvent event) {
                FolderInfo folder = mSyncthingClient.getIndexHandler().getFolderInfo(event.getFolder());
                IndexInfo indexInfo = event.getIndexInfo();
                event.getNewRecords().size();
                Log.i(TAG, "handleIndexRecordEvent trigger folder list update from index record acquired");
                mOnIndexUpdatedListener.onIndexUpdateProgress(folder, (int) (indexInfo.getCompleted() * 100));
            }

            @Subscribe
            @SuppressWarnings("unused")
            public void handleRemoteIndexAquiredEvent(IndexHandler.FullIndexAquiredEvent event)  {
                Log.i(TAG, "handleIndexAquiredEvent trigger folder list update from index acquired");
                mOnIndexUpdatedListener.onIndexUpdateComplete();
            }
        });
    }

    public void destroy() {
        mFolderBrowser.close();
        mSyncthingClient.close();
        mConfiguration.close();
    }

    public SyncthingClient getSyncthingClient() {
        return mSyncthingClient;
    }

    public ConfigurationService getConfiguration() {
        return mConfiguration;
    }

    public FolderBrowser getFolderBrowser() {
        return mFolderBrowser;
    }
}
