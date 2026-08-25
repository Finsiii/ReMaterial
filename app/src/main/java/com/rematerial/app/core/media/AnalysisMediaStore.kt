package com.rematerial.app.core.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.domain.PhotoReference
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AnalysisMediaStore {
    suspend fun importUri(uri: Uri): Result<PhotoReference>
    suspend fun adoptCapture(file: File): Result<PhotoReference>
    suspend fun delete(photo: PhotoReference)
    suspend fun cleanupAbandoned()
    suspend fun cleanupOrphans(referencedPaths: Set<String>)
    suspend fun isValidOwned(photo: PhotoReference): Boolean
}

class FileAnalysisMediaStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AnalysisMediaStore {
    private val ownedDirectory = File(context.filesDir, "analysis_media")
    private val captureDirectory = File(context.cacheDir, "scan_capture")

    override suspend fun importUri(uri: Uri): Result<PhotoReference> = withContext(ioDispatcher) {
        try {
            val contentType = context.contentResolver.getType(uri)?.lowercase()?.substringBefore(';')
                ?: return@withContext Result.Failure(DomainFailure.UnsupportedImage)
            if (contentType !in MediaValidationPolicy.supportedTypes) return@withContext Result.Failure(DomainFailure.UnsupportedImage)
            val id = UUID.randomUUID().toString()
            copyOwned(context.contentResolver, uri, ownedFile(id, extensionFor(contentType)), id, contentType)
        } catch (_: SecurityException) {
            Result.Failure(DomainFailure.PermissionDenied)
        } catch (_: RuntimeException) {
            Result.Failure(DomainFailure.Unavailable)
        }
    }

    override suspend fun adoptCapture(file: File): Result<PhotoReference> = withContext(ioDispatcher) {
        val canonicalCapture = runCatching { file.canonicalFile }.getOrNull()
            ?: return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        val allowedRoot = runCatching { captureDirectory.canonicalFile }.getOrNull()
            ?: return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        if (!canonicalCapture.path.startsWith(allowedRoot.path + File.separator)) {
            return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        }
        val header = file.headerBytes()
        if (MediaValidationPolicy.validate("image/jpeg", file.length()) != null || header == null || MediaValidationPolicy.validateHeader("image/jpeg", header) != null || !file.isDecodableImage()) {
            file.delete()
            return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        }
        val id = UUID.randomUUID().toString()
        val destination = ownedFile(id, "jpg")
        try {
            ownedDirectory.mkdirs()
            if (!file.renameTo(destination)) file.copyTo(destination, overwrite = false).also { file.delete() }
            Result.Success(PhotoReference(id, destination.absolutePath, "image/jpeg", destination.length()))
        } catch (_: IOException) {
            destination.delete()
            file.delete()
            Result.Failure(DomainFailure.Unavailable)
        }
    }

    override suspend fun delete(photo: PhotoReference) = withContext(ioDispatcher) { ownedPath(photo.privatePath)?.delete(); Unit }

    override suspend fun cleanupAbandoned() = withContext(ioDispatcher) {
        val cutoff = System.currentTimeMillis() - ABANDONED_AGE_MS
        captureDirectory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
        Unit
    }

    override suspend fun cleanupOrphans(referencedPaths: Set<String>) = withContext(ioDispatcher) {
        val canonicalReferences = referencedPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        ownedDirectory.listFiles()?.filter { file ->
            runCatching { file.canonicalPath }.getOrNull() !in canonicalReferences
        }?.forEach(File::delete)
        Unit
    }

    override suspend fun isValidOwned(photo: PhotoReference): Boolean = withContext(ioDispatcher) {
        val file = ownedPath(photo.privatePath) ?: return@withContext false
        val header = file.headerBytes() ?: return@withContext false
        file.isFile && file.length() == photo.sizeBytes &&
            MediaValidationPolicy.validate(photo.contentType, file.length()) == null &&
            MediaValidationPolicy.validateHeader(photo.contentType, header) == null && file.isDecodableImage()
    }

    private fun copyOwned(resolver: ContentResolver, uri: Uri, destination: File, id: String, contentType: String): Result<PhotoReference> = try {
        ownedDirectory.mkdirs()
        val input = resolver.openInputStream(uri) ?: return Result.Failure(DomainFailure.UnsupportedImage)
        var total = 0L
        input.use { source ->
            FileOutputStream(destination).use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MediaValidationPolicy.MAX_BYTES) {
                        destination.delete()
                        return Result.Failure(DomainFailure.UnsupportedImage)
                    }
                    sink.write(buffer, 0, read)
                }
            }
        }
        val header = destination.headerBytes()
        if (MediaValidationPolicy.validate(contentType, total) != null || header == null || MediaValidationPolicy.validateHeader(contentType, header) != null || !destination.isDecodableImage()) {
            destination.delete()
            return Result.Failure(DomainFailure.UnsupportedImage)
        }
        Result.Success(PhotoReference(id, destination.absolutePath, contentType, total))
    } catch (_: IOException) {
        destination.delete()
        Result.Failure(DomainFailure.Unavailable)
    } catch (_: SecurityException) {
        destination.delete()
        Result.Failure(DomainFailure.PermissionDenied)
    }

    private fun ownedFile(id: String, extension: String): File = File(ownedDirectory, "$id.$extension")

    private fun ownedPath(path: String): File? {
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val root = runCatching { ownedDirectory.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun extensionFor(type: String): String = when (type) {
        "image/png" -> "png"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    private companion object { const val ABANDONED_AGE_MS = 24L * 60L * 60L * 1000L }
}

interface MediaPayloadReader { suspend fun read(photo: PhotoReference): Result<ByteArray> }

class OwnedFileMediaPayloadReader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaPayloadReader {
    override suspend fun read(photo: PhotoReference): Result<ByteArray> = withContext(ioDispatcher) {
        val root = runCatching { File(context.filesDir, "analysis_media").canonicalFile }.getOrNull()
            ?: return@withContext Result.Failure(DomainFailure.Unavailable)
        val file = runCatching { File(photo.privatePath).canonicalFile }.getOrNull()
            ?: return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        if (!file.path.startsWith(root.path + File.separator)) return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        val header = file.headerBytes()
        if (
            MediaValidationPolicy.validate(photo.contentType, file.length()) != null || file.length() != photo.sizeBytes ||
            header == null || MediaValidationPolicy.validateHeader(photo.contentType, header) != null || !file.isDecodableImage()
        ) return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        try { Result.Success(file.readBytes()) } catch (_: IOException) { Result.Failure(DomainFailure.Unavailable) }
    }
}

private fun File.headerBytes(): ByteArray? = runCatching {
    inputStream().use { input ->
        val buffer = ByteArray(16)
        val count = input.read(buffer)
        if (count <= 0) byteArrayOf() else buffer.copyOf(count)
    }
}.getOrNull()

private fun File.isDecodableImage(): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, options)
    return options.outWidth > 0 && options.outHeight > 0
}
