package it.anyplace.syncbrowser;

import android.app.AlertDialog;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import it.anyplace.sync.bep.FolderBrowser;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.configuration.ConfigurationService;
import it.anyplace.syncbrowser.databinding.DialogLoadingBinding;

public abstract class SyncbrowserActivity extends AppCompatActivity {

    private static final int SHUTDOWN_LIBRARY_DELAY = 60 * 1000;

    private static int mActivityCount = 0;
    private static LibraryHandler mLibraryHandler;

    private AlertDialog mLoadingDialog;

    @Override
    protected void onStart() {
        super.onStart();
        mActivityCount++;
        if (mLibraryHandler == null) {
            initLibrary();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mActivityCount--;
        new Handler().postDelayed(this::tryShutdownLibrary, SHUTDOWN_LIBRARY_DELAY);
    }

    private void initLibrary() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected void onPreExecute() {
                onLoadingLibrary();
            }

            @Override
            protected Void doInBackground(Void... voidd) {
                mLibraryHandler = new LibraryHandler();
                mLibraryHandler.init(SyncbrowserActivity.this);
                return null;
            }

            @Override
            protected void onPostExecute(Void voidd) {
                mLoadingDialog.cancel();
                mLibraryHandler.setOnIndexUpdatedListener(new LibraryHandler.OnIndexUpdatedListener() {
                    @Override
                    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
                        onIndexUpdateProgress(folder, percentage);
                    }

                    @Override
                    public void onIndexUpdateComplete() {
                        onIndexUpdateComplete();
                    }
                });
                onLibraryLoaded();
            }
        }.execute();
    }

    private void tryShutdownLibrary() {
        new Thread(() -> {
            if (mActivityCount == 0) {
                mLibraryHandler.destroy();
                mLibraryHandler = null;
            }
        }).start();
    }

    public SyncthingClient getSyncthingClient() {
        return mLibraryHandler.getSyncthingClient();
    }

    public ConfigurationService getConfiguration() {
        return mLibraryHandler.getConfiguration();
    }

    public FolderBrowser getFolderBrowser() {
        return mLibraryHandler.getFolderBrowser();
    }

    @SuppressWarnings("unused")
    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
    }

    @SuppressWarnings("unused")
    public void onIndexUpdateComplete() {
    }

    public void onLoadingLibrary() {
        DialogLoadingBinding binding = DataBindingUtil.inflate(
                getLayoutInflater(), R.layout.dialog_loading, null, false);
        binding.loadingText.setText("loading config, starting syncthing client");
        mLoadingDialog = new android.app.AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.getRoot())
                .show();
    }

    public void onLibraryLoaded() {
    }
}
