package net.syncthing.lite.utils;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import net.syncthing.lite.R;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

import it.anyplace.sync.bep.BlockPuller;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.FileInfo;

public class DownloadFileTask {

    private static final String TAG = "DownloadFileTask";

    private final Context mContext;
    private final SyncthingClient mSyncthingClient;
    private final FileInfo mFileInfo;
    private final Handler mMainHandler;

    private ProgressDialog mProgressDialog;
    private boolean mCancelled = false;

    public DownloadFileTask(Context context, SyncthingClient syncthingClient, FileInfo fileInfo) {
        mContext = context;
        mSyncthingClient = syncthingClient;
        mFileInfo = fileInfo;
        mMainHandler = new Handler();
    }

    public void downloadFile() {
        showDialog();
        // TODO: can just pass FileInfo directly?
        new Thread(() -> {
            mSyncthingClient.pullFile(mFileInfo.getFolder(), mFileInfo.getPath(), observer -> {
                onProgress(observer);
                try {
                    while (!observer.isCompleted()) {
                        if (mCancelled)
                            return;

                        observer.waitForProgressUpdate();
                        Log.i("pullFile", "download progress = " + observer.getProgressMessage());
                        onProgress(observer);
                    }

                    File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File outputFile = new File(outputDir, mFileInfo.getFileName());
                    FileUtils.copyInputStreamToFile(observer.getInputStream(), outputFile);
                    Log.i(TAG, "downloaded file = " + mFileInfo.getPath());
                    onComplete(outputFile);
                } catch (IOException | InterruptedException e) {
                    onError(R.string.toast_file_download_failed);
                    Log.w(TAG, "Failed to download file " + mFileInfo, e);
                }
            }, () -> onError(R.string.toast_file_download_failed));
        }).start();
    }

    private void showDialog() {
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage(mContext.getString(R.string.dialog_downloading_file, mFileInfo.getFileName()));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(dialogInterface -> mCancelled = true);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
    }

    private void onProgress(BlockPuller.FileDownloadObserver fileDownloadObserver) {
        mMainHandler.post(() -> {
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax((int) (long) mFileInfo.getSize());
            mProgressDialog.setProgress((int) (fileDownloadObserver.getProgress() * mFileInfo.getSize()));
        });
    }

    private void onComplete(File file) {
        mProgressDialog.dismiss();
        if (mCancelled)
            return;

        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FilenameUtils.getExtension(file.getName()));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), mimeType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            onError(R.string.toast_open_file_failed);
            Log.w(TAG, "No handler found for file " + file.getName(), e);
        }
    }

    private void onError(@StringRes int error) {
        mProgressDialog.dismiss();
        mMainHandler.post(() ->
                Toast.makeText(mContext, error, Toast.LENGTH_SHORT).show());
    }
}