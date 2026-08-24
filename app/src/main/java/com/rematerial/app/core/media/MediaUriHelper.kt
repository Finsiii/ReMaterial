package com.rematerial.app.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object MediaUriHelper {
    fun newCameraUri(context: Context): Uri {
        val file = File.createTempFile("rematerial_scan_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
