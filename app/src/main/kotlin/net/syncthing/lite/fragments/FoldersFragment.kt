package net.syncthing.lite.fragments

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.syncthing.lite.R
import net.syncthing.lite.activities.FolderBrowserActivity
import net.syncthing.lite.adapters.FoldersListAdapter
import net.syncthing.lite.databinding.FragmentFoldersBinding
import org.jetbrains.anko.intentFor

class FoldersFragment : SyncthingFragment() {

    private val TAG = "FoldersFragment"

    private lateinit var binding: FragmentFoldersBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_folders, container, false)
        binding.list.emptyView = binding.empty
        return binding.root
    }

    override fun onLibraryLoaded() {
        showAllFoldersListView()
    }

    private fun showAllFoldersListView() {
        libraryHandler?.folderBrowser { folderBrowser ->
            val list = folderBrowser.folderInfoAndStatsList().sortedBy { it.left.label }
            Log.i(TAG, "list folders = " + list + " (" + list.size + " records)")
            val adapter = FoldersListAdapter(context, list)
            binding.list.adapter = adapter
            binding.list.setOnItemClickListener { _, _, position, _ ->
                val folder = adapter.getItem(position)!!.left.folder
                val intent = context?.intentFor<FolderBrowserActivity>(FolderBrowserActivity.EXTRA_FOLDER_NAME to folder)
                startActivity(intent)
            }
        }
    }
}
