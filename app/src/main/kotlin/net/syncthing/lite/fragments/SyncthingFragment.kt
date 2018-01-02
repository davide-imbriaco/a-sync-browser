package net.syncthing.lite.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import net.syncthing.lite.activities.SyncthingActivity

/**
 * Handle connection to [[SyncthingActivity]], and make sure device rotation are handled correctly.
 */
abstract class SyncthingFragment : Fragment() {

    protected fun getSyncthingActivity() = activity as SyncthingActivity

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        checkConditions()
    }

    fun onLibraryLoaded() {
        checkConditions()
    }

    private fun checkConditions() {
        if (activity != null && getSyncthingActivity().folderBrowser() != null ) {
            onLibraryLoadedAndActivityCreated()
        }
    }

    open fun onLibraryLoadedAndActivityCreated() {
    }
}