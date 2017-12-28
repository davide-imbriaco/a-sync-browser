package net.syncthing.lite.utils

import android.content.Context
import android.os.Handler
import android.preference.PreferenceManager
import android.widget.Toast
import net.syncthing.java.client.SyncthingClient
import net.syncthing.lite.R
import java.util.*

class UpdateIndexTask(private val context: Context, private val syncthingClient: SyncthingClient) {
    private val mPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val mMainHandler = Handler()

    fun updateIndex() {
        if (sIndexUpdateInProgress)
            return

        sIndexUpdateInProgress = true
        syncthingClient.updateIndexFromPeers { _, failures ->
            sIndexUpdateInProgress = false
            if (failures.isEmpty()) {
                showToast(context.getString(R.string.toast_index_update_successful))
            } else {
                showToast(context.getString(R.string.toast_index_update_failed, failures.size))
            }
            mPreferences.edit()
                    .putLong(LAST_INDEX_UPDATE_TS_PREF, Date().time)
                    .apply()
        }
    }

    private fun showToast(message: String) {
        mMainHandler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {

        val LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS"

        private var sIndexUpdateInProgress: Boolean = false
    }
}