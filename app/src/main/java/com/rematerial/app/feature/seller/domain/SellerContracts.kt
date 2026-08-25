package com.rematerial.app.feature.seller.domain

import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.ListingState
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import kotlinx.coroutines.flow.StateFlow

data class SellerListing(
    val id: String,
    val sellerId: String = "seller-nusa",
    val title: String,
    val materialOrigin: String,
    val description: String,
    val price: Int,
    val stock: Int,
    val fulfillment: String,
    val location: String,
    val imageUri: String? = null,
    val imageRes: Int? = null,
    val category: String = "Lainnya",
    val featured: Boolean = false,
    val state: ListingState = ListingState.DRAFT,
)

data class SellerOrder(
    val id: String,
    val productTitle: String,
    val buyerName: String,
    val buyerWhatsapp: String,
    val quantity: Int,
    val total: Int,
    val address: String,
    val status: OrderStatus,
)

enum class VerificationState(val label: String) { NOT_SUBMITTED("Belum dikirim"), SUBMITTED("Sedang ditinjau"), NEEDS_CORRECTION("Perlu koreksi"), VERIFIED("Terverifikasi") }

data class SellerProfile(
    val sellerId: String = "seller-nusa",
    val name: String = "Alya Studio",
    val storeName: String = "Nusa Forma",
    val location: String = "Bandung",
    val nik: String = "",
    val ktpUri: String? = null,
    val selfieUri: String? = null,
    val verification: VerificationState = VerificationState.NOT_SUBMITTED,
)

interface SellerRepository {
    val listings: StateFlow<List<SellerListing>>
    val orders: StateFlow<List<SellerOrder>>
    val profile: StateFlow<SellerProfile>
    suspend fun saveListing(listing: SellerListing): CommerceResult<SellerListing>
    suspend fun setListingState(id: String, state: ListingState): CommerceResult<SellerListing>
    suspend fun transitionOrder(id: String, status: OrderStatus): CommerceResult<SellerOrder>
    suspend fun saveProfile(profile: SellerProfile): CommerceResult<SellerProfile>
}
