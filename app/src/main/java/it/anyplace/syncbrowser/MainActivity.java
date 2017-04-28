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
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.nononsenseapps.filepicker.FilePickerActivity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.Text;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import it.anyplace.sync.bep.BlockPuller;
import it.anyplace.sync.bep.BlockPusher;
import it.anyplace.sync.bep.FolderBrowser;
import it.anyplace.sync.bep.IndexBrowser;
import it.anyplace.sync.bep.IndexFinder;
import it.anyplace.sync.bep.IndexHandler;
import it.anyplace.sync.client.SyncthingClient;
import it.anyplace.sync.core.beans.DeviceInfo;
import it.anyplace.sync.core.beans.DeviceStats;
import it.anyplace.sync.core.beans.FileInfo;
import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.beans.FolderStats;
import it.anyplace.sync.core.beans.IndexInfo;
import it.anyplace.sync.core.configuration.ConfigurationService;
import it.anyplace.sync.core.security.KeystoreHandler;
import it.anyplace.sync.core.utils.FileInfoOrdering;
import it.anyplace.sync.core.utils.PathUtils;
import it.anyplace.syncbrowser.filepicker.MIVFilePickerActivity;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.nullToEmpty;
import static it.anyplace.syncbrowser.utils.ViewUtils.listViews;
import static org.apache.commons.io.FileUtils.getFile;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;

