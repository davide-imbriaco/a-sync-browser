package net.syncthing.lite.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import net.syncthing.lite.R
import java.io.File
import java.util.*

/**
 * Activity that allows selecting a directory in the local file system.
 */
class FilePickerActivity : SyncthingActivity(), AdapterView.OnItemClickListener {

    private lateinit var mListView: ListView
    private lateinit var mFilesAdapter: FileAdapter
    private lateinit var mLocation: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_folder_picker)
        mListView = findViewById(android.R.id.list)
        mListView.onItemClickListener = this
        mListView.emptyView = findViewById(android.R.id.empty)
        mFilesAdapter = FileAdapter(this)
        mListView.adapter = mFilesAdapter

        displayFolder(Environment.getExternalStorageDirectory())
    }

    /**
     * Refreshes the ListView to show the contents of the location in ``mLocation.peek()}.
     */
    private fun displayFolder(location: File) {
        mLocation = location
        mFilesAdapter.clear()
        // In case we don't have read access to the location, just display nothing.
        val contents = location.listFiles() ?: arrayOf()

        Arrays.sort(contents) { f1, f2 ->
            if (f1.isDirectory && f2.isFile)
                return@sort -1
            if (f1.isFile && f2.isDirectory)
                return@sort 1
            f1.name.compareTo(f2.name)
        }

        for (f in contents) {
            mFilesAdapter.add(f)
        }
        mListView.adapter = mFilesAdapter
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        val f = mFilesAdapter.getItem(i)
        if (f!!.isDirectory) {
            displayFolder(f)
        } else {
            val intent = Intent()
            intent.data = Uri.fromFile(f)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private inner class FileAdapter(context: Context) : ArrayAdapter<File>(context, R.layout.item_folder_picker) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val title = view.findViewById<TextView>(android.R.id.text1)
            val f = getItem(position)!!
            title.text = f.name
            val icon =
                    if (f.isDirectory) R.drawable.ic_folder_black_24dp
                    else               R.drawable.ic_image_black_24dp
            title.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0)

            return view
        }
    }

    override fun onBackPressed() {
        if (mLocation != Environment.getExternalStorageDirectory()) {
            displayFolder(mLocation.parentFile)
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

}
