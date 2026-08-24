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
)

@Serializable
data class ProductionRequest(
    val id: String,
    val artisan: ArtisanProfile,
    val draft: ProductDraft,
    val quantity: String,
    val notes: String,
    val address: String,
    val targetDate: String,
    val phone: String = "",
    val whatsapp: String = "",
    val preferredContact: String = "WhatsApp",
    val status: ProductionStatus = ProductionStatus.SUBMITTED,
)

@Serializable
enum class ProductionStatus(val label: String, val progress: Float) {
    SUBMITTED("Permintaan diterima", 0.2f),
    REVIEW("Sedang ditinjau pengrajin", 0.4f),
    PROCESSING("Sedang dikerjakan", 0.7f),
    READY("Siap dikirim", 1f),
}

@Serializable
data class ProductionRequestInput(
    val artisanId: String,
    val draft: ProductDraft,
    val quantity: String,
    val notes: String,
    val address: String,
    val targetDate: String,
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
}
