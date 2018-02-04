package net.syncthing.lite.library

import android.content.Context
import android.util.Log
import net.syncthing.java.bep.BlockPuller
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FileInfo
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

class DownloadFileTask(private val context: Context, syncthingClient: SyncthingClient,
                       private val fileInfo: FileInfo,
                       private val onProgress: (DownloadFileTask, BlockPuller.FileDownloadObserver) -> Unit,
                       private val onComplete: (File) -> Unit,
                       private val onError: () -> Unit) {

    private val Tag = "DownloadFileTask"
    private var isCancelled = false

    init {
        syncthingClient.getBlockPuller(fileInfo.folder, { blockPuller ->
            val observer = blockPuller.pullFile(fileInfo)
            onProgress(this, observer)
            try {
                while (!observer.isCompleted()) {
                    if (isCancelled)
                        return@getBlockPuller

                    observer.waitForProgressUpdate()
                    Log.i("pullFile", "download progress = " + observer.progressMessage())
                    onProgress(this, observer)
                }

                val outputFile = File("${context.externalCacheDir}/${fileInfo.folder}/${fileInfo.path}")
                FileUtils.copyInputStreamToFile(observer.inputStream(), outputFile)
                Log.i(Tag, "Downloaded file $fileInfo")
                onComplete(outputFile)
            } catch (e: IOException) {
                onError()
                Log.w(Tag, "Failed to download file $fileInfo", e)
            }
        }, { onError() })
    }

    fun cancel() {
        isCancelled = true
    }
}