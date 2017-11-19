package it.anyplace.syncbrowser.adapters;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.common.collect.Lists;

import org.apache.commons.io.FileUtils;

import it.anyplace.sync.core.beans.FileInfo;
import it.anyplace.syncbrowser.R;
import it.anyplace.syncbrowser.databinding.ListviewFileBinding;

public class FolderContentsAdapter extends ArrayAdapter<FileInfo> {

    public FolderContentsAdapter(Context context) {
        super(context, R.layout.listview_file, Lists.newArrayList());
    }

    @NonNull
    @Override
    public View getView(int position, View v, @NonNull ViewGroup parent) {
        ListviewFileBinding binding;
        if (v == null) {
            binding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.listview_file, parent, false);
        } else {
            binding = DataBindingUtil.bind(v);
        }
        FileInfo fileInfo = getItem(position);
        binding.fileLabel.setText(fileInfo.getFileName());
        if (fileInfo.isDirectory()) {
            binding.fileIcon.setImageResource(R.drawable.ic_folder_black_24dp);
            binding.fileSize.setVisibility(View.GONE);
        } else {
            binding.fileIcon.setImageResource(R.drawable.ic_image_black_24dp);
            binding.fileSize.setVisibility(View.VISIBLE);
            binding.fileSize.setText(FileUtils.byteCountToDisplaySize(fileInfo.getSize())
                    + " - last modified "
                    + DateUtils.getRelativeDateTimeString(getContext(), fileInfo.getLastModified().getTime(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0));
        }
        return binding.getRoot();
    }
}
