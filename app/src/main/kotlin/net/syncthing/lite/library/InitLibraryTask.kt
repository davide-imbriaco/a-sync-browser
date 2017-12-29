package net.syncthing.lite.library

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import net.syncthing.java.core.beans.FolderInfo
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*

class InitLibraryTask(private val context: Context, private val onLibraryLoaded: (LibraryHandler) -> Unit,
                      private val onIndexUpdateProgressListener: (FolderInfo, Int) -> Unit,
                      private val onIndexUpdateCompleteListener: () -> Unit) {

    private val TAG = "InitLibraryTask"

    init {
        doAsync {
            val libraryHandler = LibraryHandler()
            libraryHandler.init(context)
            libraryHandler.setOnIndexUpdatedListener(object : LibraryHandler.OnIndexUpdatedListener {
                override fun onIndexUpdateProgress(folder: FolderInfo, percentage: Int) {
                    onIndexUpdateProgressListener(folder, percentage)
                }

                override fun onIndexUpdateComplete() {
                    onIndexUpdateCompleteListener()
                }
            })
            //trigger update if last was more than 10mins ago
            val lastUpdateMillis = PreferenceManager.getDefaultSharedPreferences(context)
                    .getLong(UpdateIndexTask.LAST_INDEX_UPDATE_TS_PREF, -1)
            val lastUpdateTimeAgo = Date().time - lastUpdateMillis
            if (lastUpdateMillis == -1L || lastUpdateTimeAgo > 10 * 60 * 1000) {
                Log.d(TAG, "trigger index update, last was " + Date(lastUpdateMillis))
                UpdateIndexTask(context, libraryHandler.syncthingClient!!).updateIndex()
            }
            uiThread {
                onLibraryLoaded(libraryHandler)
            }
        }
    }
}