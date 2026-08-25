package com.rematerial.app.feature.production.data

import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.SessionStore
import com.rematerial.app.feature.identity.domain.VerificationStatus
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.core.model.AccountId
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductDraft
import com.rematerial.app.feature.production.domain.ProductionRequest
import com.rematerial.app.feature.production.domain.ProductionRequestInput
import com.rematerial.app.feature.production.domain.ProductionRepository
import com.rematerial.app.feature.production.domain.ProductionStatus
import com.rematerial.app.feature.production.domain.isReadyForProduction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DemoProductionStore(seedDemoRequest: Boolean = true) {
    private val mutex = Mutex()
    private var nextRequest = 2402
    val artisans: List<ArtisanProfile> = demoArtisans()
    private val mutableRequests = MutableStateFlow<List<ProductionRequest>>(emptyList())
    val requests: StateFlow<List<ProductionRequest>> = mutableRequests.asStateFlow()

    init {
        if (seedDemoRequest) mutableRequests.value = listOf(seedRequest())
    }

    suspend fun submit(input: ProductionRequestInput, ownerAccountId: String, customerName: String): Result<ProductionRequest> = mutex.withLock {
        val artisan = artisans.firstOrNull { it.id == input.artisanId && it.verified }
            ?: return@withLock Result.Failure(DomainFailure.Unavailable)
        val violations = buildList {
            if (!input.draft.isReadyForProduction()) add("Pilih rekomendasi dari analisis bahan terlebih dahulu.")
            if (input.quantity <= 0) add("Kuantitas minimal satu unit.")
            if (input.address.isBlank()) add("Alamat wajib diisi.")
            if (!ISO_DATE.matches(input.targetDateIso)) add("Target selesai harus berformat YYYY-MM-DD.")
            if (!PHONE.matches(input.phone.filter(Char::isDigit))) add("Nomor kontak tidak valid.")
        }
        if (violations.isNotEmpty()) return@withLock Result.Failure(DomainFailure.Validation(violations))
        val request = ProductionRequest(
            id = "PR-${nextRequest++}", ownerAccountId = ownerAccountId, artisan = artisan, draft = input.draft, quantity = input.quantity,
            notes = input.notes, address = input.address.trim(), targetDateIso = input.targetDateIso,
            phone = input.phone, whatsapp = input.whatsapp.ifBlank { input.phone }, preferredContact = input.preferredContact,
            customerName = customerName,
        )
        mutableRequests.value = listOf(request) + mutableRequests.value
        Result.Success(request)
    }

    suspend fun transition(id: String, target: ProductionStatus, artisanId: String): Result<ProductionRequest> = mutex.withLock {
        val current = mutableRequests.value.firstOrNull { it.id == id } ?: return@withLock Result.Failure(DomainFailure.Unavailable)
        if (current.artisan.id != artisanId) return@withLock Result.Failure(DomainFailure.Unauthorized)
        val valid = when (current.status) {
            ProductionStatus.SUBMITTED -> target == ProductionStatus.NEEDS_CLARIFICATION || target == ProductionStatus.ACCEPTED
            ProductionStatus.NEEDS_CLARIFICATION -> target == ProductionStatus.ACCEPTED
            ProductionStatus.ACCEPTED -> target == ProductionStatus.IN_PRODUCTION
            ProductionStatus.IN_PRODUCTION -> target == ProductionStatus.READY_FOR_REVIEW
            ProductionStatus.READY_FOR_REVIEW, ProductionStatus.COMPLETED, ProductionStatus.CANCELLED -> false
            ProductionStatus.REVISION_REQUESTED -> target == ProductionStatus.IN_PRODUCTION
        }
        if (!valid) return@withLock Result.Failure(DomainFailure.Validation(listOf("Status pekerjaan tidak dapat dipindahkan ke tahap tersebut.")))
        val updated = current.copy(status = target)
        mutableRequests.value = mutableRequests.value.map { if (it.id == id) updated else it }
        Result.Success(updated)
    }

    suspend fun transitionForCustomer(id: String, target: ProductionStatus, ownerAccountId: String): Result<ProductionRequest> = mutex.withLock {
        val current = mutableRequests.value.firstOrNull { it.id == id }
            ?: return@withLock Result.Failure(DomainFailure.Unavailable)
        if (current.ownerAccountId != ownerAccountId) return@withLock Result.Failure(DomainFailure.Unauthorized)
        val valid = when (current.status) {
            ProductionStatus.SUBMITTED, ProductionStatus.NEEDS_CLARIFICATION,
            ProductionStatus.ACCEPTED, ProductionStatus.IN_PRODUCTION -> target == ProductionStatus.CANCELLED
            ProductionStatus.READY_FOR_REVIEW -> target == ProductionStatus.COMPLETED || target == ProductionStatus.REVISION_REQUESTED || target == ProductionStatus.CANCELLED
            ProductionStatus.REVISION_REQUESTED, ProductionStatus.COMPLETED, ProductionStatus.CANCELLED -> false
        }
        if (!valid) return@withLock Result.Failure(DomainFailure.Validation(listOf("Status permintaan belum dapat dipindahkan ke tahap tersebut.")))
        val updated = current.copy(status = target)
        mutableRequests.value = mutableRequests.value.map { if (it.id == id) updated else it }
        Result.Success(updated)
    }

    private fun seedRequest() = ProductionRequest(
        id = "PR-2401", ownerAccountId = "demo-user", artisan = artisans.first(),
        draft = ProductDraft(
            ProductOptionId("lampu-kabel"), "Lampu meja dari kabel tembaga", "Kabel tembaga · 2,45 kg · kondisi layak olah",
            "1 kg", "analysis-demo", true, listOf("cable", "metal"), listOf("hand-tools", "finishing-tools"),
            listOf("basic-making", "surface-finishing"), 89.0, "2,2 kg",
        ),
        quantity = 1, notes = "Buat kabel terlihat sebagai aksen, bukan ditutup seluruhnya.",
        address = "Jl. Merdeka 24, Bandung", targetDateIso = "2026-09-20", phone = "081234567890",
        whatsapp = "081234567890", status = ProductionStatus.IN_PRODUCTION, customerName = "Dika",
    )

    private companion object {
        val PHONE = Regex("^(?:62|0)[0-9]{8,13}$")
        val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    }
}

