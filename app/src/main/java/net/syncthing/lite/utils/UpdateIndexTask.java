package net.syncthing.lite.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.util.Date;

import it.anyplace.sync.client.SyncthingClient;

public class UpdateIndexTask extends AsyncTask<Void, Void, Exception> {

    private static final String TAG = "UpdateIndexTask";
    public static final String LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS";

    private static boolean sIndexUpdateInProgress;

    private final Context mContext;
    private final SyncthingClient mSyncthingClient;
    private final SharedPreferences mPreferences;

    public UpdateIndexTask(Context context, SyncthingClient syncthingClient) {
        mContext = context;
        mSyncthingClient = syncthingClient;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    @Override
    protected void onPreExecute() {
        if (sIndexUpdateInProgress) {
            cancel(true);
        } else {
            sIndexUpdateInProgress = true;
        }
    }

    @Override
    protected Exception doInBackground(Void... voidd) {
        mSyncthingClient.waitForRemoteIndexAquired();
        return null;
    }

    @Override
    protected void onPostExecute(Exception ex) {
        sIndexUpdateInProgress = false;
        if (ex != null) {
            Toast.makeText(mContext, "error updating index: " + ex.toString(), Toast.LENGTH_LONG).show();
        }
        mPreferences.edit()
                .putLong(LAST_INDEX_UPDATE_TS_PREF, new Date().getTime())
                .apply();
    }
}