package it.anyplace.syncbrowser;


import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;

import it.anyplace.sync.bep.BlockPusher;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.utils.PathUtils;

// TODO: this should be an IntentService
public class UploadFileTask extends AsyncTask<Void, BlockPusher.FileUploadObserver, Exception> {

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
    private OnUploadCompleteListener mOnUploadCompleteListener;

    private ProgressDialog progressDialog;
    private boolean cancelled = false;

    public UploadFileTask(Context context, SyncthingClient syncthingClient, Uri localFile,
                          String syncthingFolder, String syncthingSubFolder, OnUploadCompleteListener onUploadCompleteListener) {
        mContext = context;
        mSyncthingClient = syncthingClient;
        mSyncthingFolder = syncthingFolder;
        mLocalFile = localFile;
        mFileName = Util.getContentFileName(context, mLocalFile);
        mSyncthingPath = PathUtils.buildPath(syncthingSubFolder, mFileName);
        mOnUploadCompleteListener = onUploadCompleteListener;
    }

    @Override
    protected void onPreExecute() {
        Log.i(TAG, "upload of file " + mLocalFile + " to folder " + mSyncthingFolder + ":" + mSyncthingPath);
        progressDialog = new ProgressDialog(mContext);
        progressDialog.setMessage("uploading file " + mFileName);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(true);
        progressDialog.setOnCancelListener(dialogInterface -> {
            cancelled = true;
            Log.d(TAG, "upload aborted by user");
        });
        progressDialog.setIndeterminate(true);
        progressDialog.show();
    }

    @Override
    protected Exception doInBackground(Void... voidd) {
        try {
            try (BlockPusher.FileUploadObserver observer = mSyncthingClient.pushFile(mContext.getContentResolver().openInputStream(mLocalFile), mSyncthingFolder, mSyncthingPath)) {
                Log.i(TAG, "pushing file " + mFileName + " to folder " + mSyncthingFolder + ":" + mSyncthingPath);
                publishProgress(observer);
                while (!observer.isCompleted()) {
                    if (cancelled) {
                        return null;
                    }

                    observer.waitForProgressUpdate();
                    Log.i(TAG, "upload progress = " + observer.getProgressMessage());
                    publishProgress(observer);
                }
                return null;
            }
        } catch (InterruptedException | IOException ex) {
            Log.e(TAG, "file upload exception", ex);
            return ex;
        }
    }

    @Override
    protected void onProgressUpdate(BlockPusher.FileUploadObserver... observer) {
        if (observer[0].getProgress() > 0) {
            progressDialog.setIndeterminate(false);
            progressDialog.setMax((int) observer[0].getDataSource().getSize());
            progressDialog.setProgress((int) (observer[0].getProgress() * observer[0].getDataSource().getSize()));
        }
    }

    @Override
    protected void onPostExecute(Exception res) {
        progressDialog.dismiss();
        if (cancelled) {
            mOnUploadCompleteListener.onUploadComplete();
            return;
        }

        if (res != null) {
            Toast.makeText(mContext, "error uploading file: " + res, Toast.LENGTH_LONG).show();
        } else {
            Log.i(TAG, "uploaded file " + mFileName + " to folder " + mSyncthingFolder + ":" + mSyncthingPath);
            Toast.makeText(mContext, "uploaded file: " + mFileName, Toast.LENGTH_SHORT).show();
            mOnUploadCompleteListener.onUploadComplete();
        }
    }
}