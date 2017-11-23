package net.syncthing.lite.adapters;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.syncthing.lite.R;
import net.syncthing.lite.databinding.ListviewFolderBinding;

import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.beans.FolderStats;

public class FoldersListAdapter extends ArrayAdapter<Pair<FolderInfo, FolderStats>> {

    public FoldersListAdapter(Context context, List<Pair<FolderInfo, FolderStats>> list) {
        super(context, R.layout.listview_folder, list);
    }

    @NonNull
    @Override
    public View getView(int position, View v, @NonNull ViewGroup parent) {
        ListviewFolderBinding binding;
        if (v == null) {
            binding = DataBindingUtil.inflate(LayoutInflater.from(getContext()), R.layout.listview_folder, parent, false);
        } else {
            binding = DataBindingUtil.bind(v);
        }
        FolderInfo folderInfo = getItem(position).getLeft();
        FolderStats folderStats = getItem(position).getRight();
        binding.folderName.setText(folderInfo.getLabel() + " (" + folderInfo.getFolder() + ")");
        binding.folderLastmodInfo.setText(folderStats.getLastUpdate() == null ? "last modified: unknown" : ("last modified: " + DateUtils.getRelativeDateTimeString(getContext(), folderStats.getLastUpdate().getTime(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0)));
        binding.folderContentInfo.setText(folderStats.describeSize() + ", " + folderStats.getFileCount() + " files, " + folderStats.getDirCount() + " dirs");
        return binding.getRoot();
    }

}
