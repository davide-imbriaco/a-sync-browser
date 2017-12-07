package net.syncthing.lite.adapters

import android.content.Context
import android.databinding.DataBindingUtil
import android.graphics.PorterDuff
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.collect.Lists
import it.anyplace.sync.core.beans.DeviceStats
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ListviewDeviceBinding

class DevicesAdapter(context: Context) :
        ArrayAdapter<DeviceStats>(context, R.layout.listview_device, Lists.newArrayList()) {

    override fun getView(position: Int, v: View?, parent: ViewGroup): View {
        val binding: ListviewDeviceBinding
            = if (v == null) {
                DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.listview_device, parent, false)
            } else {
                DataBindingUtil.bind(v)
            }
        val deviceStats = getItem(position)
        binding.deviceName.text = deviceStats!!.name
        var color =
            when (deviceStats.status) {
                DeviceStats.DeviceStatus.OFFLINE -> R.color.device_offline
                DeviceStats.DeviceStatus.ONLINE_INACTIVE -> R.color.device_online_inactive
                DeviceStats.DeviceStatus.ONLINE_ACTIVE -> R.color.device_online_active
            }
        // TODO: this is not working (and will also break on API 19
        binding.deviceIcon
                .setColorFilter(ContextCompat.getColor(context, color), PorterDuff.Mode.SRC_IN)
        return binding.root
    }
}
