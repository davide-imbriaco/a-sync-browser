package net.syncthing.lite.activities

import android.app.AlertDialog
import android.content.Context
import android.databinding.DataBindingUtil
import android.os.AsyncTask
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import net.syncthing.java.bep.FolderBrowser
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.configuration.ConfigurationService
import net.syncthing.lite.BuildConfig
import net.syncthing.lite.R
import net.syncthing.lite.databinding.DialogLoadingBinding
import net.syncthing.lite.utils.LibraryHandler
import net.syncthing.lite.utils.UpdateIndexTask
import org.slf4j.impl.HandroidLoggerAdapter
import java.util.*

abstract class SyncthingActivity : AppCompatActivity() {

    companion object {
        private val TAG = "SyncthingActivity"

        private var activityCount = 0
        private var libraryHandler: LibraryHandler? = null
    }

    fun syncthingClient(): SyncthingClient = libraryHandler!!.syncthingClient!!

    fun configuration(): ConfigurationService = libraryHandler!!.configuration!!

    fun folderBrowser(): FolderBrowser? = libraryHandler!!.folderBrowser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
        activityCount++
        if (libraryHandler == null) {
            InitTask(this, this::onLibraryLoaded).execute()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityCount--
        Thread {
            if (activityCount == 0) {
                libraryHandler!!.destroy()
                libraryHandler = null
            }
        }.start()
    }

    private class InitTask(val context: Context, val onLibraryLoaded: () -> Unit)
        : AsyncTask<Void?, Void?, Void?>() {

        private var loadingDialog: AlertDialog? = null

        override fun onPreExecute() {
            val binding = DataBindingUtil.inflate<DialogLoadingBinding>(
                    LayoutInflater.from(context), R.layout.dialog_loading, null, false)
            binding.loadingText.text = "loading config, starting syncthing client"
            loadingDialog = AlertDialog.Builder(context)
                    .setCancelable(false)
                    .setView(binding.root)
                    .show()
        }

        override fun doInBackground(vararg voidd: Void?): Void? {
            libraryHandler = LibraryHandler()
            libraryHandler!!.init(context)
            return null
        }

        override fun onPostExecute(voidd: Void?) {
            Log.d(TAG, "xxx onPostExecute()")
            loadingDialog!!.cancel()
            libraryHandler!!.setOnIndexUpdatedListener(object : LibraryHandler.OnIndexUpdatedListener {
                override fun onIndexUpdateProgress(folder: FolderInfo, percentage: Int) {
                    onIndexUpdateProgress(folder, percentage)
                }

                override fun onIndexUpdateComplete() {
                    onIndexUpdateComplete()
                }
            })

            //trigger update if last was more than 10mins ago
            val lastUpdateMillis = PreferenceManager.getDefaultSharedPreferences(context)
                    .getLong(UpdateIndexTask.LAST_INDEX_UPDATE_TS_PREF, -1)
            val lastUpdateTimeAgo = Date().time - lastUpdateMillis
            if (lastUpdateMillis == -1L || lastUpdateTimeAgo > 10 * 60 * 1000) {
                Log.d(TAG, "trigger index update, last was " + Date(lastUpdateMillis))
                UpdateIndexTask(context, libraryHandler!!.syncthingClient!!).updateIndex()
            }
            onLibraryLoaded()
        }
    }

    open fun onIndexUpdateProgress(folder: FolderInfo, percentage: Int) {}

    open fun onIndexUpdateComplete() {}

    open fun onLibraryLoaded() {}
}
