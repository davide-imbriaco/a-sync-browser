package net.syncthing.lite.fragments;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import net.syncthing.lite.R;
import net.syncthing.lite.activities.FolderBrowserActivity;
import net.syncthing.lite.activities.SyncthingActivity;
import net.syncthing.lite.adapters.FoldersListAdapter;
import net.syncthing.lite.databinding.FragmentFoldersBinding;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;

import it.anyplace.sync.core.beans.FolderInfo;
import it.anyplace.sync.core.beans.FolderStats;

public class FoldersFragment extends Fragment {

    private static final String TAG = "FoldersFragment";

    private SyncthingActivity mActivity;
    private FragmentFoldersBinding mBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.fragment_folders, container, false);
        mBinding.list.setEmptyView(mBinding.empty);
        return mBinding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = (SyncthingActivity) getActivity();
        showAllFoldersListView();
    }

    private void showAllFoldersListView() {
        List<Pair<FolderInfo, FolderStats>> list = Lists.newArrayList(mActivity.getFolderBrowser().getFolderInfoAndStatsList());
        Collections.sort(list, Ordering.natural().onResultOf(input -> input.getLeft().getLabel()));
        Log.i(TAG, "list folders = " + list + " (" + list.size() + " records");
        ArrayAdapter<Pair<FolderInfo, FolderStats>> adapter = new FoldersListAdapter(getContext(), list);
        mBinding.list.setAdapter(adapter);
        mBinding.list.setOnItemClickListener((adapterView, view, position, l) -> {
            String folder = adapter.getItem(position).getLeft().getFolder();
            Intent intent = new Intent(getContext(), FolderBrowserActivity.class);
            intent.putExtra(FolderBrowserActivity.EXTRA_FOLDER_NAME, folder);
            startActivity(intent);
        });
    }
}
