package com.rematerial.app.feature.artisan.data

import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.artisan.domain.ArtisanJob
import com.rematerial.app.feature.artisan.domain.ArtisanProfileDraft
import com.rematerial.app.feature.artisan.domain.ArtisanRepository
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.SessionStore
import com.rematerial.app.feature.identity.domain.VerificationStatus
import com.rematerial.app.feature.production.data.DemoProductionStore
import com.rematerial.app.feature.production.domain.ProductionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MockArtisanRepository @Inject constructor(
    private val store: DemoProductionStore,
    private val sessions: SessionStore,
) : ArtisanRepository {
    constructor() : this(DemoProductionStore(), InMemorySessionStore())

    private val profileDrafts = MutableStateFlow<Map<String, ArtisanProfileDraft>>(emptyMap())
    private fun artisanId(): String? = sessions.current()?.let {
        if (it.role != Role.ARTISAN) null else if (it.accountId.value == "demo-artisan") "artisan-bima" else it.accountId.value
    }

    override fun observeJobs(): Flow<List<ArtisanJob>> = store.requests.map { requests ->
        val id = artisanId()
        requests.filter { it.artisan.id == id }.map { request ->
            ArtisanJob(
                request.id, request.customerName, request.draft.title, request.draft.materialSummary,
                request.quantity, request.targetDateIso, request.address, request.notes, request.status,
                request.phone, request.whatsapp, request.preferredContact,
                request.draft.requiredCapabilities, request.draft.requiredTools, request.draft.requiredSkills,
                request.draft.provisionalScore, request.draft.estimatedUsage,
            )
        }
    }

    override suspend fun updateJob(id: String, status: ProductionStatus): Result<ArtisanJob> {
        val session = sessions.current()
        if (session?.role != Role.ARTISAN || session.verificationStatus != VerificationStatus.APPROVED) {
            return Result.Failure(DomainFailure.Unauthorized)
        }
        val artisanId = artisanId() ?: return Result.Failure(DomainFailure.Unauthorized)
        return when (val result = store.transition(id, status, artisanId)) {
            is Result.Failure -> result
            is Result.Success -> Result.Success(
                ArtisanJob(
                    result.value.id, result.value.customerName, result.value.draft.title, result.value.draft.materialSummary,
                    result.value.quantity, result.value.targetDateIso, result.value.address, result.value.notes, result.value.status,
                    result.value.phone, result.value.whatsapp, result.value.preferredContact,
                    result.value.draft.requiredCapabilities, result.value.draft.requiredTools, result.value.draft.requiredSkills,
                    result.value.draft.provisionalScore, result.value.draft.estimatedUsage,
                ),
            )
        }
    }

    override fun profile(): ArtisanProfileDraft = artisanId()?.let { profileDrafts.value[it] } ?: ArtisanProfileDraft()
    override fun saveProfile(profile: ArtisanProfileDraft) {
        artisanId()?.let { id -> profileDrafts.value = profileDrafts.value + (id to profile) }
    }
}
