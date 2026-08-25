package com.rematerial.app.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaUriHelper {
    private const val CAPTURE_DIRECTORY = "scan_capture"

    suspend fun newCameraFile(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): File = withContext(ioDispatcher) {
        val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
        File.createTempFile("capture_", ".jpg", directory)
    }

    suspend fun newCameraUri(
        context: Context,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Uri {
        val file = newCameraFile(context, ioDispatcher)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

}
