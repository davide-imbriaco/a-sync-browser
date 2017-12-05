package net.syncthing.lite.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import net.syncthing.lite.R;

import java.io.IOException;
import java.io.InputStream;

import it.anyplace.sync.bep.BlockPusher;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.utils.PathUtils;

// TODO: this should be an IntentService with notification
public class UploadFileTask {

    private static final String TAG = "UploadFileTask";

    public interface OnUploadCompleteListener {
        public void onUploadComplete();
    }

    private final Context mContext;
    private final SyncthingClient mSyncthingClient;
    private final String mSyncthingFolder;
    private final Uri mLocalFile;
    private final String mFileName;
    private final String mSyncthingPath;
    private final OnUploadCompleteListener mOnUploadCompleteListener;
    private final Handler mMainHandler;

    private ProgressDialog mProgressDialog;
    private boolean mCancelled = false;

    public UploadFileTask(Context context, SyncthingClient syncthingClient, Uri localFile,
                          String syncthingFolder, String syncthingSubFolder, OnUploadCompleteListener onUploadCompleteListener) {
        mContext = context;
        mSyncthingClient = syncthingClient;
        mSyncthingFolder = syncthingFolder;
        mLocalFile = localFile;
        mFileName = Util.getContentFileName(context, mLocalFile);
        mSyncthingPath = PathUtils.buildPath(syncthingSubFolder, mFileName);
        mOnUploadCompleteListener = onUploadCompleteListener;
        mMainHandler = new Handler();
    }

    public void uploadFile() {
        createDialog();
        Log.i(TAG, "Uploading file " + mLocalFile + " to folder " + mSyncthingFolder + ":" + mSyncthingPath);
        try {
            InputStream uploadStream = mContext.getContentResolver().openInputStream(mLocalFile);
            mSyncthingClient.pushFile(uploadStream, mSyncthingFolder, mSyncthingPath, observer -> {
                onProgress(observer);
                try {
                    while (!observer.isCompleted()) {
                        if (mCancelled)
                            return;

                        observer.waitForProgressUpdate();
                        Log.i(TAG, "upload progress = " + observer.getProgressMessage());
                        onProgress(observer);
                    }
                } catch (InterruptedException e) {
                    onError();
                }
                onComplete();
            }, this::onError);
        } catch (IOException e) {
            onError();
        }
    }

    private void createDialog() {
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage(mContext.getString(R.string.dialog_uploading_file, mFileName));
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(dialogInterface -> mCancelled = true);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
    }

    private void onProgress(BlockPusher.FileUploadObserver observer) {
        mMainHandler.post(() -> {
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax((int) observer.getDataSource().getSize());
            mProgressDialog.setProgress((int) (observer.getProgress() * observer.getDataSource().getSize()));
        });
    }

    private void onComplete() {
        mProgressDialog.dismiss();
        if (mCancelled)
            return;

        Log.i(TAG, "Uploaded file " + mFileName + " to folder " + mSyncthingFolder + ":" + mSyncthingPath);
        mMainHandler.post(() -> {
            Toast.makeText(mContext, R.string.toast_upload_complete, Toast.LENGTH_SHORT).show();
            mOnUploadCompleteListener.onUploadComplete();
        });
    }

    private void onError() {
        mProgressDialog.dismiss();
        mMainHandler.post(() ->
                Toast.makeText(mContext, R.string.toast_file_upload_failed, Toast.LENGTH_SHORT).show());
    }
}