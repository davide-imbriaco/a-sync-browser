package net.syncthing.lite.activities

import com.nononsenseapps.filepicker.AbstractFilePickerActivity
import com.nononsenseapps.filepicker.AbstractFilePickerFragment

import net.syncthing.lite.fragments.MIVFilePickerFragment

import java.io.File

class MIVFilePickerActivity : AbstractFilePickerActivity<File>() {

    override fun getFragment(startPath: String, mode: Int, allowMultiple: Boolean,
            allowCreateDir: Boolean): AbstractFilePickerFragment<File> {
        // Only the fragment in this line needs to be changed
        val fragment = MIVFilePickerFragment()
        fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir)
        return fragment
    }
}
