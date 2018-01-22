package net.syncthing.lite.adapters

import android.content.Context
import android.databinding.DataBindingUtil
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import net.syncthing.java.core.beans.DeviceStats
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ListviewDeviceBinding

class DevicesAdapter(context: Context) :
        ArrayAdapter<DeviceStats>(context, R.layout.listview_device, mutableListOf()) {

    override fun getView(position: Int, v: View?, parent: ViewGroup): View {
        val binding: ListviewDeviceBinding
            = if (v == null) {
                DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.listview_device, parent, false)
            } else {
                DataBindingUtil.bind(v)
            }
        val deviceStats = getItem(position)
        binding.deviceName.text = deviceStats!!.name
        val icon =
            when (deviceStats.status) {
                DeviceStats.DeviceStatus.OFFLINE -> R.drawable.ic_laptop_red_24dp
                DeviceStats.DeviceStatus.ONLINE_INACTIVE,
                DeviceStats.DeviceStatus.ONLINE_ACTIVE -> R.drawable.ic_laptop_green_24dp
            }
        binding.deviceIcon.setImageResource(icon)
        return binding.root
    }
}
