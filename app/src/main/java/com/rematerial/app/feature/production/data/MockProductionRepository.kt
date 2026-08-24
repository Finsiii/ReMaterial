package com.rematerial.app.feature.production.data

import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductDraft
import com.rematerial.app.feature.production.domain.ProductionRequest
import com.rematerial.app.feature.production.domain.ProductionRequestInput
import com.rematerial.app.feature.production.domain.ProductionRepository
import com.rematerial.app.feature.production.domain.ProductionStatus
import com.rematerial.app.feature.production.domain.isReadyForProduction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockProductionRepository : ProductionRepository {
    private var draft = ProductDraft()
    private val requests = MutableStateFlow<List<ProductionRequest>>(emptyList())

    private val artisans = listOf(
        ArtisanProfile(
            "artisan-bima", "Bima Pratama", "Bandung · Cicendo", "3,2 km",
            listOf("Logam bekas", "Lampu kecil", "Las ringan"), "5–7 hari", "Rp450.000–650.000", "Menerima pesanan minggu ini",
            "Bima mengolah kabel dan komponen lama menjadi benda pakai dengan detail yang rapi.",
            "Paling cocok untuk lampu meja: punya alat las ringan dan pernah membuat rangka dari tembaga.",
            whatsapp = "081234567891", portfolioImageKeys = listOf("material_metal", "material_cable"),
        ),
        ArtisanProfile(
            "artisan-sari", "Sari Kurnia", "Bandung · Sukajadi", "5,8 km",
            listOf("Kabel dan tekstil", "Kap lampu", "Finishing"), "7–10 hari", "Rp375.000–575.000", "Slot tersedia mulai 12 September",
            "Sari menggabungkan sisa kabel dengan tekstil lokal untuk karya rumah yang hangat.",
            "Punya kemampuan finishing dan kap lampu yang paling mendekati bentuk pilihanmu.",
            whatsapp = "081234567892", latitude = -6.895, longitude = 107.604, portfolioImageKeys = listOf("material_textile", "material_wood"), rating = "4,8", completedJobs = "64 karya selesai",
        ),
        ArtisanProfile(
            "artisan-raka", "Raka Workshop", "Bandung · Antapani", "8,4 km",
            listOf("Logam", "Furniture kecil", "Patina"), "10–14 hari", "Rp600.000–900.000", "Menerima pesanan terbatas",
            "Workshop kecil untuk eksperimen material dengan karakter permukaan yang kuat.",
            "Pilihan tepat bila kamu ingin mengeksplorasi patina dan konstruksi logam yang lebih eksperimental.",
            whatsapp = "081234567893", latitude = -6.91, longitude = 107.66, portfolioImageKeys = listOf("material_metal", "material_wood"), rating = "4,7", completedJobs = "41 karya selesai",
        ),
    )

    init {
        requests.value = listOf(seededRequest())
    }

    override fun currentDraft(): ProductDraft = draft

    override fun saveDraft(draft: ProductDraft) { this.draft = draft }

    override suspend fun searchArtisans(area: String): Result<List<ArtisanProfile>> {
        val query = area.trim()
        return Result.Success(if (query.isBlank()) artisans else artisans.filter { it.area.contains(query, true) || it.name.contains(query, true) })
    }

    override suspend fun submit(input: ProductionRequestInput): Result<ProductionRequest> {
        val artisan = artisans.firstOrNull { it.id == input.artisanId } ?: return Result.Failure(DomainFailure.Unavailable)
        if (!input.draft.isReadyForProduction()) {
            return Result.Failure(DomainFailure.Validation(listOf("Pilih rekomendasi dari analisis bahan terlebih dahulu.")))
        }
        if (!input.draft.safetyAllowed) {
            return Result.Failure(DomainFailure.Validation(listOf("Bahan ini belum aman untuk diproduksi.")))
        }
        if (input.quantity.isBlank() || input.address.isBlank() || input.targetDate.isBlank() || input.phone.filter { it.isDigit() }.length < 10) {
            return Result.Failure(DomainFailure.Validation(listOf("Kuantitas, alamat, target selesai, dan nomor kontak wajib diisi.")))
        }
        val request = ProductionRequest("PR-${requests.value.size + 2401}", artisan, input.draft, input.quantity, input.notes, input.address, input.targetDate, input.phone, input.whatsapp, input.preferredContact)
        requests.value = listOf(request) + requests.value
        return Result.Success(request)
    }

    override fun observeRequests(): Flow<List<ProductionRequest>> = requests.asStateFlow()

    override suspend fun getRequest(id: String): Result<ProductionRequest> = requests.value.firstOrNull { it.id == id }?.let { Result.Success(it) } ?: Result.Failure(DomainFailure.Unavailable)

    private fun seededRequest() = ProductionRequest(
        id = "PR-2401", artisan = artisans.first(), draft = ProductDraft(ProductOptionId("lampu-kabel"), "Lampu meja dari kabel tembaga", "Kabel tembaga · 2,45 kg · kondisi layak olah", "1 unit", "analysis-demo", true), quantity = "1 unit",
        notes = "Buat kabel terlihat sebagai aksen, bukan ditutup seluruhnya.", address = "Jl. Merdeka 24, Bandung",
        targetDate = "20 September 2026", phone = "081234567890", whatsapp = "081234567890", status = ProductionStatus.PROCESSING,
    )
}