class MockProductionRepository @Inject constructor(
    private val store: DemoProductionStore,
    private val sessions: SessionStore,
) : ProductionRepository {
    constructor() : this(
        DemoProductionStore(),
        InMemorySessionStore(Session(AccountId("demo-user"), "user@rematerial.demo", Role.USER, "Dika", verificationStatus = VerificationStatus.NOT_REQUIRED)),
    )

    private var draft = ProductDraft()
    override fun currentDraft(): ProductDraft = draft
    override fun saveDraft(draft: ProductDraft) { this.draft = draft }

    override suspend fun searchArtisans(area: String): Result<List<ArtisanProfile>> {
        val required = draft
        val ranked = store.artisans.asSequence()
            .filter { it.verified }
            .filter { area.isBlank() || it.area.contains(area.trim(), true) }
            .filter { required.requiredCapabilities.all(it.capabilityKeys::contains) }
            .filter { required.requiredTools.all(it.toolKeys::contains) }
            .filter { required.requiredSkills.all(it.skillKeys::contains) }
            .sortedBy { it.distance.substringBefore(' ').replace(',', '.').toDoubleOrNull() ?: Double.MAX_VALUE }
            .toList()
        return Result.Success(ranked)
    }

    override suspend fun submit(input: ProductionRequestInput): Result<ProductionRequest> {
        val session = sessions.current()
        if (session?.role != Role.USER) return Result.Failure(DomainFailure.Unauthorized)
        return store.submit(input, session.accountId.value, session.displayName)
    }

    override fun observeRequests(): Flow<List<ProductionRequest>> = combine(store.requests, sessions.session) { requests, session ->
        val owner = session?.takeIf { it.role == Role.USER }?.accountId?.value
        requests.filter { owner != null && it.ownerAccountId == owner }
    }
    override suspend fun getRequest(id: String): Result<ProductionRequest> =
        store.requests.value.firstOrNull { it.id == id && it.ownerAccountId == sessions.current()?.accountId?.value && sessions.current()?.role == Role.USER }
            ?.let { Result.Success(it) } ?: Result.Failure(DomainFailure.Unauthorized)

    override suspend fun completeRequest(id: String): Result<ProductionRequest> = transitionCustomer(id, ProductionStatus.COMPLETED)
    override suspend fun requestRevision(id: String): Result<ProductionRequest> = transitionCustomer(id, ProductionStatus.REVISION_REQUESTED)
    override suspend fun cancelRequest(id: String): Result<ProductionRequest> = transitionCustomer(id, ProductionStatus.CANCELLED)

    private suspend fun transitionCustomer(id: String, target: ProductionStatus): Result<ProductionRequest> {
        val session = sessions.current()
        if (session?.role != Role.USER) return Result.Failure(DomainFailure.Unauthorized)
        return store.transitionForCustomer(id, target, session.accountId.value)
    }
}

