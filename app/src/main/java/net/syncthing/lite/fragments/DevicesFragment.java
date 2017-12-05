package net.syncthing.lite.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import net.syncthing.lite.R;
import net.syncthing.lite.activities.SyncthingActivity;
import net.syncthing.lite.adapters.DevicesAdapter;
import net.syncthing.lite.databinding.FragmentDevicesBinding;
import net.syncthing.lite.utils.UpdateIndexTask;

import org.jetbrains.annotations.NotNull;

import java.security.InvalidParameterException;

import it.anyplace.sync.core.beans.DeviceInfo;
import it.anyplace.sync.core.beans.DeviceStats;
import it.anyplace.sync.core.security.KeystoreHandler;
import uk.co.markormesher.android_fab.SpeedDialMenuAdapter;
import uk.co.markormesher.android_fab.SpeedDialMenuItem;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class DevicesFragment extends Fragment {

    private static final String TAG = "DevicesFragment";

    private SyncthingActivity mActivity;
    private FragmentDevicesBinding mBinding;
    private DevicesAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.fragment_devices, container, false);
        mBinding.list.setEmptyView(mBinding.empty);
        mBinding.fab.setSpeedDialMenuAdapter(new FabMenuAdapter());
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (SyncthingActivity) getActivity();
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
        try {
            KeystoreHandler.validateDeviceId(deviceId);
        } catch (IllegalArgumentException e) {
            Toast.makeText(getContext(), R.string.invalid_device_id, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean modified = mActivity.getConfiguration().edit().addPeers(new DeviceInfo(deviceId, null));
        if (modified) {
            mActivity.getConfiguration().edit().persistLater();
            Toast.makeText(getContext(), "successfully imported device: " + deviceId, Toast.LENGTH_SHORT).show();
            updateDeviceList();//TODO remove this if event triggered (and handler trigger update)
            new UpdateIndexTask(getActivity(), mActivity.getSyncthingClient()).updateIndex();
        } else {
            Toast.makeText(getContext(), "device already present: " + deviceId, Toast.LENGTH_SHORT).show();
        }
    }

    private class FabMenuAdapter extends SpeedDialMenuAdapter {
        @Override
        public int getCount() {
            return 2;
        }

        @NotNull
        @Override
        public SpeedDialMenuItem getMenuItem(Context context, int i) {
            switch (i) {
                case 0: return new SpeedDialMenuItem(getContext(), R.drawable.ic_qr_code_white_24dp, R.string.scan_qr_code);
                case 1: return new SpeedDialMenuItem(getContext(), R.drawable.ic_edit_white_24dp, R.string.enter_device_id);
            }
            throw new InvalidParameterException();
        }

        @Override
        public boolean onMenuItemClick(int position) {
            switch (position) {
                case 0:
                    new FragmentIntentIntegrator(DevicesFragment.this).initiateScan();
                    break;
                case 1:
                    EditText editText = new EditText(getContext());
                    AlertDialog dialog = new AlertDialog.Builder(getContext())
                            .setTitle(R.string.device_id_dialog_title)
                            .setView(editText)
                            .setPositiveButton(android.R.string.ok, (dialogInterface, i) ->
                                    importDeviceId(editText.getText().toString()))
                            .setNegativeButton(android.R.string.cancel, null)
                            .create();
                    dialog.setOnShowListener(dialogInterface -> {
                        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
                    });
                    dialog.show();
            }
            return true;
        }
    }
}
