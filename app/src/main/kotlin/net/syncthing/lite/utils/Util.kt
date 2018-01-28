package net.syncthing.lite.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import org.apache.commons.lang3.StringUtils.capitalize
import java.security.InvalidParameterException

object Util {

    private val Tag = "Util"

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        val deviceName =
            if (model.startsWith(manufacturer)) {
                capitalize(model)
            } else {
                capitalize(manufacturer) + " " + model
            }
        return deviceName ?: "android"
    }

    fun getContentFileName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                throw InvalidParameterException("Cursor is null or empty")
            }
            return cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
    }
}
