package com.rematerial.app.feature.marketplace.domain

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
    val imageRes: Int,
    val seller: MarketplaceSeller,
    val category: String,
    val featured: Boolean = false,
)

data class CartLine(val product: MarketplaceProduct, val quantity: Int)

data class CheckoutDraft(
    val address: String = "Jl. Riau No. 18, Bandung",
    val delivery: String = "Reguler · 2–4 hari",
    val payment: String = "Transfer bank demo",
)

enum class OrderStatus(val label: String) {
    PLACED("Pesanan dibuat"), CONFIRMED("Dikonfirmasi"), PROCESSING("Sedang diproses"),
    READY_TO_SHIP("Siap dikirim"), SHIPPED("Dikirim"), DELIVERED("Selesai"),
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
)

interface MarketplaceRepository {
    val products: StateFlow<List<MarketplaceProduct>>
    val cart: StateFlow<List<CartLine>>
    val orders: StateFlow<List<MarketplaceOrder>>
    fun addToCart(product: MarketplaceProduct, quantity: Int = 1, replaceSellerCart: Boolean = false): Boolean
    fun updateQuantity(productId: String, quantity: Int)
    fun removeFromCart(productId: String)
    fun placeOrder(checkout: CheckoutDraft): MarketplaceOrder?
    fun order(id: String): MarketplaceOrder?
}
