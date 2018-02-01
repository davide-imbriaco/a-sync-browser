package net.syncthing.lite.library

import android.content.Context
import android.util.Log
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.java.bep.BlockPuller
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import org.apache.commons.io.FileUtils
import org.jetbrains.anko.toast
import java.io.File
import java.io.IOException

class DownloadFileTask(private val mContext: Context, private val mSyncthingClient: SyncthingClient,
                       private val fileInfo: FileInfo, private val isCancelled: () -> Boolean,
                       private val onProgress: (BlockPuller.FileDownloadObserver) -> Unit,
                       private val onComplete: (File) -> Unit,
                       private val onError: () -> Unit) {

    private val Tag = "DownloadFileTask"

    init {
        mSyncthingClient.pullFile(fileInfo, { observer ->
            onProgress(observer)
            try {
                while (!observer.isCompleted()) {
                    if (isCancelled())
                        return@pullFile

                    observer.waitForProgressUpdate()
                    Log.i("pullFile", "download progress = " + observer.progressMessage())
                    onProgress(observer)
                }

                val outputFile = File("${mContext.externalCacheDir}/${fileInfo.folder}/${fileInfo.path}")
                FileUtils.copyInputStreamToFile(observer.inputStream(), outputFile)
                Log.i(Tag, "Downloaded file $fileInfo")
                onComplete(outputFile)
            } catch (e: IOException) {
                onErrorHandler()
                Log.w(Tag, "Failed to download file $fileInfo", e)
            } catch (e: InterruptedException) {
                onErrorHandler()
                Log.w(Tag, "Failed to download file $fileInfo", e)
            }
        }) { onErrorHandler() }
    }

    private fun onErrorHandler() {
        async(UI) {
            onError()
            mContext.toast(R.string.toast_file_download_failed)
        }
    }
}