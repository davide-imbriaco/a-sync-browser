package net.syncthing.lite.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.widget.Toast;

import net.syncthing.lite.R;

import java.util.Date;

import it.anyplace.sync.client.SyncthingClient;

public class UpdateIndexTask {

    public static final String LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS";

    private static boolean sIndexUpdateInProgress;

    private final Context mContext;
    private final SyncthingClient mSyncthingClient;
    private final SharedPreferences mPreferences;
    private final Handler mMainHandler;

    public UpdateIndexTask(Context context, SyncthingClient syncthingClient) {
        mContext = context;
        mSyncthingClient = syncthingClient;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mMainHandler = new Handler();
    }

    public void updateIndex() {
        if (sIndexUpdateInProgress)
            return;

        sIndexUpdateInProgress = true;
        mSyncthingClient.updateIndexFromPeers((successes, failures) -> {
            sIndexUpdateInProgress = false;
            if (failures.isEmpty()) {
                showToast(mContext.getString(R.string.toast_index_update_successful));
            } else {
                showToast(mContext.getString(R.string.toast_index_update_failed, failures.size()));
            }
            mPreferences.edit()
                    .putLong(LAST_INDEX_UPDATE_TS_PREF, new Date().getTime())
                    .apply();
        });
    }

    private void showToast(String message) {
        mMainHandler.post(() ->
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show());
    }
}