package com.rematerial.app.feature.marketplace.data

import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.DemoCommerceStore
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.marketplace.domain.CartLine
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.MarketplaceRepository
import com.rematerial.app.core.commerce.CommerceError
import com.rematerial.app.core.model.AccountId
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Singleton
class MockMarketplaceRepository @Inject constructor(
    private val store: DemoCommerceStore,
    private val sessions: SessionStore,
) : MarketplaceRepository {
    constructor(store: DemoCommerceStore) : this(
        store,
        InMemorySessionStore(Session(AccountId("demo-user"), "user@rematerial.demo", Role.USER, "Dika")),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    override val products: StateFlow<List<MarketplaceProduct>> = store.products
    override val cart: StateFlow<List<CartLine>> = combine(sessions.session, store.carts) { session, carts ->
        session?.takeIf { it.role == Role.USER }?.accountId?.value?.let { carts[it].orEmpty() }.orEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, buyerId()?.let(store::cartFor).orEmpty())
    override val orders: StateFlow<List<MarketplaceOrder>> = combine(sessions.session, store.orders) { session, orders ->
        val owner = session?.takeIf { it.role == Role.USER }?.accountId?.value
        if (owner == null) emptyList() else orders.filter { it.buyerAccountId == owner }
    }.stateIn(scope, SharingStarted.Eagerly, buyerId()?.let(store::ordersFor).orEmpty())
    override suspend fun addToCart(productId: String, quantity: Int, replaceSellerCart: Boolean) = withBuyer {
        store.addToCart(it, productId, quantity, replaceSellerCart)
    }
    override suspend fun updateQuantity(productId: String, quantity: Int) = withBuyer { store.updateQuantity(it, productId, quantity) }
    override suspend fun removeFromCart(productId: String) = withBuyer { store.removeFromCart(it, productId) }
    override suspend fun placeOrder(checkout: CheckoutDraft, buyer: BuyerContext) = withBuyer { store.placeOrder(it, checkout, buyer) }
    override suspend fun cancelOrder(id: String): CommerceResult<MarketplaceOrder> = withBuyer { store.cancelOrder(it, id) }
    override suspend fun confirmReceipt(id: String): CommerceResult<MarketplaceOrder> = withBuyer { store.confirmReceipt(it, id) }
    override fun order(id: String) = orders.value.firstOrNull { it.id == id }

    private fun buyerId(): String? = sessions.current()?.takeIf { it.role == Role.USER }?.accountId?.value
    private suspend fun <T> withBuyer(block: suspend (String) -> CommerceResult<T>): CommerceResult<T> =
        buyerId()?.let { block(it) } ?: CommerceResult.Failure(CommerceError.InvalidInput(listOf("session")))
}
