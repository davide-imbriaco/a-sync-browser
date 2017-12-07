package net.syncthing.lite.utils

import android.content.Context
import android.os.Handler
import android.preference.PreferenceManager
import android.widget.Toast
import it.anyplace.sync.client.SyncthingClient
import net.syncthing.lite.R
import java.util.*

class UpdateIndexTask(private val mContext: Context, private val mSyncthingClient: SyncthingClient) {
    private val mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
    private val mMainHandler = Handler()

    fun updateIndex() {
        if (sIndexUpdateInProgress)
            return

        sIndexUpdateInProgress = true
        mSyncthingClient.updateIndexFromPeers { _, failures ->
            sIndexUpdateInProgress = false
            if (failures.isEmpty()) {
                showToast(mContext.getString(R.string.toast_index_update_successful))
            } else {
                showToast(mContext.getString(R.string.toast_index_update_failed, failures.size))
            }
            mPreferences.edit()
                    .putLong(LAST_INDEX_UPDATE_TS_PREF, Date().time)
                    .apply()
        }
    }

    private fun showToast(message: String) {
        mMainHandler.post { Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {

        val LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS"

        private var sIndexUpdateInProgress: Boolean = false
    }
}