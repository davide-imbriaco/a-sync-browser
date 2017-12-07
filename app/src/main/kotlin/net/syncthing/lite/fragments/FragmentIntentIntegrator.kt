package net.syncthing.lite.fragments

import android.content.Intent
import android.support.v4.app.Fragment

import com.google.zxing.integration.android.IntentIntegrator

// https://stackoverflow.com/a/22320076/1837158
class FragmentIntentIntegrator(private val fragment: Fragment) : IntentIntegrator(fragment.activity) {

    override fun startActivityForResult(intent: Intent, code: Int) {
        fragment.startActivityForResult(intent, code)
    }
}
