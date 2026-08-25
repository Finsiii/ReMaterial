package com.rematerial.app.feature.production.domain

import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ProductDraft(
    val optionId: ProductOptionId? = null,
    val title: String = "",
    val materialSummary: String = "",
    val minimumQuantity: String = "",
    val analysisId: String? = null,
    val safetyAllowed: Boolean = true,
    val requiredCapabilities: List<String> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val requiredSkills: List<String> = emptyList(),
    val provisionalScore: Double = 0.0,
    val estimatedUsage: String = "",
)

fun ProductDraft.isReadyForProduction(): Boolean =
    optionId != null &&
        title.isNotBlank() &&
        !analysisId.isNullOrBlank() &&
        safetyAllowed

@Serializable
data class ArtisanProfile(
    val id: String,
    val name: String,
    val area: String,
    val distance: String,
    val capabilities: List<String>,
    val eta: String,
    val priceRange: String,
    val availability: String,
    val about: String,
    val matchReason: String,
    val whatsapp: String = "081234567891",
    val latitude: Double = -6.9147,
    val longitude: Double = 107.6098,
    val portfolioImageKeys: List<String> = emptyList(),
    val rating: String = "4,9",
    val completedJobs: String = "86 karya selesai",
    val responseTime: String = "Balas dalam 1 jam",
    val workingHours: String = "Senin–Sabtu · 09.00–17.00",
    val verifiedState: String = "Identitas dan portofolio telah diverifikasi",
    val capabilityKeys: Set<String> = emptySet(),
    val toolKeys: Set<String> = emptySet(),
    val skillKeys: Set<String> = emptySet(),
    val verified: Boolean = true,
)

@Serializable
data class ProductionRequest(
    val id: String,
    val ownerAccountId: String = "demo-user",
    val artisan: ArtisanProfile,
    val draft: ProductDraft,
    val quantity: Int,
    val notes: String,
    val address: String,
    val targetDateIso: String,
    val phone: String = "",
    val whatsapp: String = "",
    val preferredContact: String = "WhatsApp",
    val status: ProductionStatus = ProductionStatus.SUBMITTED,
    val customerName: String = "Pengguna",
)

@Serializable
enum class ProductionStatus(val label: String, val progress: Float) {
    SUBMITTED("Permintaan dikirim", 0.12f),
    NEEDS_CLARIFICATION("Perlu penjelasan", 0.22f),
    ACCEPTED("Diterima pengrajin", 0.36f),
    IN_PRODUCTION("Sedang dikerjakan", 0.68f),
    READY_FOR_REVIEW("Siap diperiksa", 0.9f),
    REVISION_REQUESTED("Perlu revisi", 0.68f),
    COMPLETED("Selesai", 1f),
    CANCELLED("Dibatalkan", 0f),
}

@Serializable
data class ProductionRequestInput(
    val artisanId: String,
    val draft: ProductDraft,
    val quantity: Int,
    val notes: String,
    val address: String,
    val targetDateIso: String,
    val phone: String = "",
    val whatsapp: String = "",
    val preferredContact: String = "WhatsApp",
)

interface ProductionRepository {
    fun currentDraft(): ProductDraft
    fun saveDraft(draft: ProductDraft)
    suspend fun searchArtisans(area: String): Result<List<ArtisanProfile>>
    suspend fun submit(input: ProductionRequestInput): Result<ProductionRequest>
    fun observeRequests(): Flow<List<ProductionRequest>>
    suspend fun getRequest(id: String): Result<ProductionRequest>
    suspend fun completeRequest(id: String): Result<ProductionRequest>
    suspend fun requestRevision(id: String): Result<ProductionRequest>
    suspend fun cancelRequest(id: String): Result<ProductionRequest>
}
