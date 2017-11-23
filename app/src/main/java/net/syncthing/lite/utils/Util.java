package net.syncthing.lite.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Strings.nullToEmpty;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.isBlank;

class Util {

    static String getContentFileName(Context context, Uri contentUri) {
        String fileName = new File(contentUri.getLastPathSegment()).getName();
        if (equal(contentUri.getScheme(), "content")) {
            try (Cursor cursor = context.getContentResolver().query(contentUri, new String[]{MediaStore.Images.Media.DATA}, null, null, null)) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(column_index);
                Log.d("Main", "recovered 'content' uri real path = " + path);
                fileName = new File(Uri.parse(path).getLastPathSegment()).getName();
            }
        }
        return fileName;
    }

    static String getDeviceName() {
        String manufacturer = nullToEmpty(Build.MANUFACTURER);
        String model = nullToEmpty(Build.MODEL);
        String deviceName;
        if (model.startsWith(manufacturer)) {
            deviceName = capitalize(model);
        } else {
            deviceName = capitalize(manufacturer) + " " + model;
        }
        if (isBlank(deviceName)) {
            deviceName = "android";
        }
        return deviceName;
    }
}
