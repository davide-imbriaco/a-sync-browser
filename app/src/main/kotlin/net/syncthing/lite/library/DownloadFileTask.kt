package net.syncthing.lite.library

import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.support.annotation.StringRes
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import net.syncthing.java.bep.BlockPuller
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.IOException

class DownloadFileTask(private val mContext: Context, private val mSyncthingClient: SyncthingClient,
                       private val mFileInfo: FileInfo) {
    private val mMainHandler: Handler = Handler()

    private lateinit var progressDialog: ProgressDialog
    private var cancelled = false

    fun downloadFile() {
        showDialog()
        // TODO: can just pass FileInfo directly?
        Thread {
            mSyncthingClient.pullFile(mFileInfo.folder, mFileInfo.path, { observer ->
                onProgress(observer)
                try {
                    while (!observer.isCompleted) {
                        if (cancelled)
                            return@pullFile

                        observer.waitForProgressUpdate()
                        Log.i("pullFile", "download progress = " + observer.progressMessage)
                        onProgress(observer)
                    }

                    val outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outputFile = File(outputDir, mFileInfo.fileName)
                    FileUtils.copyInputStreamToFile(observer.inputStream, outputFile)
                    Log.i(TAG, "downloaded file = " + mFileInfo.path)
                    onComplete(outputFile)
                } catch (e: IOException) {
                    onError(R.string.toast_file_download_failed)
                    Log.w(TAG, "Failed to download file " + mFileInfo, e)
                } catch (e: InterruptedException) {
                    onError(R.string.toast_file_download_failed)
                    Log.w(TAG, "Failed to download file " + mFileInfo, e)
                }
            }) { onError(R.string.toast_file_download_failed) }
        }.start()
    }

    private fun showDialog() {
        progressDialog = ProgressDialog(mContext)
        progressDialog.setMessage(mContext.getString(R.string.dialog_downloading_file, mFileInfo.fileName))
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.setCancelable(true)
        progressDialog.setOnCancelListener { cancelled = true }
        progressDialog.isIndeterminate = true
        progressDialog.show()
    }

    private fun onProgress(fileDownloadObserver: BlockPuller.FileDownloadObserver) {
        mMainHandler.post {
            progressDialog.isIndeterminate = false
            progressDialog.max = (mFileInfo.size as Long).toInt()
            progressDialog.progress = (fileDownloadObserver.progress * mFileInfo.size!!).toInt()
        }
    }

    private fun onComplete(file: File) {
        progressDialog.dismiss()
        if (cancelled)
            return

        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FilenameUtils.getExtension(file.name))
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(Uri.fromFile(file), mimeType)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            mContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            onError(R.string.toast_open_file_failed)
            Log.w(TAG, "No handler found for file " + file.name, e)
        }

    }

    private fun onError(@StringRes error: Int) {
        progressDialog.dismiss()
        mMainHandler.post { Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show() }
    }

    companion object {

        private val TAG = "DownloadFileTask"
    }
}