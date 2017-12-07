package net.syncthing.lite.fragments

import android.view.View
import com.nononsenseapps.filepicker.AbstractFilePickerFragment

import com.nononsenseapps.filepicker.FilePickerFragment

import java.io.File

class MIVFilePickerFragment : FilePickerFragment() {

    override fun onClickCheckable(v: View, vh: AbstractFilePickerFragment<File>.CheckableViewHolder) {
        // auto open file on click
        if (!allowMultiple) {
            // Clear is necessary, in case user clicked some checkbox directly
            mCheckedItems.clear()
            mCheckedItems.add(vh.file)
            onClickOk(null)
        } else {
            super.onClickCheckable(v, vh)
        }
    }

    //    private static final String EXTENSION = ".*[.](jpg|png|jpeg)";


    override fun isItemVisible(file: File): Boolean {
        //        return  isDir(file) || file.getName().toLowerCase().matches(EXTENSION);
        return true
    }
}
