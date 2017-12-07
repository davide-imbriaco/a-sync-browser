package net.syncthing.lite.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.common.base.Objects.equal
import com.google.common.base.Strings.nullToEmpty
import org.apache.commons.lang3.StringUtils.capitalize
import java.io.File

object Util {

    fun getDeviceName(): String {
        val manufacturer = nullToEmpty(Build.MANUFACTURER)
        val model = nullToEmpty(Build.MODEL)
        val deviceName =
            if (model.startsWith(manufacturer)) {
                capitalize(model)
            } else {
                capitalize(manufacturer) + " " + model
            }
        return deviceName ?: "android"
        }

    fun getContentFileName(context: Context, contentUri: Uri): String {
        var fileName = File(contentUri.lastPathSegment).name
        if (equal(contentUri.scheme, "content")) {
            context.contentResolver.query(contentUri, arrayOf(MediaStore.Images.Media.DATA), null, null, null)!!.use { cursor ->
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.moveToFirst()
                val path = cursor.getString(columnIndex)
                Log.d("Main", "recovered 'content' uri real path = " + path)
                fileName = File(Uri.parse(path).lastPathSegment).name
            }
        }
        return fileName
    }
}
