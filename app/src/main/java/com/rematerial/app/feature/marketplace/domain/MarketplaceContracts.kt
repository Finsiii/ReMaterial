package com.rematerial.app.feature.marketplace.domain

import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.ListingState
import kotlinx.coroutines.flow.StateFlow

data class MarketplaceSeller(
    val id: String,
    val name: String,
    val location: String,
    val story: String,
)

data class MarketplaceProduct(
    val id: String,
    val title: String,
    val materialOrigin: String,
    val description: String,
    val price: Int,
    val stock: Int,
    val location: String,
    val fulfillment: String,
    val imageRes: Int? = null,
    val imageUri: String? = null,
    val seller: MarketplaceSeller,
    val category: String,
    val featured: Boolean = false,
    val listingState: ListingState = ListingState.PUBLISHED,
)

data class CartLine(val product: MarketplaceProduct, val quantity: Int)

data class CheckoutDraft(
    val address: String = "Jl. Riau No. 18, Bandung",
    val delivery: String = "Reguler · 2–4 hari",
    val payment: String = "Transfer bank demo",
)

data class BuyerContext(val name: String = "", val whatsapp: String = "")

enum class OrderStatus(val label: String) {
    PLACED("Pesanan dibuat"), CONFIRMED("Dikonfirmasi"), PROCESSING("Sedang diproses"),
    READY_TO_SHIP("Siap dikirim"), SHIPPED("Dikirim"), DELIVERED("Selesai"), CANCELLED("Dibatalkan"),
}

data class MarketplaceOrder(
    val id: String,
    val lines: List<CartLine>,
    val total: Int,
    val address: String,
    val delivery: String,
    val payment: String,
    val status: OrderStatus,
    val createdLabel: String,
    val buyer: BuyerContext = BuyerContext(),
    val sellerId: String = lines.firstOrNull()?.product?.seller?.id.orEmpty(),
    val buyerAccountId: String = "demo-user",
)

interface MarketplaceRepository {
    val products: StateFlow<List<MarketplaceProduct>>
    val cart: StateFlow<List<CartLine>>
    val orders: StateFlow<List<MarketplaceOrder>>
    suspend fun addToCart(productId: String, quantity: Int = 1, replaceSellerCart: Boolean = false): CommerceResult<Unit>
    suspend fun updateQuantity(productId: String, quantity: Int): CommerceResult<Unit>
    suspend fun removeFromCart(productId: String): CommerceResult<Unit>
    suspend fun placeOrder(checkout: CheckoutDraft, buyer: BuyerContext): CommerceResult<MarketplaceOrder>
    suspend fun cancelOrder(id: String): CommerceResult<MarketplaceOrder>
    suspend fun confirmReceipt(id: String): CommerceResult<MarketplaceOrder>
    fun order(id: String): MarketplaceOrder?
}