private fun demoArtisans() = listOf(
    ArtisanProfile(
        "artisan-bima", "Bima Pratama", "Bandung · Cicendo", "3,2 km",
        listOf("Logam bekas", "Lampu kecil", "Las ringan"), "5–7 hari", "Rp450.000–650.000", "Menerima pesanan minggu ini",
        "Bima mengolah kabel dan komponen lama menjadi benda pakai dengan detail yang rapi.",
        "Cocok karena kemampuan, alat, dan teknik finishing memenuhi kebutuhan produk.",
        portfolioImageKeys = listOf("material_metal", "material_cable"),
        capabilityKeys = setOf("cable", "metal"), toolKeys = setOf("hand-tools", "measuring-tools", "cutting-tools", "finishing-tools", "light-welding"),
        skillKeys = setOf("basic-making", "precision-making", "surface-finishing"),
    ),
    ArtisanProfile(
        "artisan-sari", "Sari Kurnia", "Bandung · Sukajadi", "5,8 km",
        listOf("Kabel dan tekstil", "Kap lampu", "Finishing"), "7–10 hari", "Rp375.000–575.000", "Slot tersedia mulai 12 September",
        "Sari menggabungkan sisa kabel dengan tekstil lokal untuk karya rumah yang hangat.",
        "Cocok untuk produk tekstil dan kap lampu.", latitude = -6.895, longitude = 107.604,
        portfolioImageKeys = listOf("material_textile", "material_wood"), rating = "4,8", completedJobs = "64 karya selesai",
        capabilityKeys = setOf("cable", "textile"), toolKeys = setOf("hand-tools", "cutting-tools", "measuring-tools", "finishing-tools", "sewing-tools"),
        skillKeys = setOf("basic-making", "precision-making", "surface-finishing", "sewing"),
    ),
    ArtisanProfile(
        "artisan-raka", "Raka Workshop", "Bandung · Antapani", "8,4 km",
        listOf("Logam", "Furniture kecil", "Patina"), "10–14 hari", "Rp600.000–900.000", "Menerima pesanan terbatas",
        "Workshop kecil untuk eksperimen material dengan karakter permukaan yang kuat.",
        "Cocok untuk patina dan konstruksi logam.", latitude = -6.91, longitude = 107.66,
        portfolioImageKeys = listOf("material_metal", "material_wood"), rating = "4,7", completedJobs = "41 karya selesai",
        capabilityKeys = setOf("metal", "wood", "plastic"), toolKeys = setOf("hand-tools", "cutting-tools", "measuring-tools", "finishing-tools", "light-welding"),
        skillKeys = setOf("basic-making", "precision-making", "surface-finishing", "patina"),
    ),
)
