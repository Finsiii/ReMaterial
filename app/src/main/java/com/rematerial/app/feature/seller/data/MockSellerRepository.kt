package com.rematerial.app.feature.seller.data

import com.rematerial.app.R
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.feature.seller.domain.SellerOrder
import com.rematerial.app.feature.seller.domain.SellerProfile
import com.rematerial.app.feature.seller.domain.SellerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class MockSellerRepository @Inject constructor() : SellerRepository {
    private val _listings = MutableStateFlow(listOf(
        SellerListing("p-copper", "Lampu Tembaga Senja", "Tembaga reclaimed dari kabel industri", "Kap lampu hangat dengan karakter permukaan yang tidak pernah sama.", 485000, 7, "Dibuat dan dikirim dalam 3–5 hari", "Bandung", imageRes = R.drawable.material_metal),
        SellerListing("p-oak", "Tray Kayu Sisa", "Papan kayu jati dari bongkaran rumah", "Tray sederhana dengan tepi lembut.", 275000, 12, "Siap dikirim besok", "Bandung", imageRes = R.drawable.material_wood),
        SellerListing("p-metal-book", "Bookend Arang", "Aluminium pasca produksi", "Sepasang bookend bertekstur.", 320000, 4, "Dibuat dalam 4 hari", "Bandung", imageRes = R.drawable.material_metal, published = false),
    ))
    private val _orders = MutableStateFlow(listOf(
        SellerOrder("RM-2408-018", "Tray Kayu Sisa", "Dika", 1, 275000, "Jl. Riau No. 18, Bandung", OrderStatus.PLACED),
        SellerOrder("RM-2408-011", "Lampu Tembaga Senja", "Nadia", 1, 485000, "Jl. Ciumbuleuit No. 7, Bandung", OrderStatus.PROCESSING),
        SellerOrder("RM-2407-092", "Bookend Arang", "Raka", 2, 640000, "Jl. Setiabudi No. 22, Bandung", OrderStatus.SHIPPED),
    ))
    private val _profile = MutableStateFlow(SellerProfile())
    override val listings: StateFlow<List<SellerListing>> = _listings.asStateFlow()
    override val orders: StateFlow<List<SellerOrder>> = _orders.asStateFlow()
    override val profile: StateFlow<SellerProfile> = _profile.asStateFlow()
    override fun saveListing(listing: SellerListing): SellerListing { _listings.value = if (_listings.value.any { it.id == listing.id }) _listings.value.map { if (it.id == listing.id) listing else it } else _listings.value + listing; return listing }
    override fun toggleListing(id: String, published: Boolean) { _listings.value = _listings.value.map { if (it.id == id) it.copy(published = published) else it } }
    override fun transitionOrder(id: String, status: OrderStatus) { _orders.value = _orders.value.map { if (it.id == id) it.copy(status = status) else it } }
    override fun saveProfile(profile: SellerProfile) { _profile.value = profile }
}
