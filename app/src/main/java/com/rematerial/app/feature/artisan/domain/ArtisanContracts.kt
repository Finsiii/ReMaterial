package com.rematerial.app.feature.artisan.domain

import com.rematerial.app.core.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import com.rematerial.app.feature.production.domain.ProductionStatus

@Serializable
data class ArtisanJob(
    val id: String,
    val customerName: String,
    val productTitle: String,
    val materialSummary: String,
    val quantity: Int,
    val deadlineIso: String,
    val address: String,
    val notes: String,
    val status: ProductionStatus,
    val customerPhone: String = "",
    val customerWhatsapp: String = "",
    val preferredContact: String = "WhatsApp",
    val requiredCapabilities: List<String> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val requiredSkills: List<String> = emptyList(),
    val provisionalScore: Double = 0.0,
    val estimatedUsage: String = "",
)

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
    suspend fun updateJob(id: String, status: ProductionStatus): Result<ArtisanJob>
    fun profile(): ArtisanProfileDraft
    fun saveProfile(profile: ArtisanProfileDraft)
}
