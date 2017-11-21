package it.anyplace.syncbrowser.utils;


import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;

import it.anyplace.sync.bep.BlockPuller;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.FileInfo;

// TODO: this should be an IntentService
public class DownloadFileTask extends AsyncTask<Void, BlockPuller.FileDownloadObserver, Pair<File, Exception>> {

    private final Context mContext;
    private final SyncthingClient mSyncthingClient;
    private final FileInfo mFileInfo;
    private ProgressDialog mProgressDialog;
    private boolean cancelled;

    public DownloadFileTask(Context context, SyncthingClient syncthingClient, FileInfo fileInfo) {
        mContext = context;
        mSyncthingClient = syncthingClient;
        mFileInfo = fileInfo;
        cancelled = false;
    }

    @Override
    protected void onPreExecute() {
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage("downloading file " + mFileInfo.getFileName());
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(dialogInterface -> {
            cancelled = true;
            Toast.makeText(mContext, "download aborted by user", Toast.LENGTH_SHORT).show();
        });
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
    }

    @Override
    protected Pair<File, Exception> doInBackground(Void... voidd) {
        try {
            try (BlockPuller.FileDownloadObserver fileDownloadObserver = mSyncthingClient.pullFile(mFileInfo.getFolder(), mFileInfo.getPath())) {
                publishProgress(fileDownloadObserver);
                while (!fileDownloadObserver.isCompleted() && !cancelled) {
                    fileDownloadObserver.waitForProgressUpdate();
                    Log.i("pullFile", "download progress = " + fileDownloadObserver.getProgressMessage());
                    publishProgress(fileDownloadObserver);
                }
                if (cancelled) {
                    return null;
                }
                File outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outputFile = new File(outputDir, mFileInfo.getFileName());
                FileUtils.copyInputStreamToFile(fileDownloadObserver.getInputStream(), outputFile);
                Log.i("pullFile", "downloaded file = " + mFileInfo.getPath());
                return Pair.of(outputFile, null);
            }
        } catch (IOException | InterruptedException ex) {
            if (cancelled) {
                return null;
            }
            Log.e("pullFile", "file download exception", ex);
            return Pair.of(null, ex);
        }
    }

    @Override
    protected void onProgressUpdate(BlockPuller.FileDownloadObserver... fileDownloadObserver) {
        if (fileDownloadObserver[0].getProgress() > 0) {
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax((int) (long) mFileInfo.getSize());
            mProgressDialog.setProgress((int) (fileDownloadObserver[0].getProgress() * mFileInfo.getSize()));
        }
    }

    @Override
    protected void onPostExecute(Pair<File, Exception> res) {
        mProgressDialog.dismiss();
        if (cancelled)
            return;

        if (res.getLeft() == null) {
            Toast.makeText(mContext, "error downloading file: " + res.getRight(), Toast.LENGTH_LONG).show();
        } else {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FilenameUtils.getExtension(res.getLeft().getName()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Log.i("Main", "open file = " + res.getLeft().getName() + " (" + mimeType + ")");
            intent.setDataAndType(Uri.fromFile(res.getLeft()), mimeType);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                mContext.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, "no handler found for this file: " + res.getLeft().getName() + " (" + mimeType + ")", Toast.LENGTH_LONG).show();
            }
        }
    }
}