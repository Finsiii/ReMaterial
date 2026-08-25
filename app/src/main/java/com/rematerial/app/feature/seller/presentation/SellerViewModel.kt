package com.rematerial.app.feature.seller.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.CommerceError
import com.rematerial.app.core.commerce.ListingState
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.feature.seller.domain.SellerOrder
import com.rematerial.app.feature.seller.domain.SellerProfile
import com.rematerial.app.feature.seller.domain.SellerRepository
import com.rematerial.app.feature.identity.domain.VerificationDocumentKind
import com.rematerial.app.feature.identity.domain.VerificationDocumentStore
import com.rematerial.app.core.model.Result
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SellerState(
    val listings: List<SellerListing> = emptyList(),
    val orders: List<SellerOrder> = emptyList(),
    val profile: SellerProfile = SellerProfile(),
    val selectedListing: SellerListing? = null,
    val selectedOrder: SellerOrder? = null,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SellerViewModel @Inject constructor(
    private val repository: SellerRepository,
    private val documentStore: VerificationDocumentStore,
) : ViewModel() {
    private val selectedListingId = MutableStateFlow<String?>(null)
    private val selectedOrderId = MutableStateFlow<String?>(null)
    private val isMutating = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<SellerState> = combine(
        listOf(repository.listings, repository.orders, repository.profile, selectedListingId, selectedOrderId, isMutating, error),
    ) { values ->
        @Suppress("UNCHECKED_CAST") val listings = values[0] as List<SellerListing>
        @Suppress("UNCHECKED_CAST") val orders = values[1] as List<SellerOrder>
        val listingId = values[3] as String?
        val orderId = values[4] as String?
        SellerState(
            listings,
            orders,
            values[2] as SellerProfile,
            listings.firstOrNull { it.id == listingId },
            orders.firstOrNull { it.id == orderId },
            values[5] as Boolean,
            values[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellerState())

    fun selectListing(value: SellerListing?) { selectedListingId.value = value?.id }
    fun selectOrder(value: SellerOrder?) { selectedOrderId.value = value?.id }
    fun clearError() { error.value = null }
    fun importListingImage(sourceUri: String, onImported: (String) -> Unit) = mutate {
        when (val result = documentStore.import(sourceUri, VerificationDocumentKind.STORE_EVIDENCE)) {
            is Result.Success -> onImported(File(result.value.privatePath).toURI().toString())
            is Result.Failure -> error.value = "Foto produk tidak dapat disimpan."
        }
    }

    fun saveListing(value: SellerListing, onSaved: () -> Unit = {}) = mutate {
        when (val result = repository.saveListing(value)) {
            is CommerceResult.Success -> { selectedListingId.value = result.value.id; onSaved() }
            is CommerceResult.Failure -> error.value = result.error.sellerMessage()
        }
    }

    fun toggleListing(id: String, currentlyPublished: Boolean) = mutate {
        handle(repository.setListingState(id, if (currentlyPublished) ListingState.PAUSED else ListingState.PUBLISHED))
    }
    fun archiveListing(id: String) = mutate { handle(repository.setListingState(id, ListingState.ARCHIVED)) }

    fun transitionOrder(id: String, status: OrderStatus) = mutate { handle(repository.transitionOrder(id, status)) }
    fun saveProfile(value: SellerProfile, onSaved: () -> Unit = {}) = mutate {
        when (val result = repository.saveProfile(value)) {
            is CommerceResult.Success -> onSaved()
            is CommerceResult.Failure -> error.value = result.error.sellerMessage()
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (isMutating.value) return
        isMutating.value = true
        error.value = null
        viewModelScope.launch {
            try { block() } finally { isMutating.value = false }
        }
    }

    private fun <T> handle(result: CommerceResult<T>) {
        if (result is CommerceResult.Failure) error.value = result.error.sellerMessage()
    }
}

private fun CommerceError.sellerMessage(): String = when (this) {
    CommerceError.SellerNotVerified -> "Verifikasi penjual belum disetujui. Produk tetap dapat disimpan sebagai draft."
    CommerceError.ProductNotFound, CommerceError.OrderNotFound -> "Data tidak ditemukan atau bukan milik akun ini."
    CommerceError.InvalidTransition -> "Status sudah berubah. Periksa pesanan lalu coba lagi."
    CommerceError.OutOfStock -> "Stok produk harus lebih dari nol untuk diterbitkan."
    CommerceError.MediaImportFailed -> "Foto produk tidak dapat disimpan."
    CommerceError.ListingUnavailable -> "Produk sedang tidak tersedia."
    CommerceError.DifferentSeller, CommerceError.EmptyCart -> "Operasi tidak dapat diselesaikan."
    is CommerceError.InvalidInput -> "Lengkapi data yang masih kosong."
}
