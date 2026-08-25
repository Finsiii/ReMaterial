package com.rematerial.app.feature.identity.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class AndroidSessionStore @Inject constructor(@ApplicationContext context: Context) : SessionStore {
    private val preferences = context.getSharedPreferences("rematerial_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(preferences.getString(KEY, null)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() })
    override val session: StateFlow<Session?> = mutable
    override fun current(): Session? = mutable.value

    override suspend fun save(session: Session) = withContext(Dispatchers.IO) {
        // accessToken is process-only; Session's serialized form intentionally excludes it.
        preferences.edit().putString(KEY, json.encodeToString(session.copy(accessToken = null))).apply()
        mutable.value = session.copy(accessToken = null)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().remove(KEY).apply()
        mutable.value = null
    }

    private companion object { const val KEY = "active_session_v1" }
}

@Singleton
class AndroidCoarseLocationResolver @Inject constructor(@ApplicationContext private val context: Context) : LocationResolver {
    override suspend fun resolveCoarseLocation(): Result<LocationProfile> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return@withContext Result.Failure(DomainFailure.PermissionDenied)
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = runCatching {
            manager.getProviders(true).mapNotNull(manager::getLastKnownLocation).maxByOrNull { it.time }
        }.getOrNull() ?: return@withContext Result.Failure(DomainFailure.Unavailable)
        val age = System.currentTimeMillis() - location.time
        if (location.time <= 0L || age !in 0..MAX_LOCATION_AGE_MILLIS) {
            return@withContext Result.Failure(DomainFailure.Unavailable)
        }
        if (location.hasAccuracy() && location.accuracy > MAX_LOCATION_ACCURACY_METERS) {
            return@withContext Result.Failure(DomainFailure.Unavailable)
        }
        Result.Success(
            LocationProfile(
                area = "",
                latitude = location.latitude,
                longitude = location.longitude,
                source = "device_last_known_coarse",
                capturedAtEpochMillis = location.time,
                accuracyMeters = location.accuracy.takeIf { location.hasAccuracy() },
            ),
        )
    }

    private companion object {
        const val MAX_LOCATION_AGE_MILLIS = 15 * 60 * 1000L
        const val MAX_LOCATION_ACCURACY_METERS = 5_000f
    }
}

@Singleton
class AndroidVerificationDocumentStore @Inject constructor(@ApplicationContext private val context: Context) : VerificationDocumentStore {
    override suspend fun import(sourceUri: String, kind: VerificationDocumentKind): Result<OwnedDocument> = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime !in SUPPORTED_MIME) return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        val directory = File(context.filesDir, "verification_documents").apply { mkdirs() }
        val destination = File(directory, "${kind.name.lowercase()}-${UUID.randomUUID()}.img")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) error("document too large")
                        output.write(buffer, 0, read)
                    }
                    check(total > 0)
                }
            } ?: error("source unavailable")
        }.isSuccess
        if (!copied) {
            destination.delete()
            return@withContext Result.Failure(DomainFailure.UnsupportedImage)
        }
        Result.Success(OwnedDocument(destination.absolutePath))
    }

    override suspend fun delete(privatePath: String) = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "verification_documents").canonicalFile
        val candidate = File(privatePath).canonicalFile
        if (candidate.parentFile == root) candidate.delete()
        Unit
    }

    private companion object {
        const val MAX_BYTES = 10L * 1024L * 1024L
        val SUPPORTED_MIME = setOf("image/jpeg", "image/png", "image/heic", "image/heif")
    }
}
