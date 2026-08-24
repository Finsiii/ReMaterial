package com.rematerial.app.feature.marketplace.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.MarketplaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class MarketplaceState(
    val products: List<MarketplaceProduct> = emptyList(),
    val cart: List<com.rematerial.app.feature.marketplace.domain.CartLine> = emptyList(),
    val orders: List<MarketplaceOrder> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val selectedProduct: MarketplaceProduct? = null,
    val lastOrder: MarketplaceOrder? = null,
    val sellerSwitchPrompt: Boolean = false,
)

@HiltViewModel
class MarketplaceViewModel @Inject constructor(private val repository: MarketplaceRepository) : ViewModel() {
    private val query = kotlinx.coroutines.flow.MutableStateFlow("")
    private val category = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val selected = kotlinx.coroutines.flow.MutableStateFlow<MarketplaceProduct?>(null)
    private val prompt = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val lastOrder = kotlinx.coroutines.flow.MutableStateFlow<MarketplaceOrder?>(null)
    val state: StateFlow<MarketplaceState> = combine(listOf(repository.products, repository.cart, repository.orders, query, category, selected, prompt, lastOrder)) { values ->
        @Suppress("UNCHECKED_CAST")
        val products = values[0] as List<MarketplaceProduct>
        @Suppress("UNCHECKED_CAST")
        val cart = values[1] as List<com.rematerial.app.feature.marketplace.domain.CartLine>
        @Suppress("UNCHECKED_CAST")
        val orders = values[2] as List<MarketplaceOrder>
        val q = values[3] as String
        val cat = values[4] as String?
        val selectedProduct = values[5] as MarketplaceProduct?
        val switchPrompt = values[6] as Boolean
        val placed = values[7] as MarketplaceOrder?
        MarketplaceState(products.filter { (cat == null || it.category == cat) && it.title.contains(q, true) }, cart, orders, q, cat, selectedProduct, placed, switchPrompt)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketplaceState())

    fun search(value: String) { query.value = value }
    fun category(value: String?) { category.value = value }
    fun open(product: MarketplaceProduct) { selected.value = product }
    fun closeProduct() { selected.value = null }
    fun add(product: MarketplaceProduct, quantity: Int = 1) { if (!repository.addToCart(product, quantity)) prompt.value = true }
    fun confirmSellerSwitch(product: MarketplaceProduct) { prompt.value = false; repository.addToCart(product, 1, replaceSellerCart = true) }
    fun dismissPrompt() { prompt.value = false }
    fun increment(productId: String, current: Int) { repository.updateQuantity(productId, current + 1) }
    fun decrement(productId: String, current: Int) { repository.updateQuantity(productId, current - 1) }
    fun remove(productId: String) { repository.removeFromCart(productId) }
    fun placeOrder(checkout: CheckoutDraft) { lastOrder.value = repository.placeOrder(checkout) }
    fun clearLastOrder() { lastOrder.value = null }
}
