package net.syncthing.lite.fragments

import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.collect.Lists
import com.google.common.collect.Ordering
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.lite.R
import net.syncthing.lite.activities.FolderBrowserActivity
import net.syncthing.lite.adapters.FoldersListAdapter
import net.syncthing.lite.databinding.FragmentFoldersBinding
import org.apache.commons.lang3.tuple.Pair
import java.util.*

class FoldersFragment : SyncthingFragment() {

    companion object {
        private val TAG = "FoldersFragment"
    }

    private lateinit var binding: FragmentFoldersBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(layoutInflater, R.layout.fragment_folders, container, false)
        binding.list.emptyView = binding.empty
        return binding.root
    }

    override fun onLibraryLoadedAndActivityCreated() {
        showAllFoldersListView()
    }

    private fun showAllFoldersListView() {
        val list = Lists.newArrayList(getSyncthingActivity().folderBrowser()!!.folderInfoAndStatsList)
        Collections.sort(list, Ordering.natural<Comparable<String>>()
                .onResultOf<Pair<FolderInfo, FolderStats>> { input -> input?.left?.label })
        Log.i(TAG, "list folders = " + list + " (" + list.size + " records")
        val adapter = FoldersListAdapter(context, list)
        binding.list.adapter = adapter
        binding.list.setOnItemClickListener { _, _, position, _ ->
            val folder = adapter.getItem(position)!!.left.folder
            val intent = Intent(context, FolderBrowserActivity::class.java)
            intent.putExtra(FolderBrowserActivity.EXTRA_FOLDER_NAME, folder)
            startActivity(intent)
        }
    }
}
