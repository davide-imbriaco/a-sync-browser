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
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import net.syncthing.lite.R
import net.syncthing.lite.adapters.DevicesAdapter
import net.syncthing.lite.databinding.FragmentDevicesBinding
import net.syncthing.lite.databinding.ViewEnterDeviceIdBinding
import net.syncthing.lite.utils.FragmentIntentIntegrator
import net.syncthing.lite.utils.Util
import java.io.IOException

class DevicesFragment : SyncthingFragment() {

    private lateinit var binding: FragmentDevicesBinding
    private lateinit var adapter: DevicesAdapter
    private var addDeviceDialog: AlertDialog? = null
    private var addDeviceDialogBinding: ViewEnterDeviceIdBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_devices, container, false)
        binding.list.emptyView = binding.empty
        binding.addDevice.setOnClickListener { showDialog() }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        libraryHandler?.syncthingClient { it.addOnConnectionChangedListener { updateDeviceList() } }
    }

    override fun onPause() {
        super.onPause()
        libraryHandler?.syncthingClient { it.removeOnConnectionChangedListener{ updateDeviceList() } }
    }

    override fun onLibraryLoaded() {
        initDeviceList()
        updateDeviceList()
    }

    private fun initDeviceList() {
        adapter = DevicesAdapter(context!!)
        binding.list.adapter = adapter
        binding.list.setOnItemLongClickListener { _, _, position, _ ->
            val device = adapter.getItem(position)
            AlertDialog.Builder(context)
                    .setTitle(getString(R.string.remove_device_title, device.name))
                    .setMessage(getString(R.string.remove_device_message, device.deviceId.deviceId.substring(0, 7)))
                    .setPositiveButton(android.R.string.yes) { _, _ ->
                        libraryHandler?.configuration { config ->
                            config.peers = config.peers.filterNot { it.deviceId == device.deviceId }.toSet()
                            config.persistLater()
                            updateDeviceList()
                        }
                    }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
            false
        }
    }

    private fun updateDeviceList() {
        async(UI) {
            libraryHandler?.syncthingClient { syncthingClient ->
                adapter.clear()
                adapter.addAll(syncthingClient.getPeerStatus())
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent)
        if (scanResult?.contents != null && scanResult.contents.isNotBlank()) {
            addDeviceDialogBinding?.deviceId?.setText(scanResult.contents)
        }
    }

    private fun showDialog() {
        addDeviceDialogBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.view_enter_device_id, null, false)
        addDeviceDialogBinding?.let { binding ->
            binding.scanQrCode.setOnClickListener {
                FragmentIntentIntegrator(this@DevicesFragment).initiateScan()
            }
            binding.deviceId.post {
                val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.deviceId, InputMethodManager.SHOW_IMPLICIT)
            }

            addDeviceDialog = AlertDialog.Builder(context)
                    .setTitle(R.string.device_id_dialog_title)
                    .setView(binding.root)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()

            // Use different listener to keep dialog open after button click.
            // https://stackoverflow.com/a/15619098
            addDeviceDialog?.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.setOnClickListener {
                        try {
                            val deviceId = binding.deviceId.text.toString()
                            Util.importDeviceId(libraryHandler, context, deviceId, { updateDeviceList() })
                            addDeviceDialog?.dismiss()
                        } catch (e: IOException) {
                            binding.deviceId.error = getString(R.string.invalid_device_id)
                        }
                    }
        }
    }
}
