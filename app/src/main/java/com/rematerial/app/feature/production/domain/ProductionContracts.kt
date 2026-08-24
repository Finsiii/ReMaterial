package com.rematerial.app.feature.production.domain

import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ProductDraft(
    val optionId: ProductOptionId? = null,
    val title: String = "Lampu meja dari kabel tembaga",
    val materialSummary: String = "Kabel tembaga · 2,45 kg · kondisi layak olah",
    val minimumQuantity: String = "1 unit",
)

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
)

interface ProductionRepository {
    fun currentDraft(): ProductDraft
    fun saveDraft(draft: ProductDraft)
    suspend fun searchArtisans(area: String): Result<List<ArtisanProfile>>
    suspend fun submit(input: ProductionRequestInput): Result<ProductionRequest>
    fun observeRequests(): Flow<List<ProductionRequest>>
    suspend fun getRequest(id: String): Result<ProductionRequest>
}
