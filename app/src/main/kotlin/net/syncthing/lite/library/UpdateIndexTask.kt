package net.syncthing.lite.library

import android.content.Context
import android.preference.PreferenceManager
import net.syncthing.java.client.SyncthingClient
import net.syncthing.lite.R
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.util.*

class UpdateIndexTask(private val androidContext: Context, private val syncthingClient: SyncthingClient) {
    private val mPreferences = PreferenceManager.getDefaultSharedPreferences(androidContext)

    fun updateIndex() {
        if (sIndexUpdateInProgress)
            return

        sIndexUpdateInProgress = true
        syncthingClient.updateIndexFromPeers { _, failures ->
            sIndexUpdateInProgress = false
            if (failures.isEmpty()) {
                showToast(androidContext.getString(R.string.toast_index_update_successful))
            } else {
                showToast(androidContext.getString(R.string.toast_index_update_failed, failures.size))
            }
            mPreferences.edit()
                    .putLong(LAST_INDEX_UPDATE_TS_PREF, Date().time)
                    .apply()
        }
    }

    private fun showToast(message: String) {
        doAsync {
            uiThread {
                androidContext.toast(message)
            }
        }
    }

    companion object {

        val LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS"

        private var sIndexUpdateInProgress: Boolean = false
    }
}