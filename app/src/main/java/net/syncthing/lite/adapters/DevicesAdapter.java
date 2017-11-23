package net.syncthing.lite.adapters;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.common.collect.Lists;

import net.syncthing.lite.R;
import net.syncthing.lite.databinding.ListviewDeviceBinding;

import it.anyplace.sync.core.beans.DeviceStats;

public class DevicesAdapter extends ArrayAdapter<DeviceStats> {

    public DevicesAdapter(Context context) {
        super(context, R.layout.listview_device, Lists.newArrayList());
    }

    @NonNull
    @Override
    public View getView(int position, View v, @NonNull ViewGroup parent) {
        ListviewDeviceBinding binding;
        if (v == null) {
            binding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.listview_device, parent, false);
        } else {
            binding = DataBindingUtil.bind(v);
        }
        DeviceStats deviceStats = getItem(position);
        binding.deviceName.setText(deviceStats.getName());
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
        binding.deviceIcon
                .setColorFilter(ContextCompat.getColor(getContext(), color), PorterDuff.Mode.SRC_IN);
        return binding.getRoot();
    }
}
