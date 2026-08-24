package com.rematerial.app.feature.artisan.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.artisan.domain.ArtisanJob
import com.rematerial.app.feature.artisan.domain.ArtisanJobStatus
import com.rematerial.app.feature.artisan.domain.ArtisanProfileDraft
import com.rematerial.app.feature.artisan.domain.ArtisanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtisanState(
    val jobs: List<ArtisanJob> = emptyList(),
    val selectedJob: ArtisanJob? = null,
    val profile: ArtisanProfileDraft = ArtisanProfileDraft(),
    val error: String? = null,
)

@HiltViewModel
class ArtisanViewModel @Inject constructor(
    private val repository: ArtisanRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ArtisanState(profile = repository.profile()))
    val state: StateFlow<ArtisanState> = _state.asStateFlow()

    init { viewModelScope.launch { repository.observeJobs().collect { jobs -> _state.update { it.copy(jobs = jobs, selectedJob = it.selectedJob?.let { selected -> jobs.firstOrNull { job -> job.id == selected.id } }) } } } }

    fun selectJob(job: ArtisanJob) { _state.update { it.copy(selectedJob = job, error = null) } }
    fun clearJob() { _state.update { it.copy(selectedJob = null, error = null) } }
    fun transition(status: ArtisanJobStatus) {
        val job = _state.value.selectedJob ?: return
        viewModelScope.launch {
            when (val result = repository.updateJob(job.id, status)) {
                is Result.Success -> _state.update { it.copy(selectedJob = result.value, error = null) }
                is Result.Failure -> _state.update { it.copy(error = "Status belum dapat diperbarui.") }
            }
        }
    }
    fun updateProfile(profile: ArtisanProfileDraft) { repository.saveProfile(profile); _state.update { it.copy(profile = profile) } }
}
