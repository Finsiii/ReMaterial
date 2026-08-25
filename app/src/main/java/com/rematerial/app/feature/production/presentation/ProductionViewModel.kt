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
import com.rematerial.app.feature.identity.domain.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ProductionPage { DISCOVERY, DETAIL, FORM, CONFIRMED, HISTORY, REQUEST }

data class ProductionState(
    val page: ProductionPage = ProductionPage.DISCOVERY,
    val draft: ProductDraft = ProductDraft(),
    val area: String = "",
    val artisans: List<ArtisanProfile> = emptyList(),
    val selectedArtisan: ArtisanProfile? = null,
    val quantity: String = "1",
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
    private var activeAccountId: String? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { repository.observeRequests().collect { list -> _state.update { it.copy(requests = list) } } }
        search()
    }

    fun saveDraft(draft: ProductDraft) {
        repository.saveDraft(draft)
        _state.update { it.copy(draft = draft, artisans = emptyList(), selectedArtisan = null, error = null) }
        if (draft.isReadyForProduction()) search()
    }
    fun applySession(session: Session?) {
        if (session == null) {
            activeAccountId = null
            repository.saveDraft(ProductDraft())
            _state.value = ProductionState()
            return
        }
        val accountId = session.accountId.value
        if (activeAccountId != accountId) {
            activeAccountId = accountId
            repository.saveDraft(ProductDraft())
            _state.value = ProductionState(
                area = session.location?.area.orEmpty(),
                address = session.location?.address.orEmpty(),
                phone = session.contact?.phone.orEmpty(),
                whatsapp = session.contact?.whatsapp.orEmpty(),
                preferredContact = session.contact?.preferred?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "WhatsApp",
            )
            search()
            return
        }
        _state.update {
            it.copy(
                area = if (it.area.isBlank()) session.location?.area.orEmpty() else it.area,
                address = if (it.address.isBlank()) session.location?.address.orEmpty() else it.address,
                phone = if (it.phone.isBlank()) session.contact?.phone.orEmpty() else it.phone,
                whatsapp = if (it.whatsapp.isBlank()) session.contact?.whatsapp.orEmpty() else it.whatsapp,
                preferredContact = session.contact?.preferred?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: it.preferredContact,
            )
        }
    }
    fun setArea(value: String) { _state.update { it.copy(area = value, artisans = emptyList(), selectedArtisan = null, error = null) } }
    fun search() {
        searchJob?.cancel()
        val area = _state.value.area
        val draft = _state.value.draft
        searchJob = viewModelScope.launch {
            if (!draft.isReadyForProduction()) {
                _state.update { it.copy(artisans = emptyList(), loading = false) }
                return@launch
            }
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.searchArtisans(area)) {
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
        if (current.loading) return
        val artisan = current.selectedArtisan ?: return
        if (!current.draft.isReadyForProduction()) {
            _state.update { it.copy(page = ProductionPage.DISCOVERY, error = "Analisis bahan dulu untuk memilih produk yang aman dibuat.") }
            return
        }
        val quantity = current.quantity.toIntOrNull()
        if (quantity == null || quantity <= 0 || current.address.isBlank() || !ISO_DATE.matches(current.targetDate) || current.phone.filter { it.isDigit() }.length < 10) {
            _state.update { it.copy(error = "Lengkapi kuantitas, alamat, target selesai, dan nomor kontak.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.submit(ProductionRequestInput(artisan.id, current.draft, quantity, current.notes, current.address, current.targetDate, current.phone, current.whatsapp, current.preferredContact))) {
                is Result.Success -> _state.update { it.copy(submitted = result.value, page = ProductionPage.CONFIRMED, loading = false) }
                is Result.Failure -> _state.update { it.copy(loading = false, error = "Permintaan belum dapat dikirim.") }
            }
        }
    }
    fun openHistory() { _state.update { it.copy(page = ProductionPage.HISTORY) } }
    fun openRequest(request: ProductionRequest) { _state.update { it.copy(submitted = request, page = ProductionPage.REQUEST) } }
    fun completeRequest() { transitionRequest { repository.completeRequest(it) } }
    fun requestRevision() { transitionRequest { repository.requestRevision(it) } }
    fun cancelRequest() { transitionRequest { repository.cancelRequest(it) } }

    private fun transitionRequest(action: suspend (String) -> Result<ProductionRequest>) {
        val request = _state.value.submitted ?: return
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = action(request.id)) {
                is Result.Success -> _state.update { it.copy(submitted = result.value, loading = false, error = null) }
                is Result.Failure -> _state.update { it.copy(loading = false, error = "Permintaan belum dapat diperbarui.") }
            }
        }
    }
    fun backToDiscovery() { _state.update { it.copy(page = ProductionPage.DISCOVERY, selectedArtisan = null, error = null) } }
    fun back(): Boolean {
        val target = productionBackTarget(_state.value.page) ?: return false
        _state.update {
            it.copy(
                page = target,
                selectedArtisan = if (target == ProductionPage.DISCOVERY) null else it.selectedArtisan,
                error = null,
            )
        }
        return true
    }
    fun normalizePage() {
        _state.update {
            when {
                it.page in setOf(ProductionPage.DETAIL, ProductionPage.FORM) && it.selectedArtisan == null -> it.copy(page = ProductionPage.DISCOVERY)
                it.page == ProductionPage.CONFIRMED && it.submitted == null -> it.copy(page = ProductionPage.DISCOVERY)
                it.page == ProductionPage.REQUEST && it.submitted == null -> it.copy(page = ProductionPage.HISTORY)
                it.page == ProductionPage.REQUEST && it.submitted?.let { request -> it.requests.none { current -> current.id == request.id } } == true -> it.copy(page = ProductionPage.HISTORY, submitted = null)
                else -> it
            }
        }
    }
}

private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

internal fun productionBackTarget(page: ProductionPage): ProductionPage? = when (page) {
    ProductionPage.DISCOVERY -> null
    ProductionPage.DETAIL, ProductionPage.CONFIRMED, ProductionPage.HISTORY -> ProductionPage.DISCOVERY
    ProductionPage.FORM -> ProductionPage.DETAIL
    ProductionPage.REQUEST -> ProductionPage.HISTORY
}
