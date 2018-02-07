package net.syncthing.lite.utils

import android.app.ProgressDialog
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.java.bep.BlockPusher
import net.syncthing.java.client.SyncthingClient
import net.syncthing.lite.R
import net.syncthing.lite.library.UploadFileTask
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast

class FileUploadDialog(private val context: Context, private val syncthingClient: SyncthingClient,
                       private val localFile: Uri, private val syncthingFolder: String,
                       syncthingSubFolder: String,
                       private val onUploadCompleteListener: () -> Unit) {

    private lateinit var progressDialog: ProgressDialog
    private var uploadFileTask: UploadFileTask? = null

    init {
        showDialog()
        doAsync {
            uploadFileTask = UploadFileTask(context, syncthingClient, localFile, syncthingFolder,
                    syncthingSubFolder, this@FileUploadDialog::onProgress,
                    this@FileUploadDialog::onComplete, this@FileUploadDialog::onError)
        }
    }

    private fun showDialog() {
        progressDialog = ProgressDialog(context)
        progressDialog.setMessage(context.getString(R.string.dialog_uploading_file, Util.getContentFileName(context, localFile)))
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setCancelable(true)
        progressDialog.setOnCancelListener { uploadFileTask?.cancel() }
        progressDialog.isIndeterminate = true
        progressDialog.show()
    }

    private fun onProgress(observer: BlockPusher.FileUploadObserver) {
        async(UI) {
            progressDialog.isIndeterminate = false
            progressDialog.progress = observer.progressPercentage()
            progressDialog.max = 100
        }
    }

    private fun onComplete() {
        async(UI) {
            progressDialog.dismiss()
            this@FileUploadDialog.context.toast(R.string.toast_upload_complete)
            onUploadCompleteListener()
        }
    }

    private fun onError() {
        async(UI) {
            progressDialog.dismiss()
            this@FileUploadDialog.context.toast(R.string.toast_file_upload_failed)
        }
    }
}