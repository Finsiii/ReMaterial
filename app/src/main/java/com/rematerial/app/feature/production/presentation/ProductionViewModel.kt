package com.rematerial.app.feature.production.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.production.domain.ArtisanProfile
import com.rematerial.app.feature.production.domain.ProductDraft
import com.rematerial.app.feature.production.domain.ProductionRequest
import com.rematerial.app.feature.production.domain.ProductionRequestInput
import com.rematerial.app.feature.production.domain.ProductionRepository
import com.rematerial.app.feature.production.domain.isReadyForProduction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProductionPage { DISCOVERY, DETAIL, FORM, CONFIRMED, HISTORY, REQUEST }

data class ProductionState(
    val page: ProductionPage = ProductionPage.DISCOVERY,
    val draft: ProductDraft = ProductDraft(),
    val area: String = "",
    val artisans: List<ArtisanProfile> = emptyList(),
    val selectedArtisan: ArtisanProfile? = null,
    val quantity: String = "1 unit",
    val notes: String = "",
    val address: String = "",
    val targetDate: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val preferredContact: String = "WhatsApp",
    val requests: List<ProductionRequest> = emptyList(),
    val submitted: ProductionRequest? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val repository: ProductionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductionState(draft = repository.currentDraft()))
    val state: StateFlow<ProductionState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.observeRequests().collect { list -> _state.update { it.copy(requests = list) } } }
        search()
    }

    fun saveDraft(draft: ProductDraft) { repository.saveDraft(draft); _state.update { it.copy(draft = draft) } }
    fun setArea(value: String) { _state.update { it.copy(area = value) } }
    fun search() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.searchArtisans(_state.value.area)) {
                is Result.Success -> _state.update { it.copy(artisans = result.value, loading = false) }
                is Result.Failure -> _state.update { it.copy(loading = false, error = "Pengrajin belum dapat dimuat.") }
            }
        }
    }
    fun openDetail(artisan: ArtisanProfile) {
        if (!_state.value.draft.isReadyForProduction()) {
            _state.update { it.copy(page = ProductionPage.DISCOVERY, error = "Analisis bahan dulu untuk melihat pengrajin yang cocok.") }
        } else {
            _state.update { it.copy(selectedArtisan = artisan, page = ProductionPage.DETAIL, error = null) }
        }
    }
    fun openForm() {
        val current = _state.value
        if (!current.draft.isReadyForProduction()) {
            _state.update { it.copy(page = ProductionPage.DISCOVERY, error = "Analisis bahan dulu untuk memilih produk yang aman dibuat.") }
        } else {
            _state.update { it.copy(page = ProductionPage.FORM, error = null) }
        }
    }
    fun setQuantity(value: String) { _state.update { it.copy(quantity = value) } }
    fun setNotes(value: String) { _state.update { it.copy(notes = value) } }
    fun setAddress(value: String) { _state.update { it.copy(address = value) } }
    fun setTargetDate(value: String) { _state.update { it.copy(targetDate = value) } }
    fun setPhone(value: String) { _state.update { it.copy(phone = value) } }
    fun setWhatsapp(value: String) { _state.update { it.copy(whatsapp = value) } }
    fun setPreferredContact(value: String) { _state.update { it.copy(preferredContact = value) } }
    fun submit() {
        val current = _state.value
        val artisan = current.selectedArtisan ?: return
        if (!current.draft.isReadyForProduction()) {
            _state.update { it.copy(page = ProductionPage.DISCOVERY, error = "Analisis bahan dulu untuk memilih produk yang aman dibuat.") }
            return
        }
        if (current.quantity.isBlank() || current.address.isBlank() || current.targetDate.isBlank() || current.phone.filter { it.isDigit() }.length < 10) {
            _state.update { it.copy(error = "Lengkapi kuantitas, alamat, target selesai, dan nomor kontak.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.submit(ProductionRequestInput(artisan.id, current.draft, current.quantity, current.notes, current.address, current.targetDate, current.phone, current.whatsapp, current.preferredContact))) {
                is Result.Success -> _state.update { it.copy(submitted = result.value, page = ProductionPage.CONFIRMED, loading = false) }
                is Result.Failure -> _state.update { it.copy(loading = false, error = "Permintaan belum dapat dikirim.") }
            }
        }
    }
    fun openHistory() { _state.update { it.copy(page = ProductionPage.HISTORY) } }
    fun openRequest(request: ProductionRequest) { _state.update { it.copy(submitted = request, page = ProductionPage.REQUEST) } }
    fun backToDiscovery() { _state.update { it.copy(page = ProductionPage.DISCOVERY, selectedArtisan = null, error = null) } }
}
