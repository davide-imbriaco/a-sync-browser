package net.syncthing.lite.library

import android.content.Context
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import com.google.common.eventbus.Subscribe
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.java.bep.FolderBrowser
import net.syncthing.java.bep.IndexHandler
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.configuration.ConfigurationService
import net.syncthing.java.core.security.KeystoreHandler
import net.syncthing.lite.utils.Util
import org.apache.commons.io.FileUtils
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.IOException
import java.util.*

class LibraryHandler(context: Context, onLibraryLoaded: (LibraryHandler) -> Unit,
                     onIndexUpdateProgressListener: (String, Int) -> Unit,
                     onIndexUpdateCompleteListener: () -> Unit) {

    companion object {
        private var instanceCount = 0
        private var configuration: ConfigurationService? = null
        private var syncthingClient: SyncthingClient? = null
        private var folderBrowser: FolderBrowser? = null
        private val callbacks = ArrayList<(ConfigurationService, SyncthingClient, FolderBrowser) -> Unit>()
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
            @Subscribe
            fun handleIndexRecordAquiredEvent(event: IndexHandler.IndexRecordAquiredEvent) {
                val indexInfo = event.indexInfo()
                event.newRecords().size
                Log.i(TAG, "handleIndexRecordEvent trigger folder list update from index record acquired")
                onIndexUpdateProgressListener(event.folder(), (indexInfo.completed * 100).toInt())
            }

            @Subscribe
            fun handleRemoteIndexAquiredEvent(event: IndexHandler.FullIndexAquiredEvent) {
                Log.i(TAG, "handleIndexAcquiredEvent trigger folder list update from index acquired")
                onIndexUpdateCompleteListener()
            }
        }
        syncthingClient {
            it.indexHandler.eventBus.register(onIndexUpdateListener)
        }
    }

    private fun init(context: Context) {
        isLoading = true
        val configuration = ConfigurationService.newLoader()
                .setCache(File(context.externalCacheDir, ".cache"))
                .setDatabase(File(context.getExternalFilesDir(null), "database"))
                .loadFrom(File(context.getExternalFilesDir(null), "config.properties"))
        configuration.edit().setDeviceName(Util.getDeviceName())
        try {
            FileUtils.cleanDirectory(configuration.temp)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to delete temporary files", e)
            close()
        }

        KeystoreHandler.newLoader().loadAndStore(configuration)
        configuration.edit().persistLater()
        Log.i(TAG, "loaded mConfiguration = " + configuration.newWriter().dumpToString())
        Log.i(TAG, "storage space = " + configuration.storageInfo.dumpAvailableSpace())
        val syncthingClient = SyncthingClient(configuration)
        //TODO listen for device events, update device list
        val folderBrowser = syncthingClient.indexHandler.newFolderBrowser()

        if (instanceCount == 0) {
            Log.d(TAG, "All LibraryHandler instances were closed during init")
            configuration.close()
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

    fun library(callback: (ConfigurationService, SyncthingClient, FolderBrowser) -> Unit) {
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

    fun configuration(callback: (ConfigurationService) -> Unit) {
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
                it.indexHandler.eventBus.unregister(onIndexUpdateListener)
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
                    configuration?.close()
                    configuration = null
                }
            }.start()
        }, 1000)

    }
}
