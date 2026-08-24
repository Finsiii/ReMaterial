package com.rematerial.app.feature.marketplace.data

import com.rematerial.app.R
import com.rematerial.app.feature.marketplace.domain.CartLine
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.MarketplaceRepository
import com.rematerial.app.feature.marketplace.domain.MarketplaceSeller
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class MockMarketplaceRepository @Inject constructor() : MarketplaceRepository {
    private val nusa = MarketplaceSeller("seller-nusa", "Nusa Forma", "Bandung", "Studio kecil yang mengolah sisa material menjadi benda sehari-hari yang tahan lama.")
    private val serat = MarketplaceSeller("seller-serat", "Serat Selatan", "Yogyakarta", "Kolektif tekstil yang menghidupkan kembali potongan kain lokal.")
    private val _products = MutableStateFlow(
        listOf(
            MarketplaceProduct("p-copper", "Lampu Tembaga Senja", "Tembaga reclaimed dari kabel industri", "Kap lampu hangat dengan karakter permukaan yang tidak pernah sama.", 485000, 7, "Bandung", "Dibuat dan dikirim dalam 3–5 hari", R.drawable.material_metal, nusa, "Logam", true),
            MarketplaceProduct("p-oak", "Tray Kayu Sisa", "Papan kayu jati dari bongkaran rumah", "Tray sederhana dengan tepi lembut, selesai dengan minyak natural.", 275000, 12, "Bandung", "Siap dikirim besok", R.drawable.material_wood, nusa, "Kayu", true),
            MarketplaceProduct("p-weave", "Tas Anyam Senyap", "Potongan tekstil deadstock", "Tas ringan yang disusun dari potongan kain terselamatkan.", 189000, 18, "Yogyakarta", "Dikirim 1–2 hari", R.drawable.material_textile, serat, "Tekstil", true),
            MarketplaceProduct("p-metal-book", "Bookend Arang", "Aluminium pasca produksi", "Sepasang bookend bertekstur yang menjaga rak tetap tenang.", 320000, 4, "Bandung", "Dibuat dalam 4 hari", R.drawable.material_metal, nusa, "Logam"),
            MarketplaceProduct("p-textile-pouch", "Pouch Sisa Tenun", "Sisa kain tenun produksi", "Pouch kecil berlapis kanvas untuk benda esensial.", 145000, 20, "Yogyakarta", "Siap dikirim besok", R.drawable.material_textile, serat, "Tekstil"),
        ),
    )
    private val _cart = MutableStateFlow<List<CartLine>>(emptyList())
    private val _orders = MutableStateFlow(
        listOf(
            MarketplaceOrder("RM-2408-018", listOf(CartLine(_products.value[1], 1)), 275000, "Jl. Riau No. 18, Bandung", "Reguler · 2–4 hari", "Transfer bank demo", OrderStatus.DELIVERED, "12 Agustus 2026"),
        ),
    )
    override val products: StateFlow<List<MarketplaceProduct>> = _products.asStateFlow()
    override val cart: StateFlow<List<CartLine>> = _cart.asStateFlow()
    override val orders: StateFlow<List<MarketplaceOrder>> = _orders.asStateFlow()

    override fun addToCart(product: MarketplaceProduct, quantity: Int, replaceSellerCart: Boolean): Boolean {
        val current = _cart.value
        val otherSeller = current.firstOrNull()?.product?.seller?.id?.let { it != product.seller.id } == true
        if (otherSeller && !replaceSellerCart) return false
        val base = if (otherSeller) emptyList() else current
        val existing = base.firstOrNull { it.product.id == product.id }
        _cart.value = if (existing == null) base + CartLine(product, quantity.coerceIn(1, product.stock)) else base.map { if (it.product.id == product.id) it.copy(quantity = (it.quantity + quantity).coerceAtMost(product.stock)) else it }
        return true
    }

    override fun updateQuantity(productId: String, quantity: Int) {
        if (quantity <= 0) removeFromCart(productId) else _cart.value = _cart.value.map { if (it.product.id == productId) it.copy(quantity = quantity.coerceAtMost(it.product.stock)) else it }
    }
    override fun removeFromCart(productId: String) { _cart.value = _cart.value.filterNot { it.product.id == productId } }
    override fun placeOrder(checkout: CheckoutDraft): MarketplaceOrder? {
        val lines = _cart.value
        if (lines.isEmpty()) return null
        val order = MarketplaceOrder("RM-${System.currentTimeMillis().toString().takeLast(6)}", lines, lines.sumOf { it.product.price * it.quantity }, checkout.address, checkout.delivery, checkout.payment, OrderStatus.PLACED, "Hari ini")
        _orders.value = listOf(order) + _orders.value
        _cart.value = emptyList()
        return order
    }
    override fun order(id: String): MarketplaceOrder? = _orders.value.firstOrNull { it.id == id }
}
