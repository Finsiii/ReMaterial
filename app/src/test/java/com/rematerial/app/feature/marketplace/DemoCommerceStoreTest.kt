package com.rematerial.app.feature.marketplace

import com.rematerial.app.core.commerce.CommerceError
import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.ListingState
import com.rematerial.app.feature.marketplace.data.MockMarketplaceRepository
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.seller.data.MockSellerRepository
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.core.commerce.DemoCommerceStore
import com.rematerial.app.core.model.AccountId
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.VerificationStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCommerceStoreTest {
    private fun userSession(id: String, name: String = id) = InMemorySessionStore(
        Session(AccountId(id), "$id@rematerial.test", Role.USER, name),
    )

    @Test
    fun sellerListingMutationIsVisibleInMarketplace() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store)
        val seller = MockSellerRepository(store)
        val listing = SellerListing(
            id = "p-new",
            sellerId = "seller-nusa",
            title = "Vas Aluminium",
            materialOrigin = "Sisa lembar aluminium",
            description = "Dibentuk ulang secara lokal.",
            price = 210_000,
            stock = 3,
            fulfillment = "Dikirim dalam 2 hari",
            location = "Bandung",
            state = ListingState.PUBLISHED,
        )

        assertTrue(seller.saveListing(listing) is CommerceResult.Success)

        assertTrue(market.products.value.any { it.id == listing.id && it.stock == 3 })
    }

    @Test
    fun zeroStockProductCannotEnterCart() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store)
        val seller = MockSellerRepository(store)
        val existing = seller.listings.value.first()
        seller.saveListing(existing.copy(stock = 0, state = ListingState.SOLD_OUT))

        val result = market.addToCart(existing.id)

        assertEquals(CommerceError.OutOfStock, (result as CommerceResult.Failure).error)
        assertTrue(market.cart.value.isEmpty())
    }

    @Test
    fun concurrentCheckoutDeductsStockOnlyOnce() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store)
        val product = market.products.value.first()
        market.addToCart(product.id, product.stock)
        val checkout = CheckoutDraft(address = "Jl. Uji 1", delivery = "Reguler", payment = "Transfer")
        val buyer = BuyerContext("Pembeli uji", "081234567890")

        val results = listOf(
            async { market.placeOrder(checkout, buyer) },
            async { market.placeOrder(checkout, buyer) },
        ).awaitAll()

        assertEquals(1, results.count { it is CommerceResult.Success })
        assertEquals(0, market.products.value.first { it.id == product.id }.stock)
    }

    @Test
    fun unverifiedSellerCannotPublish() = runTest {
        val store = DemoCommerceStore()
        val sessions = InMemorySessionStore(
            Session(AccountId("seller-pending"), "pending@rematerial.test", Role.SELLER, "Studio Pending", verificationStatus = VerificationStatus.PENDING),
        )
        val seller = MockSellerRepository(store, sessions)
        val listing = SellerListing(
            id = "pending-listing", title = "Draft", materialOrigin = "Sisa", description = "Uji",
            price = 10_000, stock = 1, fulfillment = "Dua hari", location = "Bandung",
            state = ListingState.DRAFT,
        )
        assertTrue(seller.saveListing(listing) is CommerceResult.Success)

        val result = seller.setListingState(listing.id, ListingState.PUBLISHED)

        assertEquals(CommerceError.SellerNotVerified, (result as CommerceResult.Failure).error)
    }

    @Test
    fun cartAndOrdersAreIsolatedByBuyerAccount() = runTest {
        val store = DemoCommerceStore()
        val buyerA = MockMarketplaceRepository(store, userSession("buyer-a", "A"))
        val buyerB = MockMarketplaceRepository(store, userSession("buyer-b", "B"))
        val product = buyerA.products.value.first()

        buyerA.addToCart(product.id)
        assertEquals(1, buyerA.cart.value.size)
        assertTrue(buyerB.cart.value.isEmpty())

        val placed = buyerA.placeOrder(
            CheckoutDraft("Alamat A", "Reguler", "Transfer"),
            BuyerContext("A", "081200000001"),
        )
        assertTrue(placed is CommerceResult.Success)
        assertEquals(1, buyerA.orders.value.count { it.buyerAccountId == "buyer-a" })
        assertTrue(buyerB.orders.value.none { it.buyerAccountId == "buyer-a" })
    }

    @Test
    fun cancellingPlacedOrderRestoresReservedStock() = runTest {
        val store = DemoCommerceStore()
        val market = MockMarketplaceRepository(store, userSession("buyer-stock"))
        val product = market.products.value.first()
        val stockBefore = product.stock
        market.addToCart(product.id, 2)
        val order = (market.placeOrder(
            CheckoutDraft("Alamat", "Reguler", "Transfer"),
            BuyerContext("Buyer", "081200000002"),
        ) as CommerceResult.Success).value
        assertEquals(stockBefore - 2, market.products.value.first { it.id == product.id }.stock)

        assertTrue(market.cancelOrder(order.id) is CommerceResult.Success)

        assertEquals(stockBefore, market.products.value.first { it.id == product.id }.stock)
    }
}
