package net.syncthing.lite.library

import android.content.Context
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.java.bep.FolderBrowser
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.java.core.beans.IndexInfo
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.lite.utils.Util
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.util.*

class LibraryHandler(context: Context, onLibraryLoaded: (LibraryHandler) -> Unit,
                     private val onIndexUpdateProgressListener: (String, Int) -> Unit,
                     private val onIndexUpdateCompleteListener: (String) -> Unit) {

    companion object {
        private var instanceCount = 0
        private var configuration: Configuration? = null
        private var syncthingClient: SyncthingClient? = null
        private var folderBrowser: FolderBrowser? = null
        private val callbacks = ArrayList<(Configuration, SyncthingClient, FolderBrowser) -> Unit>()
        private var isLoading = false
    }

    private val TAG = "LibConnectionHandler"

    private val onIndexUpdateListener: Any

    init {
        instanceCount++
        if (configuration == null && !isLoading) {
            doAsync {
                init(context)
                //trigger update if last was more than 10mins ago
                val lastUpdateMillis = PreferenceManager.getDefaultSharedPreferences(context)
                        .getLong(UpdateIndexTask.LAST_INDEX_UPDATE_TS_PREF, -1)
                val lastUpdateTimeAgo = Date().time - lastUpdateMillis
                if (lastUpdateMillis == -1L || lastUpdateTimeAgo > 10 * 60 * 1000) {
                    Log.d(TAG, "trigger index update, last was " + Date(lastUpdateMillis))
                    syncthingClient { UpdateIndexTask(context, it).updateIndex() }
                }
                uiThread {
                    onLibraryLoaded(this@LibraryHandler)
                }
            }
        } else {
            onLibraryLoaded(this)
        }

        onIndexUpdateListener = object : Any() {
        }
        syncthingClient {
            it.indexHandler.registerOnIndexRecordAcquiredListener(this::onIndexRecordAcquired)
            it.indexHandler.registerOnFullIndexAcquiredListenersListener(this::onRemoteIndexAcquired)
        }
    }

    private fun onIndexRecordAcquired(folderId: String, newRecords: List<FileInfo>, indexInfo: IndexInfo) {
        Log.i(TAG, "handleIndexRecordEvent trigger folder list update from index record acquired")

        async(UI) {
            onIndexUpdateProgressListener(folderId, (indexInfo.getCompleted() * 100).toInt())
        }
    }

    private fun onRemoteIndexAcquired(folderId: String) {
        Log.i(TAG, "handleIndexAcquiredEvent trigger folder list update from index acquired")

        async(UI) {
            onIndexUpdateCompleteListener(folderId)
        }
    }

    private fun init(context: Context) {
        isLoading = true
        val configuration = Configuration(configFolder = context.filesDir, cacheFolder = context.externalCacheDir)
        configuration.localDeviceName = Util.getDeviceName()
        configuration.persistLater()
        val syncthingClient = SyncthingClient(configuration)
        //TODO listen for device events, update device list
        val folderBrowser = syncthingClient.indexHandler.newFolderBrowser()

        if (instanceCount == 0) {
            Log.d(TAG, "All LibraryHandler instances were closed during init")
            syncthingClient.close()
            folderBrowser.close()
        }

        async(UI) {
            callbacks.forEach { it(configuration, syncthingClient, folderBrowser) }
        }
        LibraryHandler.configuration = configuration
        LibraryHandler.syncthingClient = syncthingClient
        LibraryHandler.folderBrowser = folderBrowser
        isLoading = false
    }

    fun library(callback: (Configuration, SyncthingClient, FolderBrowser) -> Unit) {
        val nullCount = listOf(configuration, syncthingClient, folderBrowser).count { it == null }
        assert(nullCount == 0 || nullCount == 3, { "Inconsistent library state" })

        // https://stackoverflow.com/a/35522422/1837158
        fun <T1: Any, T2: Any, T3: Any, R: Any> safeLet(p1: T1?, p2: T2?, p3: T3?, block: (T1, T2, T3)->R?): R? {
            return if (p1 != null && p2 != null && p3 != null) block(p1, p2, p3) else null
        }
        safeLet(configuration, syncthingClient, folderBrowser) { c, s, f ->
            callback(c, s, f)
        } ?: run {
            if (isLoading) {
                callbacks.add(callback)
            }
        }
    }

    fun syncthingClient(callback: (SyncthingClient) -> Unit) {
        library { _, s, _ -> callback(s) }
    }

    fun configuration(callback: (Configuration) -> Unit) {
        library { c, _, _ -> callback(c) }
    }

    fun folderBrowser(callback: (FolderBrowser) -> Unit) {
        library { _, _, f -> callback(f) }
    }

    /**
     * Unregisters index update listener and decreases instance count.
     *
     * We wait a bit before closing [[syncthingClient]] etc, in case LibraryHandler is opened again
     * soon (eg in case of device rotation).
     */
    fun close() {
        syncthingClient {
            try {
                it.indexHandler.unregisterOnIndexRecordAcquiredListener(this::onIndexRecordAcquired)
                it.indexHandler.unregisterOnFullIndexAcquiredListenersListener(this::onRemoteIndexAcquired)
            } catch (e: IllegalArgumentException) {
                // ignored, no idea why this is thrown
            }
        }

        instanceCount--
        Handler().postDelayed({
            Thread {
                if (instanceCount == 0) {
                    folderBrowser?.close()
                    folderBrowser = null
                    syncthingClient?.close()
                    syncthingClient = null
                    configuration = null
                }
            }.start()
        }, 1000)

    }
}
