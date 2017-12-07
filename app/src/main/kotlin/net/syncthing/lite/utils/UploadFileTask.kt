package net.syncthing.lite.utils

import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.widget.Toast
import it.anyplace.sync.bep.BlockPusher
import it.anyplace.sync.client.SyncthingClient
import it.anyplace.sync.core.utils.PathUtils
import net.syncthing.lite.R
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
    private val mainHandler = Handler()

    private lateinit var mProgressDialog: ProgressDialog
    private var mCancelled = false

    fun uploadFile() {
        createDialog()
        Log.i(TAG, "Uploading file $localFile to folder $syncthingFolder:$syncthingPath")
        try {
            val uploadStream = context.contentResolver.openInputStream(localFile)
            syncthingClient.pushFile(uploadStream, syncthingFolder, syncthingPath, { observer ->
                onProgress(observer)
                try {
                    while (!observer.isCompleted) {
                        if (mCancelled)
                            return@pushFile

                        observer.waitForProgressUpdate()
                        Log.i(TAG, "upload progress = " + observer.progressMessage)
                        onProgress(observer)
                    }
                } catch (e: InterruptedException) {
                    onError()
                }

                onComplete()
            }, { this.onError() })
        } catch (e: IOException) {
            onError()
        }

    }

    private fun createDialog() {
        mProgressDialog = ProgressDialog(context)
        mProgressDialog.setMessage(context.getString(R.string.dialog_uploading_file, fileName))
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        mProgressDialog.setCancelable(true)
        mProgressDialog.setOnCancelListener { dialogInterface -> mCancelled = true }
        mProgressDialog.isIndeterminate = true
        mProgressDialog.show()
    }

    private fun onProgress(observer: BlockPusher.FileUploadObserver) {
        mainHandler.post {
            mProgressDialog.isIndeterminate = false
            mProgressDialog.max = observer.dataSource.size.toInt()
            mProgressDialog.progress = (observer.progress * observer.dataSource.size).toInt()
        }
    }

    private fun onComplete() {
        mProgressDialog.dismiss()
        if (mCancelled)
            return

        Log.i(TAG, "Uploaded file $fileName to folder $syncthingFolder:$syncthingPath")
        mainHandler.post {
            Toast.makeText(context, R.string.toast_upload_complete, Toast.LENGTH_SHORT).show()
            onUploadCompleteListener()
        }
    }

    private fun onError() {
        mProgressDialog.dismiss()
        mainHandler.post { Toast.makeText(context, R.string.toast_file_upload_failed, Toast.LENGTH_SHORT).show() }
    }
}