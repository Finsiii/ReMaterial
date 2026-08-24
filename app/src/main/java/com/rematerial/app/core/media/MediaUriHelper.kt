package com.rematerial.app.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object MediaUriHelper {
    fun newCameraFile(context: Context): File =
        File.createTempFile("rematerial_scan_", ".jpg", context.cacheDir)

    fun newCameraUri(context: Context): Uri {
        val file = newCameraFile(context)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun contentType(file: File): String = "image/jpeg"
}
