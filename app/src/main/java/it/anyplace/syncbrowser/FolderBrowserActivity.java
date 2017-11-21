package it.anyplace.syncbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.nononsenseapps.filepicker.FilePickerActivity;

import java.util.List;

import it.anyplace.sync.bep.IndexBrowser;
import it.anyplace.sync.core.beans.FileInfo;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.utils.FileInfoOrdering;
import it.anyplace.sync.core.utils.PathUtils;
import it.anyplace.syncbrowser.adapters.FolderContentsAdapter;
import it.anyplace.syncbrowser.databinding.ActivityFolderBrowserBinding;
import it.anyplace.syncbrowser.databinding.DialogLoadingBinding;
import it.anyplace.syncbrowser.filepicker.MIVFilePickerActivity;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkArgument;

public class FolderBrowserActivity extends SyncbrowserActivity {

    private static final String TAG = "FolderBrowserActivity";
    private static final int REQUEST_WRITE_STORAGE = 142;

    public static final String EXTRA_FOLDER_NAME = "folder_name";

    private ActivityFolderBrowserBinding mBinding;
    private IndexBrowser indexBrowser;
    private AlertDialog mLoadingDialog;
    private FolderContentsAdapter mAdapter;
    private Runnable mRunWhenPermissionsReceived;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_folder_browser);
        mBinding.mainListViewUploadHereButton.setOnClickListener(v -> showUploadHereDialog());
        showFolderListView(getIntent().getStringExtra(EXTRA_FOLDER_NAME), null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        new Thread(() -> {
            if (indexBrowser != null) {
                indexBrowser.close();
                indexBrowser = null;
            }
        }).start();
        cancelLoadingDialog();
    }

    @Override
    public void onBackPressed() {
        ListView listView = mBinding.mainFolderAndFilesListView;
        //click item '0', ie '..' (go to parent)
        listView.performItemClick(mAdapter.getView(0, null, null), 0, listView.getItemIdAtPosition(0));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) {
            new UploadFileTask(this, getSyncthingClient(), intent.getData(), indexBrowser.getCurrentPath(),
                    indexBrowser.getFolder(), this::updateFolderListView).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private void showLoadingDialog(String message) {
        DialogLoadingBinding binding = DataBindingUtil.inflate(
                getLayoutInflater(), R.layout.dialog_loading, null, false);
        binding.loadingText.setText(message);
        mLoadingDialog = new android.app.AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(binding.getRoot())
                .show();
    }

    private void cancelLoadingDialog() {
        if (mLoadingDialog != null) {
            mLoadingDialog.cancel();
            mLoadingDialog = null;
        }
    }

    private void showFolderListView(String folder, @Nullable String previousPath) {
        if (indexBrowser != null && equal(folder, indexBrowser.getFolder())) {
            Log.d(TAG, "reuse current index browser");
            indexBrowser.navigateToNearestPath(previousPath);
        } else {
            if (indexBrowser != null) {
                indexBrowser.close();
            }
            Log.d(TAG, "open new index browser");
            indexBrowser = getSyncthingClient().getIndexHandler()
                    .newIndexBrowserBuilder()
                    .setOrdering(FileInfoOrdering.ALPHA_ASC_DIR_FIRST)
                    .includeParentInList(true).allowParentInRoot(true)
                    .setFolder(folder)
                    .buildToNearestPath(previousPath);
        }
        mAdapter = new FolderContentsAdapter(this);
        mBinding.mainFolderAndFilesListView.setAdapter(mAdapter);
        mBinding.mainFolderAndFilesListView.setOnItemClickListener((adapterView, view, position, l) -> {
            FileInfo fileInfo = (FileInfo) mBinding.mainFolderAndFilesListView.getItemAtPosition(position);
            Log.d(TAG, "navigate to path = '" + fileInfo.getPath() + "' from path = '" + indexBrowser.getCurrentPath() + "'");
            navigateToFolder(fileInfo);
        });
        navigateToFolder(indexBrowser.getCurrentPathInfo());
    }

    private void navigateToFolder(FileInfo fileInfo) {
        if (indexBrowser.isRoot() && PathUtils.isParent(fileInfo.getPath())) {
            finish();
        } else {
            if (fileInfo.isDirectory()) {
                indexBrowser.navigateTo(fileInfo);
                FileInfo newFileInfo = PathUtils.isParent(fileInfo.getPath())?indexBrowser.getCurrentPathInfo():fileInfo;
                if (!indexBrowser.isCacheReadyAfterALittleWait()) {
                    Log.d(TAG, "load folder cache bg");
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected void onPreExecute() {
                            // TODO: show ProgressBar in ListView instead of dialog
                            showLoadingDialog("open directory: " + (indexBrowser.isRoot() ? getFolderBrowser().getFolderInfo(indexBrowser.getFolder()).getLabel() : indexBrowser.getCurrentPathFileName()));
                        }

                        @Override
                        protected Void doInBackground(Void... voids) {
                            indexBrowser.waitForCacheReady();
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            Log.d(TAG, "cache ready, navigate to folder");
                            cancelLoadingDialog();
                            navigateToFolder(newFileInfo);
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    List<FileInfo> list = indexBrowser.listFiles();
                    Log.i("navigateToFolder", "list for path = '" + indexBrowser.getCurrentPath() + "' list = " + list.size() + " records");
                    Log.d("navigateToFolder", "list for path = '" + indexBrowser.getCurrentPath() + "' list = " + list);
                    checkArgument(!list.isEmpty());//list must contain at least the 'parent' path
                    mAdapter.clear();
                    mAdapter.addAll(list);
                    mAdapter.notifyDataSetChanged();
                    mBinding.mainFolderAndFilesListView.setSelection(0);
                    getSupportActionBar().setTitle(indexBrowser.isRoot()
                            ?getFolderBrowser().getFolderInfo(indexBrowser.getFolder()).getLabel()
                            :newFileInfo.getFileName());
                }
            } else {
                Log.i(TAG, "pulling file = " + fileInfo);
                executeWithPermissions(() -> {
                    new DownloadFileTask(this, getSyncthingClient(), fileInfo)
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                });
            }
        }
    }

    private void updateFolderListView() {
        showFolderListView(indexBrowser.getFolder(), indexBrowser.getCurrentPath());
    }

    private void showUploadHereDialog() {
        executeWithPermissions(() -> {
            Intent i = new Intent(this, MIVFilePickerActivity.class);
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            Log.i(TAG, "showUploadHereDialog path = " + path);
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, path);
            startActivityForResult(i, 0);
        });
    }

    @Override
    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
        mBinding.mainIndexProgressBarLabel.setText("index update, folder "
                + folder.getLabel() + " " + percentage + "% synchronized");
        updateFolderListView();
    }

    @Override
    public void onIndexUpdateComplete() {
        mBinding.mainIndexProgressBar.setVisibility(View.GONE);
        updateFolderListView();
    }

    private void executeWithPermissions(Runnable runnable) {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
            mRunWhenPermissionsReceived = runnable;
        } else {
            runnable.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.toast_write_storage_permission_required,
                            Toast.LENGTH_LONG).show();
                } else {
                    mRunWhenPermissionsReceived.run();
                    mRunWhenPermissionsReceived = null;
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
