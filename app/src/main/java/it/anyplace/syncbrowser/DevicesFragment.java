package it.anyplace.syncbrowser;

import android.app.AlertDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import it.anyplace.sync.core.beans.DeviceInfo;
import it.anyplace.sync.core.beans.DeviceStats;
import it.anyplace.sync.core.security.KeystoreHandler;
import it.anyplace.syncbrowser.adapters.DevicesAdapter;
import it.anyplace.syncbrowser.databinding.FragmentDevicesBinding;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class DevicesFragment extends Fragment {

    private static final String TAG = "DevicesFragment";

    private SyncbrowserActivity mActivity;
    private FragmentDevicesBinding mBinding;
    private DevicesAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.fragment_devices, container, false);
        mBinding.scanQrCode.setOnClickListener(view -> new FragmentIntentIntegrator(this).initiateScan());
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (SyncbrowserActivity) getActivity();
        initDeviceList();
        updateDeviceList();
    }

    private void initDeviceList() {
        mAdapter = new DevicesAdapter(getActivity());
        mBinding.list.setAdapter(mAdapter);
        mBinding.list.setOnItemLongClickListener((adapterView, view, position, l) -> {
            String deviceId = ((DeviceStats) mBinding.list.getItemAtPosition(position)).getDeviceId();
            new AlertDialog.Builder(getActivity())
                    .setTitle("remove device " + deviceId.substring(0, 7))
                    .setMessage("remove device" + deviceId.substring(0, 7) + " from list of known devices?")
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        mActivity.getConfiguration().edit().removePeer(deviceId).persistLater();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .show();
            Log.d(TAG, "showFolderListView delete device = '" + deviceId + "'");
            return false;
        });
    }

    private void updateDeviceList() {
        mAdapter.clear();
        mAdapter.addAll(mActivity.getSyncthingClient().getDevicesHandler().getDeviceStatsList());
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check if this was a QR code scan.
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            String deviceId = scanResult.getContents();
            if (!isBlank(deviceId)) {
                importDeviceId(deviceId);
            }
        }
    }

    private void importDeviceId(String deviceId) {
        KeystoreHandler.validateDeviceId(deviceId);
        boolean modified = mActivity.getConfiguration().edit().addPeers(new DeviceInfo(deviceId, null));
        if (modified) {
            mActivity.getConfiguration().edit().persistLater();
            Toast.makeText(getContext(), "successfully imported device: " + deviceId, Toast.LENGTH_SHORT).show();
            updateDeviceList();//TODO remove this if event triggered (and handler trigger update)
            new UpdateIndexTask(getActivity(), mActivity.getSyncthingClient())
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            Toast.makeText(getContext(), "device already present: " + deviceId, Toast.LENGTH_SHORT).show();
        }
    }
}
