package net.syncthing.lite.library

import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.java.bep.BlockPusher
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.utils.PathUtils
import net.syncthing.lite.R
import net.syncthing.lite.utils.Util
import org.apache.commons.io.IOUtils
import org.jetbrains.anko.toast
import java.io.IOException

// TODO: this should be an IntentService with notification
class UploadFileTask(private val context: Context, private val syncthingClient: SyncthingClient,
                     private val localFile: Uri, private val syncthingFolder: String,
                     syncthingSubFolder: String,
                     private val onUploadCompleteListener: () -> Unit) {

    companion object {
        private val TAG = "UploadFileTask"
    }

    private val fileName = Util.getContentFileName(context, localFile)
    private val syncthingPath = PathUtils.buildPath(syncthingSubFolder, fileName)
    private val uploadStream = context.contentResolver.openInputStream(localFile)

    private lateinit var mProgressDialog: ProgressDialog
    private var mCancelled = false

    fun uploadFile() {
        createDialog()
        Log.i(TAG, "Uploading file $localFile to folder $syncthingFolder:$syncthingPath")
        try {
            syncthingClient.pushFile(uploadStream, syncthingFolder, syncthingPath, { observer ->
                onProgress(observer)
                try {
                    while (!observer.isCompleted()) {
                        if (mCancelled)
                            return@pushFile

                        observer.waitForProgressUpdate()
                        Log.i(TAG, "upload progress = ${observer.progressPercentage()}%")
                        onProgress(observer)
                    }
                } catch (e: InterruptedException) {
                    onError()
                }

                onComplete()
            }, { onError() })
        } catch (e: IOException) {
            Log.w(TAG, e)
            onError()
        }
    }

    private fun createDialog() {
        mProgressDialog = ProgressDialog(context)
        mProgressDialog.setMessage(context.getString(R.string.dialog_uploading_file, fileName))
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        mProgressDialog.setCancelable(true)
        mProgressDialog.setOnCancelListener { mCancelled = true }
        mProgressDialog.isIndeterminate = true
        mProgressDialog.show()
    }

    private fun onProgress(observer: BlockPusher.FileUploadObserver) {
        async(UI) {
            mProgressDialog.isIndeterminate = false
            mProgressDialog.progress = observer.progressPercentage()
            mProgressDialog.max = 100
        }
    }

    private fun onComplete() {
        IOUtils.closeQuietly(uploadStream)
        if (mCancelled)
            return

        Log.i(TAG, "Uploaded file $fileName to folder $syncthingFolder:$syncthingPath")
        async(UI) {
            mProgressDialog.dismiss()
            this@UploadFileTask.context.toast(R.string.toast_upload_complete)
            onUploadCompleteListener()
        }
    }

    private fun onError() {
        IOUtils.closeQuietly(uploadStream)
        async(UI) {
            mProgressDialog.dismiss()
            this@UploadFileTask.context.toast(R.string.toast_file_upload_failed)
        }
    }
}
