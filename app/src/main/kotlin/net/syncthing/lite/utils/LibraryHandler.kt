package net.syncthing.lite.utils

import android.content.Context
import android.util.Log
import com.google.common.eventbus.Subscribe
import net.syncthing.java.bep.FolderBrowser
import net.syncthing.java.bep.IndexHandler
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.configuration.ConfigurationService
import net.syncthing.java.core.security.KeystoreHandler
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

class LibraryHandler {

    private var mOnIndexUpdatedListener: OnIndexUpdatedListener? = null
    var configuration: ConfigurationService? = null
        private set
    var syncthingClient: SyncthingClient? = null
        private set
    var folderBrowser: FolderBrowser? = null
        private set

    interface OnIndexUpdatedListener {
        fun onIndexUpdateProgress(folder: FolderInfo, percentage: Int)
        fun onIndexUpdateComplete()
    }

    fun init(context: Context) {
        configuration = ConfigurationService.newLoader()
                .setCache(File(context.externalCacheDir, "cache"))
                .setDatabase(File(context.getExternalFilesDir(null), "database"))
                .loadFrom(File(context.getExternalFilesDir(null), "config.properties"))
        configuration!!.edit().setDeviceName(Util.getDeviceName())
        try {
            FileUtils.cleanDirectory(configuration!!.temp)
        } catch (ex: IOException) {
            Log.e(TAG, "error", ex)
            destroy()
        }

        KeystoreHandler.newLoader().loadAndStore(configuration!!)
        configuration!!.edit().persistLater()
        Log.i(TAG, "loaded mConfiguration = " + configuration!!.newWriter().dumpToString())
        Log.i(TAG, "storage space = " + configuration!!.storageInfo.dumpAvailableSpace())
        syncthingClient = net.syncthing.java.client.SyncthingClient(configuration!!)
        //TODO listen for device events, update device list
        folderBrowser = syncthingClient!!.indexHandler.newFolderBrowser()
    }

    fun setOnIndexUpdatedListener(onIndexUpdatedListener: OnIndexUpdatedListener) {
        mOnIndexUpdatedListener = onIndexUpdatedListener
        syncthingClient!!.indexHandler.eventBus.register(object : Any() {

            @Subscribe
            fun handleIndexRecordAquiredEvent(event: IndexHandler.IndexRecordAquiredEvent) {
                val folder = syncthingClient!!.indexHandler.getFolderInfo(event.folder)
                val indexInfo = event.indexInfo
                event.newRecords.size
                Log.i(TAG, "handleIndexRecordEvent trigger folder list update from index record acquired")
                mOnIndexUpdatedListener!!.onIndexUpdateProgress(folder, (indexInfo.completed * 100).toInt())
            }

            @Subscribe
            fun handleRemoteIndexAquiredEvent(event: IndexHandler.FullIndexAquiredEvent) {
                Log.i(TAG, "handleIndexAquiredEvent trigger folder list update from index acquired")
                mOnIndexUpdatedListener!!.onIndexUpdateComplete()
            }
        })
    }

    fun destroy() {
        folderBrowser!!.close()
        syncthingClient!!.close()
        configuration!!.close()
    }

    companion object {

        private val TAG = "LibConnectionHandler"
    }
}
