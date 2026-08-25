package com.rematerial.app.feature.marketplace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.commerce.CommerceError
import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.marketplace.domain.CartLine
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.MarketplaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MarketplaceState(
    val products: List<MarketplaceProduct> = emptyList(), val cart: List<CartLine> = emptyList(),
    val orders: List<MarketplaceOrder> = emptyList(), val query: String = "", val category: String? = null,
    val selectedProduct: MarketplaceProduct? = null, val lastOrder: MarketplaceOrder? = null,
    val sellerSwitchPrompt: Boolean = false, val isMutating: Boolean = false, val errorMessage: String? = null,
)

@HiltViewModel
class MarketplaceViewModel @Inject constructor(private val repository: MarketplaceRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val selected = MutableStateFlow<MarketplaceProduct?>(null)
    private val prompt = MutableStateFlow(false)
    private val lastOrder = MutableStateFlow<MarketplaceOrder?>(null)
    private val isMutating = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    val state: StateFlow<MarketplaceState> = combine(
        listOf(repository.products, repository.cart, repository.orders, query, category, selected, prompt, lastOrder, isMutating, error),
    ) { values ->
        @Suppress("UNCHECKED_CAST") val products = values[0] as List<MarketplaceProduct>
        @Suppress("UNCHECKED_CAST") val cart = values[1] as List<CartLine>
        @Suppress("UNCHECKED_CAST") val orders = values[2] as List<MarketplaceOrder>
        val q = values[3] as String
        val cat = values[4] as String?
        MarketplaceState(
            products.filter { (cat == null || it.category == cat) && it.title.contains(q, true) },
            cart,
            orders,
            q,
            cat,
            values[5] as MarketplaceProduct?,
            values[7] as MarketplaceOrder?,
            values[6] as Boolean,
            values[8] as Boolean,
            values[9] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketplaceState())

    fun search(value: String) { query.value = value }
    fun category(value: String?) { category.value = value }
    fun open(product: MarketplaceProduct) { selected.value = product }
    fun closeProduct() { selected.value = null }
    fun dismissPrompt() { prompt.value = false }
    fun clearLastOrder() { lastOrder.value = null }
    fun clearError() { error.value = null }

    fun add(product: MarketplaceProduct, quantity: Int = 1, onAdded: () -> Unit = {}) = mutate {
        when (val result = repository.addToCart(product.id, quantity)) {
            is CommerceResult.Success -> onAdded()
            is CommerceResult.Failure -> if (result.error == CommerceError.DifferentSeller) {
                selected.value = product; prompt.value = true
            } else error.value = result.error.message()
        }
    }

    fun confirmSellerSwitch(product: MarketplaceProduct) = mutate {
        prompt.value = false
        handle(repository.addToCart(product.id, replaceSellerCart = true))
    }
    fun increment(productId: String, current: Int) = mutate { handle(repository.updateQuantity(productId, current + 1)) }
    fun decrement(productId: String, current: Int) {
        if (current <= 1) remove(productId) else mutate { handle(repository.updateQuantity(productId, current - 1)) }
    }
    fun remove(productId: String) = mutate { handle(repository.removeFromCart(productId)) }
    fun placeOrder(checkout: CheckoutDraft, buyer: BuyerContext = BuyerContext(), onPlaced: () -> Unit) = mutate {
        when (val result = repository.placeOrder(checkout, buyer)) {
            is CommerceResult.Success -> { lastOrder.value = result.value; onPlaced() }
            is CommerceResult.Failure -> error.value = result.error.message()
        }
    }
    fun cancelOrder(id: String) = mutate { handle(repository.cancelOrder(id)) }
    fun confirmReceipt(id: String) = mutate { handle(repository.confirmReceipt(id)) }

    private fun mutate(block: suspend () -> Unit) {
        if (isMutating.value) return
        isMutating.value = true
        error.value = null
        viewModelScope.launch {
            try { block() } finally { isMutating.value = false }
        }
    }
    private fun <T> handle(result: CommerceResult<T>) {
        if (result is CommerceResult.Failure) error.value = result.error.message()
    }
}

private fun CommerceError.message(): String = when (this) {
    CommerceError.OutOfStock -> "Stok tidak mencukupi. Periksa jumlah lalu coba lagi."
    CommerceError.DifferentSeller -> "Keranjang hanya dapat memuat produk dari satu studio."
    CommerceError.EmptyCart -> "Keranjang masih kosong."
    CommerceError.ListingUnavailable -> "Produk sedang tidak tersedia."
    CommerceError.InvalidTransition -> "Status pesanan sudah berubah. Muat ulang lalu coba lagi."
    CommerceError.OrderNotFound, CommerceError.ProductNotFound -> "Data tidak ditemukan."
    CommerceError.SellerNotVerified -> "Penjual belum terverifikasi."
    CommerceError.MediaImportFailed -> "Foto tidak dapat disimpan."
    is CommerceError.InvalidInput -> "Lengkapi data: ${fields.joinToString()}."
}
