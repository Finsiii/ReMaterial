package com.rematerial.app.feature.artisan.data

import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.artisan.domain.ArtisanJob
import com.rematerial.app.feature.artisan.domain.ArtisanJobStatus
import com.rematerial.app.feature.artisan.domain.ArtisanProfileDraft
import com.rematerial.app.feature.artisan.domain.ArtisanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockArtisanRepository : ArtisanRepository {
    private val jobs = MutableStateFlow(
        listOf(
            ArtisanJob("JOB-109", "Dika Prasetya", "Lampu meja kabel tembaga", "Kabel tembaga · 2,45 kg", "1 unit", "20 September 2026", "Jl. Merdeka 24, Bandung", "Buat kabel terlihat sebagai aksen, bukan ditutup seluruhnya.", ArtisanJobStatus.PROCESSING),
            ArtisanJob("JOB-108", "Nadia S.", "Rak dinding modular", "Kayu palet · 4 papan", "1 set", "24 September 2026", "Jl. Ciumbuleuit 18, Bandung", "Warna kayu natural, sudut tidak terlalu tajam.", ArtisanJobStatus.NEW),
            ArtisanJob("JOB-104", "Reno A.", "Organizer meja", "Aluminium bekas · 1,2 kg", "2 unit", "28 September 2026", "Jl. Buah Batu 10, Bandung", "Sisakan tekstur goresan kecil sebagai karakter.", ArtisanJobStatus.ACCEPTED),
        ),
    )
    private var profileDraft = ArtisanProfileDraft()

    override fun observeJobs(): Flow<List<ArtisanJob>> = jobs.asStateFlow()

    override suspend fun updateJob(id: String, status: ArtisanJobStatus): Result<ArtisanJob> {
        val job = jobs.value.firstOrNull { it.id == id } ?: return Result.Failure(DomainFailure.Unavailable)
        val allowed = when (job.status) {
            ArtisanJobStatus.NEW -> status == ArtisanJobStatus.ACCEPTED || status == ArtisanJobStatus.REVISION
            ArtisanJobStatus.ACCEPTED -> status == ArtisanJobStatus.PROCESSING || status == ArtisanJobStatus.REVISION
            ArtisanJobStatus.REVISION -> status == ArtisanJobStatus.ACCEPTED
            ArtisanJobStatus.PROCESSING -> status == ArtisanJobStatus.COMPLETED || status == ArtisanJobStatus.REVISION
            ArtisanJobStatus.COMPLETED -> false
        }
        if (!allowed) return Result.Failure(DomainFailure.Validation(listOf("Status pekerjaan harus berurutan.")))
        val updated = job.copy(status = status)
        jobs.value = jobs.value.map { if (it.id == id) updated else it }
        return Result.Success(updated)
    }

    override fun profile(): ArtisanProfileDraft = profileDraft
    override fun saveProfile(profile: ArtisanProfileDraft) { profileDraft = profile }
}
