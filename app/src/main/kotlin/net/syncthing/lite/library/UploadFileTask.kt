package net.syncthing.lite.library

import android.content.Context
import android.net.Uri
import android.util.Log
import net.syncthing.java.bep.BlockPusher
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.utils.PathUtils
import net.syncthing.lite.utils.Util
import org.apache.commons.io.IOUtils

// TODO: this should be an IntentService with notification
class UploadFileTask(context: Context, syncthingClient: SyncthingClient,
                     localFile: Uri, private val syncthingFolder: String,
                     syncthingSubFolder: String,
                     private val onProgress: (BlockPusher.FileUploadObserver) -> Unit,
                     private val onComplete: () -> Unit,
                     private val onError: () -> Unit) {

    private val TAG = "UploadFileTask"

    private val syncthingPath = PathUtils.buildPath(syncthingSubFolder, Util.getContentFileName(context, localFile))
    private val uploadStream = context.contentResolver.openInputStream(localFile)

    private var isCancelled = false

    init {
        Log.i(TAG, "Uploading file $localFile to folder $syncthingFolder:$syncthingPath")
        syncthingClient.getBlockPusher(syncthingFolder, { blockPusher ->
            val observer = blockPusher.pushFile(uploadStream, syncthingFolder, syncthingPath)
            onProgress(observer)
                while (!observer.isCompleted()) {
                    if (isCancelled)
                        return@getBlockPusher

                    observer.waitForProgressUpdate()
                    Log.i(TAG, "upload progress = ${observer.progressPercentage()}%")
                    onProgress(observer)
                }
                IOUtils.closeQuietly(uploadStream)
                onComplete()
        }, { onError() })
    }

    fun cancel() {
        isCancelled = true
    }
}
