/*
 * Copyright (C) 2016 Davide Imbriaco
 *
 * This Java file is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package it.anyplace.syncbrowser;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nononsenseapps.filepicker.FilePickerActivity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import it.anyplace.sync.bep.IndexBrowser;
import it.anyplace.sync.core.beans.DeviceInfo;
import it.anyplace.sync.core.beans.DeviceStats;
import it.anyplace.sync.core.beans.FileInfo;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.beans.FolderStats;
import it.anyplace.sync.core.security.KeystoreHandler;
import it.anyplace.sync.core.utils.FileInfoOrdering;
import it.anyplace.sync.core.utils.PathUtils;
import it.anyplace.syncbrowser.filepicker.MIVFilePickerActivity;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.nullToEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_WRITE_STORAGE = 142;

    private LibraryHandler mLibraryHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_container);

        mLibraryHandler = new LibraryHandler();

        ((ListView) findViewById(R.id.main_folder_and_files_list_view)).setEmptyView(findViewById(R.id.main_list_view_empty_element));

        checkPermissions();

        findViewById(R.id.main_header_show_menu_button).setOnClickListener(view -> ((DrawerLayout) findViewById(R.id.main_drawer_layout)).openDrawer(Gravity.START));
        findViewById(R.id.main_header_show_devices_button).setOnClickListener(view -> ((DrawerLayout) findViewById(R.id.main_drawer_layout)).openDrawer(Gravity.END));
        findViewById(R.id.main_menu_add_device_qrcode_button).setOnClickListener(view -> {
            openQrcode();
            ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.START);
        });
        findViewById(R.id.devices_list_view_add_device_here_qrcode_button).setOnClickListener(view -> openQrcode());
        findViewById(R.id.main_menu_cleanup_button).setOnClickListener(view -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("clear cache and index")
                    .setMessage("clear all cache data and index data?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("yes", (dialog, which) -> cleanCacheAndIndex())
                    .setNegativeButton("no", null)
                    .show();
            ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.START);
        });
        findViewById(R.id.main_menu_update_index_button).setOnClickListener(view -> {
            updateIndexFromRemote();
            ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.START);
        });
        findViewById(R.id.main_list_view_upload_here_button).setOnClickListener(view -> showUploadHereDialog());
        ((DrawerLayout) findViewById(R.id.main_drawer_layout)).addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (drawerView.getId() == R.id.devices_right_drawer) {
                    updateDeviceList();
                }
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                if (newState == DrawerLayout.STATE_DRAGGING) {
                    if (!((DrawerLayout) findViewById(R.id.main_drawer_layout)).isDrawerOpen(Gravity.END)) {
                         updateDeviceList();
                    }
                }
            }
        });


        new AsyncTask<Void, Void, Void>() {


            @Override
            protected void onPreExecute() {
                updateMainProgressBar(true,"loading config, starting syncthing client");
            }

            @Override
            protected Void doInBackground(Void... voidd) {
                mLibraryHandler.init(MainActivity.this, new LibraryHandler.OnIndexUpdatedListener() {
                    @Override
                    public void onIndexUpdateProgress(FolderInfo folder, int percentage) {
                        runOnUiThread(() -> {
                            ((TextView) findViewById(R.id.main_index_progress_bar_label)).setText("index update, folder "
                                    + folder.getLabel() + " " + percentage + "% synchronized");
                                updateFolderListView();
                        });
                    }

                    @Override
                    public void onIndexUpdateComplete() {
                        runOnUiThread(() -> {
                            findViewById(R.id.main_index_progress_bar).setVisibility(View.GONE);
                            updateFolderListView();
                        });
                    }
                });
                return null;
            }

            @Override
            protected void onPostExecute(Void voidd) {
                updateMainProgressBar(false,null);
                if (mLibraryHandler.getSyncthingClient() == null) {
                    Toast.makeText(MainActivity.this, "error starting syncthing client", Toast.LENGTH_LONG).show();
                    MainActivity.this.finish();
                } else {
                    showAllFoldersListView();
                    Date lastUpdate = getLastIndexUpdateFromPref();
                    if (lastUpdate == null || new Date().getTime() - lastUpdate.getTime() > 10 * 60 * 1000) { //trigger update if last was more than 10mins ago
                        Log.d("onCreate", "trigger index update, last was " + lastUpdate);
                        updateIndexFromRemote();
                    }
                    initDeviceList();
                }
            }
        }.execute();
    }

    private void checkPermissions() {
        int permissionState = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
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
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void showUploadHereDialog() {
        if (indexBrowser == null) {
            Log.w(TAG, "showUploadHereDialog unable to open dialog, null index browser");
        } else {
            Intent i = new Intent(this, MIVFilePickerActivity.class);
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            Log.i(TAG, "showUploadHereDialog path = " + path);
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, path);
            startActivityForResult(i, 0);
        }
    }

    private void cleanCacheAndIndex() {
        mLibraryHandler.getSyncthingClient().clearCacheAndIndex();
        recreate();
    }

    private void updateButtonsVisibility() {
        findViewById(R.id.main_folder_and_files_list_view).setVisibility(View.VISIBLE);
        if (isBrowsingFolder) {
            findViewById(R.id.main_list_view_upload_here_button).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.main_list_view_upload_here_button).setVisibility(View.GONE);
        }
    }

    private void doUpload(final String syncthingFolder, final String syncthingSubFolder, final Uri fileToUpload) {
        new UploadFileTask(this, mLibraryHandler.getSyncthingClient(), fileToUpload, syncthingFolder,
                    syncthingSubFolder, this::updateFolderListView).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private IndexBrowser indexBrowser;
    private boolean isBrowsingFolder = false;
    private boolean indexUpdateInProgress = false;

    private void showAllFoldersListView() {
        Log.d(TAG, "showAllFoldersListView BEGIN");
        if (indexBrowser != null) {
            indexBrowser.close();
            indexBrowser = null;
        }
        ListView listView = findViewById(R.id.main_folder_and_files_list_view);
        List<Pair<FolderInfo, FolderStats>> list = Lists.newArrayList(mLibraryHandler.getFolderBrowser().getFolderInfoAndStatsList());
        Collections.sort(list, Ordering.natural().onResultOf(input -> input.getLeft().getLabel()));
        Log.i(TAG, "list folders = " + list + " (" + list.size() + " records");
        ArrayAdapter<Pair<FolderInfo, FolderStats>> adapter = new ArrayAdapter<Pair<FolderInfo, FolderStats>>(this, R.layout.listview_folder, list) {
            @NonNull
            @Override
            public View getView(int position, View v, @NonNull ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.listview_folder, parent, false);
                }
                FolderInfo folderInfo = getItem(position).getLeft();
                FolderStats folderStats = getItem(position).getRight();
                ((TextView) v.findViewById(R.id.folder_name)).setText(folderInfo.getLabel() + " (" + folderInfo.getFolder() + ")");
                ((TextView) v.findViewById(R.id.folder_lastmod_info)).setText(folderStats.getLastUpdate() == null ? "last modified: unknown" : ("last modified: " + DateUtils.getRelativeDateTimeString(MainActivity.this,folderStats.getLastUpdate().getTime(),DateUtils.MINUTE_IN_MILLIS,DateUtils.WEEK_IN_MILLIS,0)));
                ((TextView) v.findViewById(R.id.folder_content_info)).setText(folderStats.describeSize() + ", " + folderStats.getFileCount() + " files, " + folderStats.getDirCount() + " dirs");
                return v;
            }

        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((adapterView, view, position, l) -> {
            String folder = ((Pair<FolderInfo, FolderStats>) listView.getItemAtPosition(position)).getLeft().getFolder();
            showFolderListView(folder, null);
        });
        isBrowsingFolder = false;
        updateButtonsVisibility();
        ((TextView) findViewById(R.id.main_header_folder_label)).setText(R.string.app_name);
        Log.d(TAG, "showAllFoldersListView END");
    }

    private ArrayAdapter<FileInfo> createFileInfoArrayAdapter(){
        return new ArrayAdapter<FileInfo>(this, R.layout.listview_file, Lists.newArrayList()) {
            @NonNull
            @Override
            public View getView(int position, View v, @NonNull ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.listview_file, parent, false);
                }
                FileInfo fileInfo = getItem(position);
                ((TextView) v.findViewById(R.id.file_label)).setText(fileInfo.getFileName());
                if (fileInfo.isDirectory()) {
                    ((ImageView) v.findViewById(R.id.file_icon)).setImageResource(R.drawable.ic_folder_black_24dp);
                    v.findViewById(R.id.file_size).setVisibility(View.GONE);
                } else {
                    ((ImageView) v.findViewById(R.id.file_icon)).setImageResource(R.drawable.ic_image_black_24dp);
                    v.findViewById(R.id.file_size).setVisibility(View.VISIBLE);
                    ((TextView) v.findViewById(R.id.file_size)).setText(FileUtils.byteCountToDisplaySize(fileInfo.getSize())
                            +" - last modified "
                            + DateUtils.getRelativeDateTimeString(MainActivity.this,fileInfo.getLastModified().getTime(),DateUtils.MINUTE_IN_MILLIS,DateUtils.WEEK_IN_MILLIS,0));
                }
                return v;
            }
        };
    }

    private void showFolderListView(String folder, @Nullable String previousPath) {
        Log.d(TAG, "showFolderListView BEGIN");
        if (indexBrowser != null && equal(folder, indexBrowser.getFolder())) {
            Log.d(TAG, "reuse current index browser");
            indexBrowser.navigateToNearestPath(previousPath);
        } else {
            if (indexBrowser != null) {
                indexBrowser.close();
            }
            Log.d(TAG, "open new index browser");
            indexBrowser = mLibraryHandler.getSyncthingClient().getIndexHandler()
                    .newIndexBrowserBuilder()
                    .setOrdering(fileInfoOrdering)
                    .includeParentInList(true).allowParentInRoot(true)
                    .setFolder(folder)
                    .buildToNearestPath(previousPath);
        }
        ListView listView = findViewById(R.id.main_folder_and_files_list_view);
        ArrayAdapter<FileInfo> adapter = createFileInfoArrayAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((adapterView, view, position, l) -> {
            FileInfo fileInfo = (FileInfo) listView.getItemAtPosition(position);
            Log.d(TAG, "navigate to path = '" + fileInfo.getPath() + "' from path = '" + indexBrowser.getCurrentPath() + "'");
            navigateToFolder(fileInfo);
        });
        isBrowsingFolder = true;
        navigateToFolder(indexBrowser.getCurrentPathInfo());
        updateButtonsVisibility();
        Log.d(TAG, "showFolderListView END");
    }

    private void updateMainProgressBar(boolean visible, String message){
        findViewById(R.id.main_progress_bar_container).setVisibility(visible?View.VISIBLE:View.GONE);
        ((TextView) findViewById(R.id.main_progress_bar_label)).setText(nullToEmpty(message));
    }

    private void navigateToFolder(FileInfo fileInfo) {
        Log.d(TAG, "BEGIN");
        if (indexBrowser.isRoot() && PathUtils.isParent(fileInfo.getPath())) {
            showAllFoldersListView(); //navigate back to folder list
        } else {
            if (fileInfo.isDirectory()) {
                indexBrowser.navigateTo(fileInfo);
                FileInfo newFileInfo=PathUtils.isParent(fileInfo.getPath())?indexBrowser.getCurrentPathInfo():fileInfo;
                if (!indexBrowser.isCacheReadyAfterALittleWait()) {
                    Log.d(TAG, "load folder cache bg");
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected void onPreExecute() {
                            updateMainProgressBar(true,"open directory: " + (indexBrowser.isRoot() ? mLibraryHandler.getFolderBrowser().getFolderInfo(indexBrowser.getFolder()).getLabel() : indexBrowser.getCurrentPathFileName()));
                        }

                        @Override
                        protected Void doInBackground(Void... voids) {
                            indexBrowser.waitForCacheReady();
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            Log.d(TAG, "cache ready, navigate to folder");
                            updateMainProgressBar(false,null);
                            navigateToFolder(newFileInfo);
                        }
                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    List<FileInfo> list = indexBrowser.listFiles();
                    Log.i("navigateToFolder", "list for path = '" + indexBrowser.getCurrentPath() + "' list = " + list.size() + " records");
                    Log.d("navigateToFolder", "list for path = '" + indexBrowser.getCurrentPath() + "' list = " + list);
                    checkArgument(!list.isEmpty());//list must contain at least the 'parent' path
                    ListView listView = findViewById(R.id.main_folder_and_files_list_view);
                    ArrayAdapter<FileInfo> adapter = (ArrayAdapter<FileInfo>) listView.getAdapter();
                    adapter.clear();
                    adapter.addAll(list);
                    adapter.notifyDataSetChanged();
                    listView.setSelection(0);
                    ((TextView) findViewById(R.id.main_header_folder_label)).setText(indexBrowser.isRoot()
                            ?mLibraryHandler.getFolderBrowser().getFolderInfo(indexBrowser.getFolder()).getLabel()
                            :newFileInfo.getFileName());
                }
            } else {
                pullFile(fileInfo);
            }
        }
        Log.d(TAG, "navigate to folder END");
    }

    private void updateFolderListView() {
        Log.d(TAG, "updateFolderListView BEGIN");
        if (indexBrowser == null) {
            showAllFoldersListView();
        } else {
            showFolderListView(indexBrowser.getFolder(), indexBrowser.getCurrentPath());
        }
        Log.d(TAG, "updateFolderListView END");
    }

    private void initDeviceList() {
        ListView listView = findViewById(R.id.devices_list_view);
        listView.setEmptyView(findViewById(R.id.devices_list_view_empty_element));
        ArrayAdapter<DeviceStats> adapter = new ArrayAdapter<DeviceStats>(this, R.layout.listview_device, Lists.newArrayList()) {
            @NonNull
            @Override
            public View getView(int position, View v, @NonNull ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.listview_device, parent, false);
                }
                DeviceStats deviceStats = getItem(position);
                ((TextView) v.findViewById(R.id.device_name)).setText(deviceStats.getName());
                int color = 0;
                switch (deviceStats.getStatus()) {
                    case OFFLINE:
                        color = R.color.device_offline;
                        break;
                    case ONLINE_INACTIVE:
                        color = R.color.device_online_inactive;
                        break;
                    case ONLINE_ACTIVE:
                        color = R.color.device_online_active;
                        break;
                }
                // TODO: this is not working (and will also break on API 19
                ((ImageView) v.findViewById(R.id.device_icon))
                        .setColorFilter(ContextCompat.getColor(MainActivity.this, color), PorterDuff.Mode.SRC_IN);
                return v;
            }
        };
        listView.setAdapter(adapter);
//        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
//                FileInfo fileInfo= (FileInfo)listView.getItemAtPosition(position);
        //TODO show device detail
//            }
//        });
        listView.setOnItemLongClickListener((adapterView, view, position, l) -> {
            String deviceId = ((DeviceStats) listView.getItemAtPosition(position)).getDeviceId();
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("remove device " + deviceId.substring(0, 7))
                    .setMessage("remove device" + deviceId.substring(0, 7) + " from list of known devices?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton("yes", (dialog, which) -> {
                        mLibraryHandler.getConfiguration().edit().removePeer(deviceId).persistLater();
                        updateDeviceList();
                    })
                    .setNegativeButton("no", null)
                    .show();
            Log.d(TAG, "showFolderListView delete device = '" + deviceId + "'");
            return false;
        });
        updateDeviceList();
    }

    private final List<Comparator<FileInfo>> availableFileInfoOrderings=Collections.unmodifiableList(Arrays.asList(FileInfoOrdering.ALPHA_ASC_DIR_FIRST, FileInfoOrdering.LAST_MOD_DESC));
    private Comparator<FileInfo> fileInfoOrdering=availableFileInfoOrderings.iterator().next();

    private void updateDeviceList() {
        //TODO fix npe when opening drawer before app has fully started (no synclient)
        List<DeviceStats> list = Lists.newArrayList(mLibraryHandler.getSyncthingClient().getDevicesHandler().getDeviceStatsList());
        Collections.sort(list, new Comparator<DeviceStats>() {
            final Function<DeviceStats.DeviceStatus,Integer> fun= Functions.forMap(ImmutableMap.of(DeviceStats.DeviceStatus.OFFLINE,3, DeviceStats.DeviceStatus.ONLINE_INACTIVE,2, DeviceStats.DeviceStatus.ONLINE_ACTIVE,1));
            @Override
            public int compare(DeviceStats a, DeviceStats b) {
                return ComparisonChain.start().compare(fun.apply(a.getStatus()),fun.apply(b.getStatus())).compare(a.getName(),b.getName()).result();
            }
        });
        ListView listView = findViewById(R.id.devices_list_view);
        ((ArrayAdapter) listView.getAdapter()).clear();
        ((ArrayAdapter<DeviceStats>) listView.getAdapter()).addAll(list);
        ((ArrayAdapter) listView.getAdapter()).notifyDataSetChanged();
        listView.setSelection(0);
    }

    private void pullFile(final FileInfo fileInfo) {
        Log.i(TAG, "pulling file = " + fileInfo);
        new DownloadFileTask(this, mLibraryHandler.getSyncthingClient(), fileInfo).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private final static String LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS";

    private @Nullable Date getLastIndexUpdateFromPref() {
        long lastUpdate = getPreferences(MODE_PRIVATE).getLong(LAST_INDEX_UPDATE_TS_PREF, -1);
        if (lastUpdate < 0) {
            return null;
        } else {
            return new Date(lastUpdate);
        }
    }

    private void updateIndexFromRemote() {
        Log.d(TAG, "updateIndexFromRemote BEGIN");
        if (indexUpdateInProgress) {
            Toast.makeText(MainActivity.this, "index update already in progress", Toast.LENGTH_SHORT).show();
        } else {
            indexUpdateInProgress = true;
            new AsyncTask<Void, Void, Exception>() {
                private final View indexLoadingBar = (View) findViewById(R.id.main_index_progress_bar);

                @Override
                protected void onPreExecute() {
                    indexLoadingBar.setVisibility(View.VISIBLE);
                }

                @Override
                protected Exception doInBackground(Void... voidd) {
                    try {
                        mLibraryHandler.getSyncthingClient().waitForRemoteIndexAquired();
                        return null;
                    } catch (InterruptedException ex) {
                        Log.e(TAG, "index dump exception", ex);
                        return ex;
                    }
                }

                @Override
                protected void onPostExecute(Exception ex) {
                    indexLoadingBar.setVisibility(View.GONE);
                    if (ex != null) {
                        Toast.makeText(MainActivity.this, "error updating index: " + ex.toString(), Toast.LENGTH_LONG).show();
                    }
                    updateFolderListView();
                    indexUpdateInProgress = false;
                    getPreferences(MODE_PRIVATE).edit()
                            .putLong(LAST_INDEX_UPDATE_TS_PREF, new Date().getTime())
                            .apply();
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        Log.d(TAG, "updateIndexFromRemote END (running bg)");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        new Thread(() -> {
            mLibraryHandler.destroy();
            if (indexBrowser != null) {
                indexBrowser.close();
                indexBrowser = null;
            }
        }).start();
    }

    private void openQrcode() {
        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
        integrator.initiateScan();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check if this was a QR code scan.
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            String deviceId = scanResult.getContents();
            if (!isBlank(deviceId)) {
                Log.i(TAG, "qrcode text = " + deviceId);
                importDeviceId(deviceId);
            }
        }
        // Otherwise, it was a file upload.
        else {
            if (resultCode == Activity.RESULT_OK) {
                doUpload(indexBrowser.getFolder(), indexBrowser.getCurrentPath(), intent.getData());
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (indexBrowser != null) {
            ListView listView = findViewById(R.id.main_folder_and_files_list_view);
            listView.performItemClick(listView.getAdapter().getView(0, null, null), 0, listView.getItemIdAtPosition(0)); //click item '0', ie '..' (go to parent)
        } else {
            super.onBackPressed();
        }
    }

    private void importDeviceId(String deviceId) {
        KeystoreHandler.validateDeviceId(deviceId);
        boolean modified = mLibraryHandler.getConfiguration().edit().addPeers(new DeviceInfo(deviceId, null));
        if (modified) {
            mLibraryHandler.getConfiguration().edit().persistLater();
            Toast.makeText(this, "successfully imported device: " + deviceId, Toast.LENGTH_SHORT).show();
            updateDeviceList();//TODO remove this if event triggered (and handler trigger update)
            updateIndexFromRemote();
        } else {
            Toast.makeText(this, "device already present: " + deviceId, Toast.LENGTH_SHORT).show();
        }
    }
}
