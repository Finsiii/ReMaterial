package com.rematerial.app.feature.seller.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.feature.seller.domain.SellerOrder
import com.rematerial.app.feature.seller.domain.SellerProfile
import com.rematerial.app.feature.seller.domain.SellerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SellerState(val listings: List<SellerListing> = emptyList(), val orders: List<SellerOrder> = emptyList(), val profile: SellerProfile = SellerProfile(), val selectedListing: SellerListing? = null, val selectedOrder: SellerOrder? = null)

@HiltViewModel
class SellerViewModel @Inject constructor(private val repository: SellerRepository) : ViewModel() {
    private val selectedListing = kotlinx.coroutines.flow.MutableStateFlow<SellerListing?>(null)
    private val selectedOrder = kotlinx.coroutines.flow.MutableStateFlow<SellerOrder?>(null)
    val state: StateFlow<SellerState> = combine(repository.listings, repository.orders, repository.profile, selectedListing, selectedOrder) { listings, orders, profile, listing, order -> SellerState(listings, orders, profile, listing, order) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SellerState())
    fun selectListing(value: SellerListing?) { selectedListing.value = value }
    fun selectOrder(value: SellerOrder?) { selectedOrder.value = value }
    fun saveListing(value: SellerListing) { repository.saveListing(value); selectedListing.value = value }
    fun toggleListing(id: String, published: Boolean) { repository.toggleListing(id, published) }
    fun transitionOrder(id: String, status: OrderStatus) { repository.transitionOrder(id, status); selectedOrder.value = repository.orders.value.firstOrNull { it.id == id } }
    fun saveProfile(value: SellerProfile) { repository.saveProfile(value) }
}
