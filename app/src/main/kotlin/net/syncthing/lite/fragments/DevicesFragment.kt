package net.syncthing.lite.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.java.core.beans.DeviceStats
import net.syncthing.lite.R
import net.syncthing.lite.adapters.DevicesAdapter
import net.syncthing.lite.databinding.FragmentDevicesBinding
import net.syncthing.lite.library.UpdateIndexTask
import net.syncthing.lite.utils.FragmentIntentIntegrator
import org.apache.commons.lang3.StringUtils.isBlank
import uk.co.markormesher.android_fab.SpeedDialMenuAdapter
import uk.co.markormesher.android_fab.SpeedDialMenuItem
import java.io.IOException
import java.security.InvalidParameterException

class DevicesFragment : SyncthingFragment() {

    private lateinit var binding: FragmentDevicesBinding
    private lateinit var adapter: DevicesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_devices, container, false)
        binding.list.emptyView = binding.empty
        binding.fab.speedDialMenuAdapter = FabMenuAdapter()
        return binding.root
    }

    override fun onLibraryLoaded() {
        initDeviceList()
        updateDeviceList()
    }

    private fun initDeviceList() {
        adapter = DevicesAdapter(context!!)
        binding.list.adapter = adapter
        binding.list.setOnItemLongClickListener { _, _, position, _ ->
            val device = (binding.list.getItemAtPosition(position) as DeviceStats)
            AlertDialog.Builder(context)
                    .setTitle(getString(R.string.remove_device_title, device.name))
                    .setMessage(getString(R.string.remove_device_message, device.deviceId.deviceId.substring(0, 7)))
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        libraryHandler?.configuration { config ->
                            config.peers = config.peers.filterNot { config.localDeviceId == device.deviceId }.toSet()
                        }
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
            false
        }
    }

    private fun updateDeviceList() {
        libraryHandler?.syncthingClient { syncthingClient ->
            adapter.clear()
            adapter.addAll(syncthingClient.devicesHandler.getDeviceStatsList())
            adapter.notifyDataSetChanged()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        // Check if this was a QR code scan.
        val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent)
        if (scanResult != null) {
            val deviceId = scanResult.contents
            if (!isBlank(deviceId)) {
                importDeviceId(deviceId)
            }
        }
    }

    private fun importDeviceId(deviceIdString: String) {
        libraryHandler?.library { configuration, syncthingClient, _ ->
            async(UI) {
                val deviceId =
                    try {
                        DeviceId(deviceIdString)
                    } catch (e: IOException) {
                        Toast.makeText(this@DevicesFragment.context, R.string.invalid_device_id, Toast.LENGTH_SHORT).show()
                        return@async
                    }

                if (!configuration.peerIds.contains(deviceId)) {
                    configuration.peers = configuration.peers + DeviceInfo(deviceId, null)
                    configuration.persistLater()
                    Toast.makeText(this@DevicesFragment.context, getString(R.string.device_import_success, deviceId), Toast.LENGTH_SHORT).show()
                    updateDeviceList()//TODO remove this if event triggered (and handler trigger update)
                    UpdateIndexTask(this@DevicesFragment.context!!, syncthingClient).updateIndex()
                } else {
                    Toast.makeText(this@DevicesFragment.context, getString(R.string.device_already_known, deviceId), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class FabMenuAdapter : SpeedDialMenuAdapter() {
        override fun getCount(): Int {
            return 2
        }

        override fun getMenuItem(context: Context, position: Int): SpeedDialMenuItem {
            when (position) {
                0 -> return SpeedDialMenuItem(context, R.drawable.ic_qr_code_white_24dp, R.string.scan_qr_code)
                1 -> return SpeedDialMenuItem(context, R.drawable.ic_edit_white_24dp, R.string.enter_device_id)
            }
            throw InvalidParameterException()
        }

        override fun onMenuItemClick(position: Int): Boolean {
            when (position) {
                0 -> FragmentIntentIntegrator(this@DevicesFragment).initiateScan()
                1 -> {
                    val editText = EditText(context)
                    val dialog = AlertDialog.Builder(context)
                            .setTitle(R.string.device_id_dialog_title)
                            .setView(editText)
                            .setPositiveButton(android.R.string.ok) { _, _ -> importDeviceId(editText.text.toString()) }
                            .setNegativeButton(android.R.string.cancel, null)
                            .create()
                    dialog.setOnShowListener {
                        val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }
                    dialog.show()
                }
            }
            return true
        }
    }
}
