package com.rematerial.app.feature.marketplace

import com.rematerial.app.core.commerce.CommerceError
import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.DemoCommerceStore
import com.rematerial.app.feature.marketplace.data.MockMarketplaceRepository
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.data.MockSellerRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommerceOrderTransitionTest {
    @Test
    fun checkoutAppearsForOwningSellerAndSellerStatusSyncsBack() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store)
        val seller = MockSellerRepository(store)
        val product = market.products.value.first { it.seller.id == "seller-nusa" }
        market.addToCart(product.id)
        val placed = market.placeOrder(
            CheckoutDraft("Jl. Sinkron 10", "Reguler", "Transfer"),
            BuyerContext("Dewi", "081200000001"),
        ) as CommerceResult.Success

        assertTrue(seller.orders.value.any { it.id == placed.value.id && it.buyerName == "Dewi" })
        seller.transitionOrder(placed.value.id, OrderStatus.CONFIRMED)
        assertEquals(OrderStatus.CONFIRMED, market.order(placed.value.id)?.status)
    }

    @Test
    fun userCanCancelOnlyPlacedOrder() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store)
        val seller = MockSellerRepository(store)
        val product = market.products.value.first()
        market.addToCart(product.id)
        val order = (market.placeOrder(CheckoutDraft("Jl. Uji", "Reguler", "Transfer"), BuyerContext("Uji", "0812")) as CommerceResult.Success).value
        seller.transitionOrder(order.id, OrderStatus.CONFIRMED)

        val result = market.cancelOrder(order.id)

        assertEquals(CommerceError.InvalidTransition, (result as CommerceResult.Failure).error)
    }

    @Test
    fun sellerCannotMarkDeliveredAndBuyerCanConfirmReceiptAfterShipping() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store)
        val seller = MockSellerRepository(store)
        val product = market.products.value.first()
        market.addToCart(product.id)
        val order = (market.placeOrder(CheckoutDraft("Jl. Uji", "Reguler", "Transfer"), BuyerContext("Uji", "0812")) as CommerceResult.Success).value
        listOf(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.READY_TO_SHIP, OrderStatus.SHIPPED).forEach {
            assertTrue(seller.transitionOrder(order.id, it) is CommerceResult.Success)
        }

        val sellerResult = seller.transitionOrder(order.id, OrderStatus.DELIVERED)
        val buyerResult = market.confirmReceipt(order.id)

        assertEquals(CommerceError.InvalidTransition, (sellerResult as CommerceResult.Failure).error)
        assertTrue(buyerResult is CommerceResult.Success)
        assertEquals(OrderStatus.DELIVERED, market.order(order.id)?.status)
    }
}
