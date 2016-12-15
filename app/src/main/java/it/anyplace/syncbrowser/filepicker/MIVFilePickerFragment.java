package it.anyplace.syncbrowser.filepicker;

import android.view.View;

import com.nononsenseapps.filepicker.FilePickerFragment;

import java.io.File;

/**
 * Created by aleph on 27/05/16.
 */
public class MIVFilePickerFragment extends FilePickerFragment {

    @Override
    public void onClickCheckable(View v, CheckableViewHolder vh) {
        // auto open file on click
        if (!allowMultiple) {
            // Clear is necessary, in case user clicked some checkbox directly
            mCheckedItems.clear();
            mCheckedItems.add(vh.file);
            onClickOk(null);
        } else {
            super.onClickCheckable(v, vh);
        }
    }

//    private static final String EXTENSION = ".*[.](jpg|png|jpeg)";


    @Override
    protected boolean isItemVisible(final File file) {
//        return  isDir(file) || file.getName().toLowerCase().matches(EXTENSION);
        return  true;
    }
}