//TODO move interface code to fragment
public class MainActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private ConfigurationService configuration;
    private SyncthingClient syncthingClient;
    private Exception statupError;

    private void closeClient() {
        if (indexBrowser != null) {
            indexBrowser.close();
            indexBrowser = null;
        }
        if (folderBrowser != null) {
            folderBrowser.close();
            folderBrowser = null;
        }
        if (syncthingClient != null) {
            syncthingClient.close();
            syncthingClient = null;
        }
    }

    private void initClient() {
        closeClient();
        try {
            configuration = ConfigurationService.newLoader()
                    .setCache(new File(getExternalCacheDir(), "cache"))
                    .setDatabase(new File(getExternalFilesDir(null), "database"))
                    .loadFrom(new File(getExternalFilesDir(null), "config.properties"));
            configuration.edit().setDeviceName(getDeviceName());
            FileUtils.cleanDirectory(configuration.getTemp());
            KeystoreHandler keystoreHandler = KeystoreHandler.newLoader().loadAndStore(configuration);
            configuration.edit().persistLater();
            Log.i("initClient", "loaded configuration = " + configuration.newWriter().dumpToString());
            Log.i("initClient", "storage space = " + configuration.getStorageInfo().dumpAvailableSpace());
            syncthingClient = new SyncthingClient(configuration);
            syncthingClient.getIndexHandler().getEventBus().register(new Object() {

                @Subscribe
                public void handleIndexRecordAquiredEvent(IndexHandler.IndexRecordAquiredEvent event) {
                    final String label = syncthingClient.getIndexHandler().getFolderInfo(event.getFolder()).getLabel();
                    final IndexInfo indexInfo = event.getIndexInfo();
                    final long count = event.getNewRecords().size();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("handleIndexRecordEvent", "trigger folder list update from index record acquired");
                            ((TextView) findViewById(R.id.main_index_progress_bar_label)).setText("index update, folder "
                                    + label + " " + ((int) (indexInfo.getCompleted() * 100)) + "% synchronized");
                            updateFolderListView();
                        }
                    });
                }

                @Subscribe
                public void handleRemoteIndexAquiredEvent(IndexHandler.RemoteIndexAquiredEvent event) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("handleIndexAquiredEvent", "trigger folder list update from index acquired");
                            findViewById(R.id.main_index_progress_bar).setVisibility(View.GONE);
                            updateFolderListView();
                        }
                    });
                }
            });
            //TODO listen for device events, update device list
            folderBrowser = syncthingClient.getIndexHandler().newFolderBrowser();
            statupError = null;
        } catch (Exception ex) {
            Log.e("Main", "error", ex);
            statupError = ex;
            closeClient();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        Log.i("MainActivity", "onRequestPermissionsResult: "+Joiner.on(",").join(permissions)+" -> "+Joiner.on(",").join(Arrays.asList(grantResults)));
        Intent intent = getBaseContext().getPackageManager().getLaunchIntentForPackage( getBaseContext().getPackageName() );
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Log.i("MainActivity","onRequestPermissionsResult: restart app for new permissions");
        finish();
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("onCreate", "BEGIN");
        super.onCreate(savedInstanceState);


        {
            Log.i("MainActivity", "check permissions BEGIN");
            List<String> requests = Lists.newArrayList();
            for (String requiredPermission : Arrays.asList(
                    Manifest.permission.INTERNET,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                int permissionCheck = ContextCompat.checkSelfPermission(this, requiredPermission);
                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    Log.i("MainActivity.onCreate", "app is missing permission " + requiredPermission + ", sending request");
                    requests.add(requiredPermission);
                }
            }
            if (!requests.isEmpty()) {
                ActivityCompat.requestPermissions(MainActivity.this, requests.toArray(new String[]{}), 13);
            }
            Log.i("MainActivity", "check permissions END");
        }

        setContentView(R.layout.main_container);

        ((ListView) findViewById(R.id.main_folder_and_files_list_view)).setEmptyView(findViewById(R.id.main_list_view_empty_element));

        checkPermissions();

        ((View) findViewById(R.id.main_header_sort_order_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleFileSort();
            }
        });
        ((View) findViewById(R.id.main_header_show_menu_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((DrawerLayout) findViewById(R.id.main_drawer_layout)).openDrawer(Gravity.LEFT);
            }
        });
        ((View) findViewById(R.id.main_header_show_devices_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((DrawerLayout) findViewById(R.id.main_drawer_layout)).openDrawer(Gravity.RIGHT);
            }
        });
        ((View) findViewById(R.id.main_menu_exit_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.LEFT);
            }
        });
        ((View) findViewById(R.id.main_menu_add_device_qrcode_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openQrcode();
                ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.LEFT);
            }
        });
        ((View) findViewById(R.id.devices_list_view_add_device_here_qrcode_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openQrcode();
            }
        });
        ((View) findViewById(R.id.main_menu_cleanup_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("clear cache and index")
                        .setMessage("clear all cache data and index data?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("yes", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                cleanCacheAndIndex();
                            }
                        })
                        .setNegativeButton("no", null)
                        .show();
                ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.LEFT);
            }
        });
        ((View) findViewById(R.id.main_menu_update_index_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateIndexFromRemote();
                ((DrawerLayout) findViewById(R.id.main_drawer_layout)).closeDrawer(Gravity.LEFT);
            }
        });
        ((View) findViewById(R.id.main_list_view_upload_here_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showUploadHereDialog();
            }
        });
        ((View) findViewById(R.id.main_header_search_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterSearchMode();
            }
        });
        ((View) findViewById(R.id.main_search_bar_close_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitSearchMode();
            }
        });
        ((EditText) findViewById(R.id.main_search_bar_input_field)).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                final String text=editable.toString();
                if(indexFinder!=null && !StringUtils.isBlank(text) && text.trim().length()>2) {
                    new AsyncTask<Void,Void,Void>(){

                        @Override
                        protected Void doInBackground(Void... voids) {
                            try {
                                Thread.sleep(750);
                            } catch (InterruptedException e) {
                            }
                            return null;
                        }
                        @Override
                        protected void onPostExecute(Void aVoid) {
                            String newText=((EditText) findViewById(R.id.main_search_bar_input_field)).getText().toString();
                            if(equal(text,newText)) {
                                findViewById(R.id.main_search_progress_bar).setVisibility(View.VISIBLE);
                                indexFinder.submitSearch(text);
                            }
                        }
                    }.execute();

                }
            }
        });
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
                    if (!((DrawerLayout) findViewById(R.id.main_drawer_layout)).isDrawerOpen(Gravity.RIGHT)) {
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
                initClient();
                return null;
            }

            @Override
            protected void onPostExecute(Void voidd) {
                updateMainProgressBar(false,null);
                if (syncthingClient == null) {
                    Toast.makeText(MainActivity.this, "error starting syncthing client: " + statupError, Toast.LENGTH_LONG).show();
                } else {
                    restoreBrowserFolderFromPref();
                    Date lastUpdate = getLastIndexUpdateFromPref();
                    if (lastUpdate == null || new Date().getTime() - lastUpdate.getTime() > 10 * 60 * 1000) { //trigger update if last was more than 10mins ago
                        Log.d("onCreate", "trigger index update, last was " + lastUpdate);
                        updateIndexFromRemote();
                    }
                    initDeviceList();
                }
            }
        }.execute();

        Log.i("onCreate", "app ready, scanning intent");
        Intent intent = getIntent();
        if (equal(Intent.ACTION_SEND, intent.getAction())) {
            handleSendSingle(intent);
        } else if (equal(Intent.ACTION_SEND_MULTIPLE, intent.getAction())) {
            handleSendMany(intent);
        }
        Log.i("onCreate", "END");
    }

    private boolean checkPermissions() {
        List<String> missingPermissions = Lists.newArrayList();
        for (String permission : Arrays.asList(Manifest.permission.INTERNET, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            int permissionCheck = ContextCompat.checkSelfPermission(this, permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[]{}), 0);
            return false;
        } else {
            return true;
        }
        //TODO handle response
    }

    private void showUploadHereDialog() {
        if (indexBrowser == null) {
            Log.w("showUploadHereDialog", "unable to open dialog, null index browser");
        } else {
            Intent i = new Intent(this, MIVFilePickerActivity.class);
            String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            Log.i("showUploadHereDialog", "path = " + path);
            i.putExtra(FilePickerActivity.EXTRA_START_PATH, path);
            startActivityForResult(i, 0);
        }
    }

    private void cleanCacheAndIndex() {
        if (syncthingClient != null) {
            syncthingClient.clearCacheAndIndex();
            recreate();
        }
    }

    private void handleSendSingle(Intent intent) {
        Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (fileUri != null) {
            handleSend(Collections.singletonList(fileUri));
        }
    }

    private void handleSendMany(Intent intent) {
        List<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (fileUris != null && !fileUris.isEmpty()) {
            handleSend(fileUris);
        }
    }

    private void updateButtonsVisibility() {
        if(searchModeOn){
            findViewById(R.id.main_header_sort_order_button).setVisibility(View.VISIBLE);
            findViewById(R.id.file_upload_intent_footer).setVisibility(View.GONE);
            findViewById(R.id.main_folder_and_files_list_view).setVisibility(View.GONE);
            findViewById(R.id.main_list_view_upload_here_button).setVisibility(View.GONE);
            findViewById(R.id.main_search_results_list_view).setVisibility(View.VISIBLE);
            findViewById(R.id.main_search_bar).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.main_folder_and_files_list_view).setVisibility(View.VISIBLE);
            findViewById(R.id.main_search_results_list_view).setVisibility(View.GONE);
            findViewById(R.id.main_search_bar).setVisibility(View.GONE);
            if (isHandlingUploadIntent) {
                findViewById(R.id.main_header_search_button).setVisibility(View.GONE);
                findViewById(R.id.main_list_view_upload_here_button).setVisibility(View.GONE);
                findViewById(R.id.file_upload_intent_footer).setVisibility(View.VISIBLE);//todo set button disabled if not in folder
                if (isBrowsingFolder) {
                    ((TextView) findViewById(R.id.file_upload_intent_footer_confirm_button)).setEnabled(true);
                } else {
                    ((TextView) findViewById(R.id.file_upload_intent_footer_confirm_button)).setEnabled(false);
                }
            } else {
                findViewById(R.id.main_header_sort_order_button).setVisibility(View.VISIBLE);
                if (isBrowsingFolder) {
                    findViewById(R.id.main_list_view_upload_here_button).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.main_list_view_upload_here_button).setVisibility(View.GONE);
                }
                findViewById(R.id.file_upload_intent_footer).setVisibility(View.GONE);
            }
            if (isBrowsingFolder) {
                findViewById(R.id.main_header_search_button).setVisibility(View.VISIBLE);
            } else {
                findViewById(R.id.main_header_sort_order_button).setVisibility(View.GONE);
            }
        }
    }

    private IndexFinder indexFinder;
    private IndexFinder.SearchCompletedEvent searchCompletedEvent;

    private void enterSearchMode(){
        Log.i("Main","enterSearchMode");
        searchModeOn=true;
        searchCompletedEvent=null;
        indexFinder=syncthingClient.getIndexHandler().newIndexFinderBuilder().build();
        indexFinder.setOrdering(fileInfoOrdering);
        indexFinder.getEventBus().register(new Object(){
            @Subscribe
            public void handleSearchCompletedEvent(IndexFinder.SearchCompletedEvent event){
                runOnUiThread(new Runnable(){
                    public void run(){
                        String term = ((EditText) findViewById(R.id.main_search_bar_input_field)).getText().toString();
                        if(equal(event.getQuery(),term)){
                            searchCompletedEvent=event;
                            updateSearchResultListView();
                            findViewById(R.id.main_search_progress_bar).setVisibility(View.GONE);
                        }
                    }
                });
            }
        });

        ListView listView = (ListView) findViewById(R.id.main_search_results_list_view);
        listView.setEmptyView(findViewById(R.id.main_search_results_empty_element));
        ArrayAdapter adapter = createFileInfoArrayAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                FileInfo fileInfo = (FileInfo) listView.getItemAtPosition(position);
                if(fileInfo.isDirectory()){
                    exitSearchMode();
                    showFolderListView(fileInfo.getFolder(),fileInfo.getPath());
                }else if(fileInfo.isFile()){
                    pullFile(fileInfo);
                }
            }
        });

        updateButtonsVisibility();
        updateSearchResultListView();
    }

    private void exitSearchMode(){
        Log.i("Main","exitSearchMode");
        searchModeOn=false;
        ListView listView = (ListView) findViewById(R.id.main_search_results_list_view);
        listView.setEmptyView(null);
        ((TextView)findViewById(R.id.main_search_results_empty_element)).setVisibility(View.GONE);
        listView.setAdapter(null);
        updateButtonsVisibility();
        indexFinder.close();
        indexFinder=null;
        searchCompletedEvent=null;
    }

    private void updateSearchResultListView(){
        ListView listView = (ListView) findViewById(R.id.main_search_results_list_view);
        ArrayAdapter<FileInfo> arrayAdapter=((ArrayAdapter)listView.getAdapter());
        arrayAdapter.clear();
        if(searchCompletedEvent==null || searchCompletedEvent.hasZeroResults()){
            Log.i("Main", "updateSearchResultListView, no result");
            ((TextView)findViewById(R.id.main_search_results_empty_element)).setText(R.string.no_search_result_message);
        }else if(searchCompletedEvent.hasTooManyResults()) {
            Log.i("Main", "updateSearchResultListView, too many results");
            ((TextView)findViewById(R.id.main_search_results_empty_element)).setText(R.string.too_many_search_results_message);
        }else{
            List<FileInfo> list = searchCompletedEvent.getResultList();
            Log.i("Main", "updateSearchResultListView, result count = " + list.size());
            arrayAdapter.addAll(list);
        }
        arrayAdapter.notifyDataSetChanged();
        listView.setSelection(0);
    }

    private List<Uri> filesToUpload;

    private void handleSend(List<Uri> list) {
        Log.i("Main", "handle send of files = " + list);
        isHandlingUploadIntent = true;
        filesToUpload = list;
        updateButtonsVisibility();
        ((TextView) findViewById(R.id.file_upload_intent_footer_label)).setText(Joiner.on(", ").join(Iterables.transform(list, new Function<Uri, String>() {

            @Override
            public String apply(Uri input) {
                return getContentFileName(input);
            }
        })));
        ((TextView) findViewById(R.id.file_upload_intent_footer_confirm_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (indexBrowser != null) {
                    doUpload(indexBrowser.getFolder(), indexBrowser.getCurrentPath(), filesToUpload);
                    filesToUpload = null;
                    isHandlingUploadIntent = false;
                    updateButtonsVisibility();
                } else {
                    Toast.makeText(MainActivity.this, "choose a folder for upload", Toast.LENGTH_SHORT).show();
                }
            }
        });
        ((TextView) findViewById(R.id.file_upload_intent_footer_cancel_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filesToUpload = null;
                isHandlingUploadIntent = false;
                updateButtonsVisibility();
                Toast.makeText(MainActivity.this, "file upload cancelled", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getContentFileName(Uri contentUri) {
        String fileName = contentUri.getLastPathSegment();
        if (equal(contentUri.getScheme(), "content")) {
            try (Cursor cursor = MainActivity.this.getContentResolver().query(contentUri, new String[]{MediaStore.Images.Media.DATA}, null, null, null)) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(column_index);
                Log.d("Main", "recovered 'content' uri real path = " + path);
                fileName = Uri.parse(path).getLastPathSegment();
            }
        }
        return fileName;
    }

    private void doUpload(final String folder, final String dir, final List<Uri> filesToUpload) {
        if (!filesToUpload.isEmpty()) {
            new AsyncTask<Void, BlockPusher.FileUploadObserver, Exception>() {
                private ProgressDialog progressDialog;
                private Thread thread;
                private boolean cancelled = false;
                private Uri fileToUpload = filesToUpload.iterator().next();
                private List<Uri> nextFilesToUpload = filesToUpload.subList(1, filesToUpload.size());
                private String fileName = getContentFileName(fileToUpload);
                private String path = PathUtils.buildPath(dir, fileName);

                @Override
                protected void onPreExecute() {
                    Log.i("doUpload", "upload of file " + fileName + " to folder " + folder + ":" + dir);
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setMessage("uploading file " + fileName);
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setCancelable(true);
                    progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            cancelled = true;
                            if (thread != null) {
                                thread.interrupt();
                            }
                            Toast.makeText(MainActivity.this, "upload aborted by user", Toast.LENGTH_SHORT).show();
                        }
                    });
                    progressDialog.setIndeterminate(true);
                    progressDialog.show();
                }

                @Override
                protected Exception doInBackground(Void... voidd) {
                    try {
                        try (BlockPusher.FileUploadObserver observer = syncthingClient.pushFile(getContentResolver().openInputStream(fileToUpload), folder, path)) {
                            Log.i("doUpload", "pushing file " + fileName + " to folder " + folder + ":" + dir);
                            publishProgress(observer);
                            while (!observer.isCompleted() && !cancelled) {
                                observer.waitForProgressUpdate();
                                Log.i("Main", "upload progress = " + observer.getProgressMessage());
                                publishProgress(observer);
                            }
                            if (cancelled) {
                                return null;
                            }
                            Log.i("Main", "uploaded file = " + path);
                            return null;
                        }
                    } catch (Exception ex) {
                        if (cancelled) {
                            return null;
                        }
                        Log.e("Main", "file upload exception", ex);
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
                        // do nothing
                    } else if (res != null) {
                        Toast.makeText(MainActivity.this, "error uploading file: " + res, Toast.LENGTH_LONG).show();
                    } else {
                        Log.i("doUpload", "uploaded file " + fileName + " to folder " + folder + ":" + dir);
                        Toast.makeText(MainActivity.this, "uploaded file: " + fileName, Toast.LENGTH_SHORT).show();
                        updateFolderListView();
                        if (!nextFilesToUpload.isEmpty()) {
                            doUpload(folder, dir, nextFilesToUpload);
                        }
                    }
                }
            }.execute();
        }
    }

    private FolderBrowser folderBrowser;
    private IndexBrowser indexBrowser;
    private boolean isBrowsingFolder = false, isHandlingUploadIntent = false, indexUpdateInProgress = false, searchModeOn=false;

    private final static String CURRENT_FOLDER_PREF = "CURRENT_FOLDER";

    private void saveCurrentFolder() {
        Log.d("saveCurrentFolder", "saveCurrentFolder");
        if (isBrowsingFolder) {
            getPreferences(MODE_PRIVATE).edit()
                    .putString(CURRENT_FOLDER_PREF, new Gson().toJson(Arrays.asList(indexBrowser.getFolder(), indexBrowser.getCurrentPath())))
                    .apply();
        } else {
            getPreferences(MODE_PRIVATE).edit().remove(CURRENT_FOLDER_PREF).apply();
        }
    }

    private void restoreBrowserFolderFromPref() {
        Log.d("restoreBrowserFolder...", "restoreBrowserFolderFromPref");
        String value = getPreferences(MODE_PRIVATE).getString(CURRENT_FOLDER_PREF, null);
        if (isBlank(value)) {
            showAllFoldersListView();
        } else {
            try {
                List<String> list = new Gson().fromJson(value, new TypeToken<List<String>>() {
                }.getType());
                checkArgument(list.size() == 2);
                showFolderListView(list.get(0), list.get(1));
            } catch (Exception ex) {
                Log.e("restoreBrowserFolder...", "error restoring browser folder from preferences", ex);
                showAllFoldersListView();
            }
        }
    }

    private void showAllFoldersListView() {
        Log.d("Main", "showAllFoldersListView BEGIN");
        if (indexBrowser != null) {
            indexBrowser.close();
            indexBrowser = null;
        }
        ListView listView = (ListView) findViewById(R.id.main_folder_and_files_list_view);
        List<Pair<FolderInfo, FolderStats>> list = Lists.newArrayList(folderBrowser.getFolderInfoAndStatsList());
        Collections.sort(list, Ordering.natural().onResultOf(new Function<Pair<FolderInfo, FolderStats>, String>() {
            @Override
            public String apply(Pair<FolderInfo, FolderStats> input) {
                return input.getLeft().getLabel();
            }
        }));
        Log.i("Main", "list folders = " + list + " (" + list.size() + " records");
        ArrayAdapter adapter = new ArrayAdapter<Pair<FolderInfo, FolderStats>>(this, R.layout.listview_folder, list) {
            @NonNull
            @Override
            public View getView(int position, View v, ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.listview_folder, null);
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
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                String folder = ((Pair<FolderInfo, FolderStats>) listView.getItemAtPosition(position)).getLeft().getFolder();
                showFolderListView(folder, null);
            }
        });
        isBrowsingFolder = false;
        updateButtonsVisibility();
        saveCurrentFolder();
        ((TextView) findViewById(R.id.main_header_folder_label)).setText(R.string.app_name);
        Log.d("Main", "showAllFoldersListView END");
    }

    private ArrayAdapter<FileInfo> createFileInfoArrayAdapter(){
        return new ArrayAdapter<FileInfo>(this, R.layout.listview_file, Lists.newArrayList()) {
            @NonNull
            @Override
            public View getView(int position, View v, ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.listview_file, null);
                }
                FileInfo fileInfo = getItem(position);
                ((TextView) v.findViewById(R.id.file_label)).setText(fileInfo.getFileName());
                if (fileInfo.isDirectory()) {
                    ((TextView) v.findViewById(R.id.file_icon)).setText(R.string.icon_folder_o);
                    ((TextView) v.findViewById(R.id.file_size)).setVisibility(View.GONE);
                } else {
                    ((TextView) v.findViewById(R.id.file_icon)).setText(R.string.icon_file_o);
                    ((TextView) v.findViewById(R.id.file_size)).setVisibility(View.VISIBLE);
                    ((TextView) v.findViewById(R.id.file_size)).setText(FileUtils.byteCountToDisplaySize(fileInfo.getSize())
                            +" - last modified "
                            + DateUtils.getRelativeDateTimeString(MainActivity.this,fileInfo.getLastModified().getTime(),DateUtils.MINUTE_IN_MILLIS,DateUtils.WEEK_IN_MILLIS,0));
                }
                return v;
            }
        };
    }

    private void showFolderListView(String folder, @Nullable String previousPath) {
        Log.d("showFolderListView", "showFolderListView BEGIN");
        if (indexBrowser != null && equal(folder, indexBrowser.getFolder())) {
            Log.d("showFolderListView", "reuse current index browser");
            indexBrowser.navigateToNearestPath(previousPath);
        } else {
            if (indexBrowser != null) {
                indexBrowser.close();
            }
            Log.d("showFolderListView", "open new index browser");
            indexBrowser = syncthingClient.getIndexHandler()
                    .newIndexBrowserBuilder()
                    .setOrdering(fileInfoOrdering)
                    .includeParentInList(true).allowParentInRoot(true)
                    .setFolder(folder)
                    .buildToNearestPath(previousPath);
        }
        ListView listView = (ListView) findViewById(R.id.main_folder_and_files_list_view);
        ArrayAdapter adapter = createFileInfoArrayAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                FileInfo fileInfo = (FileInfo) listView.getItemAtPosition(position);
                Log.d("showFolderListView", "navigate to path = '" + fileInfo.getPath() + "' from path = '" + indexBrowser.getCurrentPath() + "'");
                navigateToFolder(fileInfo);
            }
        });
        isBrowsingFolder = true;
        navigateToFolder(indexBrowser.getCurrentPathInfo());
        updateButtonsVisibility();
        Log.d("Main", "showFolderListView END");
    }

    private void updateMainProgressBar(boolean visible, String message){
        findViewById(R.id.main_progress_bar_container).setVisibility(visible?View.VISIBLE:View.GONE);
        ((TextView) findViewById(R.id.main_progress_bar_label)).setText(nullToEmpty(message));
    }

    private void navigateToFolder(FileInfo fileInfo) {
        if (indexBrowser.isRoot() && PathUtils.isParent(fileInfo.getPath())) {
            showAllFoldersListView(); //navigate back to folder list
        } else {
            if (fileInfo.isDirectory()) {
                indexBrowser.navigateTo(fileInfo);
                if (!indexBrowser.isCacheReadyAfterALittleWait()) {
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected void onPreExecute() {
                            updateMainProgressBar(true,"open directory: " + (indexBrowser.isRoot() ? folderBrowser.getFolderInfo(indexBrowser.getFolder()).getLabel() : indexBrowser.getCurrentPathFileName()));
                        }

                        @Override
                        protected Void doInBackground(Void... voids) {
                            indexBrowser.waitForCacheReady();
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            updateMainProgressBar(false,null);
                            navigateToFolder(fileInfo);
                        }
                    }.execute();
                } else {
                    List<FileInfo> list = indexBrowser.listFiles();
                    Log.i("showFolderListView", "list for path = '" + indexBrowser.getCurrentPath() + "' list = " + list.size() + " records");
                    Log.d("showFolderListView", "list for path = '" + indexBrowser.getCurrentPath() + "' list = " + list);
                    checkArgument(!list.isEmpty());//list must contain at least the 'parent' path
                    ListView listView = (ListView) findViewById(R.id.main_folder_and_files_list_view);
                    ArrayAdapter adapter = (ArrayAdapter) listView.getAdapter();
                    adapter.clear();
                    adapter.addAll(list);
                    adapter.notifyDataSetChanged();
                    listView.setSelection(0);
                    saveCurrentFolder();
                    ((TextView) findViewById(R.id.main_header_folder_label)).setText(indexBrowser.isRoot()
                            ?folderBrowser.getFolderInfo(indexBrowser.getFolder()).getLabel()
                            :(PathUtils.isParent(fileInfo.getPath())?indexBrowser.getCurrentPathInfo():fileInfo).getFileName());
                }
            } else {
                pullFile(fileInfo);
            }
        }
    }

    private void updateFolderListView() {
        Log.d("updateFolderListView", "BEGIN");
        if (indexBrowser == null) {
            showAllFoldersListView();
        } else {
            showFolderListView(indexBrowser.getFolder(), indexBrowser.getCurrentPath());
        }
        Log.d("updateFolderListView", "END");
    }

    private void initDeviceList() {
        ListView listView = (ListView) findViewById(R.id.devices_list_view);
        listView.setEmptyView(findViewById(R.id.devices_list_view_empty_element));
        ArrayAdapter adapter = new ArrayAdapter<DeviceStats>(this, R.layout.listview_device, Lists.newArrayList()) {
            @NonNull
            @Override
            public View getView(int position, View v, ViewGroup parent) {
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.listview_device, null);
                }
                DeviceStats deviceStats = getItem(position);
                ((TextView) v.findViewById(R.id.device_name)).setText(deviceStats.getName());
                switch (deviceStats.getStatus()) {
                    case OFFLINE:
                        ((TextView) v.findViewById(R.id.device_icon)).setTextColor(getResources().getColor(R.color.device_offline));
                        break;
                    case ONLINE_INACTIVE:
                        ((TextView) v.findViewById(R.id.device_icon)).setTextColor(getResources().getColor(R.color.device_online_inactive));
                        break;
                    case ONLINE_ACTIVE:
                        ((TextView) v.findViewById(R.id.device_icon)).setTextColor(getResources().getColor(R.color.device_online_active));
                        break;
                }
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
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                String deviceId = ((DeviceStats) listView.getItemAtPosition(position)).getDeviceId();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("remove device " + deviceId.substring(0, 7))
                        .setMessage("remove device" + deviceId.substring(0, 7) + " from list of known devices?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("yes", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                configuration.edit().removePeer(deviceId).persistLater();
                                updateDeviceList();
                            }
                        })
                        .setNegativeButton("no", null)
                        .show();
                Log.d("showFolderListView", "delete device = '" + deviceId + "'");
                return false;
            }
        });
        updateDeviceList();
    }

    private final List<Comparator<FileInfo>> availableFileInfoOrderings=Collections.unmodifiableList(Arrays.asList(FileInfoOrdering.ALPHA_ASC_DIR_FIRST, FileInfoOrdering.LAST_MOD_DESC));
    private Comparator<FileInfo> fileInfoOrdering=availableFileInfoOrderings.iterator().next();
    private final Map<Comparator<FileInfo>,Integer> iconResourceForOrdering=ImmutableMap.of(FileInfoOrdering.ALPHA_ASC_DIR_FIRST,R.string.icon_sort_alpha_asc,FileInfoOrdering.LAST_MOD_DESC,R.string.icon_sort_numeric_desc);

    private void toggleFileSort(){
        fileInfoOrdering=availableFileInfoOrderings.get( (availableFileInfoOrderings.indexOf(fileInfoOrdering)+1)%availableFileInfoOrderings.size() );
        ((TextView)findViewById(R.id.main_header_sort_order_button)).setText(iconResourceForOrdering.get(fileInfoOrdering));
        if(indexBrowser!=null){
            indexBrowser.setOrdering(fileInfoOrdering);
            updateFolderListView();
        }
        if(indexFinder!=null){
            indexFinder.setOrdering(fileInfoOrdering);
            updateSearchResultListView();
        }
    }

    private void updateDeviceList() {
        List<DeviceStats> list = Lists.newArrayList(syncthingClient.getDevicesHandler().getDeviceStatsList());
        Collections.sort(list, new Comparator<DeviceStats>() {
            Function<DeviceStats.DeviceStatus,Integer> fun= Functions.forMap(ImmutableMap.of(DeviceStats.DeviceStatus.OFFLINE,3, DeviceStats.DeviceStatus.ONLINE_INACTIVE,2, DeviceStats.DeviceStatus.ONLINE_ACTIVE,1));
            @Override
            public int compare(DeviceStats a, DeviceStats b) {
                return ComparisonChain.start().compare(fun.apply(a.getStatus()),fun.apply(b.getStatus())).compare(a.getName(),b.getName()).result();
            }
        });
        ListView listView = (ListView) findViewById(R.id.devices_list_view);
        ((ArrayAdapter) listView.getAdapter()).clear();
        ((ArrayAdapter<DeviceStats>) listView.getAdapter()).addAll(list);
        ((ArrayAdapter) listView.getAdapter()).notifyDataSetChanged();
        listView.setSelection(0);
    }

    private void pullFile(final FileInfo fileInfo) {
        Log.i("pullFile", "pulling file = " + fileInfo);
        new AsyncTask<Void, BlockPuller.FileDownloadObserver, Pair<File, Exception>>() {
            private ProgressDialog progressDialog;
            private Thread thread;
            private boolean cancelled = false;

            @Override
            protected void onPreExecute() {
                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setMessage("downloading file " + fileInfo.getFileName());
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setCancelable(true);
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        cancelled = true;
                        if (thread != null) {
                            thread.interrupt();
                        }
                        Toast.makeText(MainActivity.this, "download aborted by user", Toast.LENGTH_SHORT).show();
                    }
                });
                progressDialog.setIndeterminate(true);
                progressDialog.show();
            }

            @Override
            protected Pair<File, Exception> doInBackground(Void... voidd) {
                try {
                    try (BlockPuller.FileDownloadObserver fileDownloadObserver = syncthingClient.pullFile(fileInfo.getFolder(), fileInfo.getPath())) {
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
                        File outputFile = new File(outputDir, fileInfo.getFileName());
                        FileUtils.copyInputStreamToFile(fileDownloadObserver.getInputStream(), outputFile);
                        Log.i("pullFile", "downloaded file = " + fileInfo.getPath());
                        return Pair.of(outputFile, null);
                    }
                } catch (Exception ex) {
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
                    progressDialog.setIndeterminate(false);
                    progressDialog.setMax((int) (long) fileInfo.getSize());
                    progressDialog.setProgress((int) (fileDownloadObserver[0].getProgress() * fileInfo.getSize()));
                }
            }

            @Override
            protected void onPostExecute(Pair<File, Exception> res) {
                progressDialog.dismiss();
                if (cancelled) {
                    // do nothing
                } else if (res.getLeft() == null) {
                    Toast.makeText(MainActivity.this, "error downloading file: " + res.getRight(), Toast.LENGTH_LONG).show();
                } else {
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FilenameUtils.getExtension(res.getLeft().getName()));
                    Intent newIntent = new Intent(Intent.ACTION_VIEW);
                    Log.i("Main", "open file = " + res.getLeft().getName() + " (" + mimeType + ")");
                    newIntent.setDataAndType(Uri.fromFile(res.getLeft()), mimeType);
                    newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Intent chooser = Intent.createChooser(newIntent, null);
                    try {
                        startActivity(chooser);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(MainActivity.this, "no handler found for this file: " + res.getLeft().getName() + " (" + mimeType + ")", Toast.LENGTH_LONG).show();
                    }
                }
            }
        }.execute();
    }

    private final static String LAST_INDEX_UPDATE_TS_PREF = "LAST_INDEX_UPDATE_TS";

    private
    @Nullable
    Date getLastIndexUpdateFromPref() {
        long lastUpdate = getPreferences(MODE_PRIVATE).getLong(LAST_INDEX_UPDATE_TS_PREF, -1);
        if (lastUpdate < 0) {
            return null;
        } else {
            return new Date(lastUpdate);
        }
    }

    private void updateIndexFromRemote() {
        Log.d("Main", "updateIndexFromRemote BEGIN");
        if (indexUpdateInProgress) {
            Toast.makeText(MainActivity.this, "index update already in progress", Toast.LENGTH_SHORT).show();
        } else {
            indexUpdateInProgress = true;
            new AsyncTask<Void, Void, Exception>() {
                private View indexLoadingBar = (View) findViewById(R.id.main_index_progress_bar);

                @Override
                protected void onPreExecute() {
                    indexLoadingBar.setVisibility(View.VISIBLE);
                }

                @Override
                protected Exception doInBackground(Void... voidd) {
                    try {
                        syncthingClient.waitForRemoteIndexAquired();
                        return null;
                    } catch (Exception ex) {
                        Log.e("Main", "index dump exception", ex);
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
            }.execute();
        }
        Log.d("Main", "updateIndexFromRemote END (running bg)");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    closeClient();
                    configuration.close();
                    configuration=null;
                    return null;
                }
            }.execute().get();
        } catch (Exception ex) {
            Log.w("Main", "error closing client", ex);
        }
    }

    public void openQrcode() {
        IntentIntegrator integrator = new IntentIntegrator(MainActivity.this);
        integrator.initiateScan();
    }


    /**
     * Receives value of scanned QR code and sets it as device ID.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            String deviceId = scanResult.getContents();
            if (!isBlank(deviceId)) {
                Log.i("Main", "qrcode text = " + deviceId);
                importDeviceId(deviceId);
                return;
            }
        }

        if (resultCode == Activity.RESULT_OK) {
            Uri fileUri = intent.getData();
            doUpload(indexBrowser.getFolder(), indexBrowser.getCurrentPath(), Arrays.asList(fileUri));
        }
    }

    @Override
    public void onBackPressed() {
        if (indexBrowser != null) {
            ListView listView = (ListView) findViewById(R.id.main_folder_and_files_list_view);
            listView.performItemClick(listView.getAdapter().getView(0, null, null), 0, listView.getItemIdAtPosition(0)); //click item '0', ie '..' (go to parent)
        } else {
            super.onBackPressed();
        }
    }

    private void importDeviceId(String deviceId) {
        try {
            KeystoreHandler.validateDeviceId(deviceId);
            boolean modified = configuration.edit().addPeers(new DeviceInfo(deviceId, null));
            if (modified) {
                configuration.edit().persistLater();
                Toast.makeText(this, "successfully imported device: " + deviceId, Toast.LENGTH_SHORT).show();
                updateDeviceList();//TODO remove this if event triggered (and handler trigger update)
                updateIndexFromRemote();
            } else {
                Toast.makeText(this, "device already present: " + deviceId, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Log.e("Main", "error importing deviceId = " + deviceId, ex);
            Toast.makeText(this, "error importing device: " + ex, Toast.LENGTH_LONG).show();
        }
    }

    private String getDeviceName() {
        String manufacturer = nullToEmpty(Build.MANUFACTURER);
        String model = nullToEmpty(Build.MODEL);
        String deviceName;
        if (model.startsWith(manufacturer)) {
            deviceName = capitalize(model);
        } else {
            deviceName = capitalize(manufacturer) + " " + model;
        }
        if (isBlank(deviceName)) {
            deviceName = "android";
        }
        return deviceName;
    }

}
