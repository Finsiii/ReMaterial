package com.rematerial.app.feature.artisan.domain

import com.rematerial.app.core.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ArtisanJob(
    val id: String,
    val customerName: String,
    val productTitle: String,
    val materialSummary: String,
    val quantity: String,
    val deadline: String,
    val address: String,
    val notes: String,
    val status: ArtisanJobStatus,
)

@Serializable
enum class ArtisanJobStatus(val label: String, val progress: Float) {
    NEW("Permintaan baru", 0.12f),
    ACCEPTED("Diterima", 0.28f),
    REVISION("Menunggu revisi", 0.42f),
    PROCESSING("Sedang dikerjakan", 0.7f),
    COMPLETED("Selesai", 1f),
}

@Serializable
data class ArtisanProfileDraft(
    val name: String = "Bima Pratama",
    val nik: String = "",
    val ktpUri: String? = null,
    val selfieUri: String? = null,
    val portfolioUris: List<String> = emptyList(),
    val submissionState: ProfileSubmissionState = ProfileSubmissionState.NOT_SUBMITTED,
)

@Serializable
enum class ProfileSubmissionState(val label: String) {
    NOT_SUBMITTED("Belum dikirim"),
    SUBMITTED("Sedang ditinjau"),
    NEEDS_CORRECTION("Perlu koreksi"),
}

interface ArtisanRepository {
    fun observeJobs(): Flow<List<ArtisanJob>>
    suspend fun updateJob(id: String, status: ArtisanJobStatus): Result<ArtisanJob>
    fun profile(): ArtisanProfileDraft
    fun saveProfile(profile: ArtisanProfileDraft)
}
