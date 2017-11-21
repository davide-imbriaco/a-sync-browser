package it.anyplace.syncbrowser.activities;

import com.nononsenseapps.filepicker.AbstractFilePickerActivity;
import com.nononsenseapps.filepicker.AbstractFilePickerFragment;

import java.io.File;

import it.anyplace.syncbrowser.fragments.MIVFilePickerFragment;

public class MIVFilePickerActivity extends AbstractFilePickerActivity<File> {

        @Override
        protected AbstractFilePickerFragment<File> getFragment(
                final String startPath, final int mode, final boolean allowMultiple,
                final boolean allowCreateDir) {
            // Only the fragment in this line needs to be changed
            AbstractFilePickerFragment<File> fragment = new MIVFilePickerFragment();
            fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir);
            return fragment;
        }
}
