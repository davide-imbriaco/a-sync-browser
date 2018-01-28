package net.syncthing.lite.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import net.syncthing.lite.library.LibraryHandler

abstract class SyncthingFragment : Fragment() {

    var libraryHandler: LibraryHandler? = null
        private set

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        LibraryHandler(context!!, this::onLibraryLoadedInternal, this::onIndexUpdateProgress,
                this::onIndexUpdateComplete)
    }

    private fun onLibraryLoadedInternal(libraryHandler: LibraryHandler) {
        this.libraryHandler = libraryHandler
        onLibraryLoaded()
    }

    override fun onDestroy() {
        super.onDestroy()
        libraryHandler?.close()
    }

    open fun onLibraryLoaded() {}

    open fun onIndexUpdateProgress(folder: String, percentage: Int) {}

    open fun onIndexUpdateComplete(folder: String) {}
}