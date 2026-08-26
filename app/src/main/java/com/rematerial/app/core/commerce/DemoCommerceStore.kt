package com.rematerial.app.core.commerce

import com.rematerial.app.R
import com.rematerial.app.feature.marketplace.domain.BuyerContext
import com.rematerial.app.feature.marketplace.domain.CartLine
import com.rematerial.app.feature.marketplace.domain.CheckoutDraft
import com.rematerial.app.feature.marketplace.domain.MarketplaceOrder
import com.rematerial.app.feature.marketplace.domain.MarketplaceProduct
import com.rematerial.app.feature.marketplace.domain.MarketplaceSeller
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.feature.seller.domain.SellerOrder
import com.rematerial.app.feature.seller.domain.SellerProfile
import com.rematerial.app.feature.seller.domain.VerificationState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DemoCommerceStore @Inject constructor() {
    private val mutex = Mutex()
    private var nextOrderNumber = 19
    private val nusa = MarketplaceSeller("seller-nusa", "Nusa Forma", "Bandung", "Studio kecil yang mengolah sisa material menjadi benda sehari-hari yang tahan lama.")
    private val serat = MarketplaceSeller("seller-serat", "Serat Selatan", "Yogyakarta", "Kolektif tekstil yang menghidupkan kembali potongan kain lokal.")
    private val sellers = mapOf(nusa.id to nusa, serat.id to serat)
    private val _profiles = MutableStateFlow(
        mapOf(nusa.id to SellerProfile(nusa.id, "Alya", nusa.name, nusa.location, "3273010101010001", "seeded-private://seller-nusa/ktp", "seeded-private://seller-nusa/selfie", VerificationState.VERIFIED)),
    )
    private val _listings = MutableStateFlow(seedListings())
    private val _products = MutableStateFlow(toProducts(_listings.value))
    private val _carts = MutableStateFlow<Map<String, List<CartLine>>>(emptyMap())
    private val _orders = MutableStateFlow(seedOrders())
    private val _sellerOrders = MutableStateFlow(toSellerOrders(_orders.value, nusa.id))

    val profiles: StateFlow<Map<String, SellerProfile>> = _profiles.asStateFlow()
    val listings: StateFlow<List<SellerListing>> = _listings.asStateFlow()
    val products: StateFlow<List<MarketplaceProduct>> = _products.asStateFlow()
    val carts: StateFlow<Map<String, List<CartLine>>> = _carts.asStateFlow()
    val orders: StateFlow<List<MarketplaceOrder>> = _orders.asStateFlow()
    val sellerOrders: StateFlow<List<SellerOrder>> = _sellerOrders.asStateFlow()

    suspend fun addToCart(buyerId: String, productId: String, quantity: Int, replaceSellerCart: Boolean): CommerceResult<Unit> = mutex.withLock {
        if (buyerId.isBlank()) return@withLock CommerceResult.Failure(CommerceError.InvalidInput(listOf("buyer")))
        if (quantity <= 0) return@withLock invalid("quantity")
        val product = _products.value.firstOrNull { it.id == productId }
            ?: return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
        if (product.stock <= 0) return@withLock CommerceResult.Failure(CommerceError.OutOfStock)
        if (product.listingState != ListingState.PUBLISHED) return@withLock CommerceResult.Failure(CommerceError.ListingUnavailable)
        val cart = cartFor(buyerId)
        val differentSeller = cart.firstOrNull()?.product?.seller?.id?.let { it != product.seller.id } == true
        if (differentSeller && !replaceSellerCart) return@withLock CommerceResult.Failure(CommerceError.DifferentSeller)
        val base = if (differentSeller) emptyList() else cart
        val existing = base.firstOrNull { it.product.id == product.id }
        val requested = (existing?.quantity ?: 0) + quantity
        if (requested > product.stock) return@withLock CommerceResult.Failure(CommerceError.OutOfStock)
        updateCart(buyerId, if (existing == null) base + CartLine(product, quantity) else base.map {
            if (it.product.id == product.id) it.copy(product = product, quantity = requested) else it
        })
        CommerceResult.Success(Unit)
    }

    suspend fun updateQuantity(buyerId: String, productId: String, quantity: Int): CommerceResult<Unit> = mutex.withLock {
        if (quantity <= 0) return@withLock invalid("quantity")
        val product = _products.value.firstOrNull { it.id == productId }
            ?: return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
        if (product.listingState != ListingState.PUBLISHED) return@withLock CommerceResult.Failure(CommerceError.ListingUnavailable)
        if (quantity > product.stock) return@withLock CommerceResult.Failure(CommerceError.OutOfStock)
        val cart = cartFor(buyerId)
        if (cart.none { it.product.id == productId }) return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
        updateCart(buyerId, cart.map { if (it.product.id == productId) it.copy(product = product, quantity = quantity) else it })
        CommerceResult.Success(Unit)
    }

    suspend fun removeFromCart(buyerId: String, productId: String): CommerceResult<Unit> = mutex.withLock {
        val cart = cartFor(buyerId)
        if (cart.none { it.product.id == productId }) return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
        updateCart(buyerId, cart.filterNot { it.product.id == productId })
        CommerceResult.Success(Unit)
    }

    suspend fun placeOrder(buyerId: String, checkout: CheckoutDraft, buyer: BuyerContext): CommerceResult<MarketplaceOrder> = mutex.withLock {
        val invalidFields = buildList {
            if (checkout.address.isBlank()) add("address")
            if (checkout.delivery.isBlank()) add("delivery")
            if (checkout.payment.isBlank()) add("payment")
            if (buyer.name.isBlank()) add("buyerName")
            if (buyer.whatsapp.isBlank()) add("buyerWhatsapp")
        }
        if (invalidFields.isNotEmpty()) return@withLock CommerceResult.Failure(CommerceError.InvalidInput(invalidFields))
        val cart = cartFor(buyerId)
        if (cart.isEmpty()) return@withLock CommerceResult.Failure(CommerceError.EmptyCart)
        val refreshed = mutableListOf<CartLine>()
        for (line in cart) {
            val product = _products.value.firstOrNull { it.id == line.product.id }
                ?: return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
            if (product.listingState != ListingState.PUBLISHED) return@withLock CommerceResult.Failure(CommerceError.ListingUnavailable)
            if (line.quantity <= 0 || line.quantity > product.stock) return@withLock CommerceResult.Failure(CommerceError.OutOfStock)
            refreshed += CartLine(product, line.quantity)
        }
        if (refreshed.map { it.product.seller.id }.distinct().size != 1) return@withLock CommerceResult.Failure(CommerceError.DifferentSeller)
        val order = MarketplaceOrder(
            id = "RM-2408-${nextOrderNumber++.toString().padStart(3, '0')}", lines = refreshed,
            total = refreshed.sumOf { it.product.price * it.quantity }, address = checkout.address.trim(),
            delivery = checkout.delivery, payment = checkout.payment, status = OrderStatus.PLACED,
            createdLabel = "Hari ini", buyer = buyer, sellerId = refreshed.first().product.seller.id,
            buyerAccountId = buyerId,
        )
        val purchased = refreshed.associate { it.product.id to it.quantity }
        _listings.value = _listings.value.map { listing ->
            val amount = purchased[listing.id] ?: return@map listing
            val remaining = listing.stock - amount
            listing.copy(stock = remaining, state = if (remaining == 0) ListingState.SOLD_OUT else listing.state)
        }
        refreshProductsAndCart()
        _orders.value = listOf(order) + _orders.value
        _sellerOrders.value = toSellerOrders(_orders.value, nusa.id)
        updateCart(buyerId, emptyList())
        CommerceResult.Success(order)
    }

    suspend fun cancelOrder(buyerId: String, id: String): CommerceResult<MarketplaceOrder> = mutex.withLock { transition(id, OrderStatus.CANCELLED, Actor.BUYER, buyerId) }
    suspend fun confirmReceipt(buyerId: String, id: String): CommerceResult<MarketplaceOrder> = mutex.withLock { transition(id, OrderStatus.DELIVERED, Actor.BUYER, buyerId) }
    suspend fun transitionSellerOrder(sellerId: String, id: String, status: OrderStatus): CommerceResult<MarketplaceOrder> = mutex.withLock { transition(id, status, Actor.SELLER, sellerId) }

    suspend fun saveListing(actorSellerId: String, verified: Boolean, listing: SellerListing): CommerceResult<SellerListing> = mutex.withLock {
        val errors = listingErrors(listing)
        if (errors.isNotEmpty()) return@withLock CommerceResult.Failure(CommerceError.InvalidInput(errors))
        val existing = _listings.value.firstOrNull { it.id == listing.id }
        if (existing != null && existing.sellerId != actorSellerId) return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
        if (listing.state == ListingState.PUBLISHED && !verified) return@withLock CommerceResult.Failure(CommerceError.SellerNotVerified)
        val normalized = listing.copy(
            sellerId = actorSellerId,
            state = if (listing.stock == 0 && listing.state == ListingState.PUBLISHED) ListingState.SOLD_OUT else listing.state,
        )
        _listings.value = if (_listings.value.any { it.id == normalized.id }) _listings.value.map { if (it.id == normalized.id) normalized else it } else _listings.value + normalized
        refreshProductsAndCart()
        CommerceResult.Success(normalized)
    }

    suspend fun setListingState(actorSellerId: String, verified: Boolean, id: String, state: ListingState): CommerceResult<SellerListing> = mutex.withLock {
        val current = _listings.value.firstOrNull { it.id == id && it.sellerId == actorSellerId }
            ?: return@withLock CommerceResult.Failure(CommerceError.ProductNotFound)
        if (state == ListingState.PUBLISHED && !verified) return@withLock CommerceResult.Failure(CommerceError.SellerNotVerified)
        if (state == ListingState.PUBLISHED && current.stock == 0) return@withLock CommerceResult.Failure(CommerceError.OutOfStock)
        val updated = current.copy(state = state)
        _listings.value = _listings.value.map { if (it.id == id) updated else it }
        refreshProductsAndCart()
        CommerceResult.Success(updated)
    }

    suspend fun saveProfile(actorSellerId: String, profile: SellerProfile): CommerceResult<SellerProfile> = mutex.withLock {
        val errors = buildList {
            if (profile.storeName.isBlank()) add("storeName")
            if (profile.location.isBlank()) add("location")
            if (!profile.nik.matches(Regex("\\d{16}"))) add("nik")
            if (profile.ktpUri.isNullOrBlank()) add("ktp")
            if (profile.selfieUri.isNullOrBlank()) add("selfie")
        }
        if (errors.isNotEmpty()) return@withLock CommerceResult.Failure(CommerceError.InvalidInput(errors))
        val normalized = profile.copy(sellerId = actorSellerId)
        _profiles.value = _profiles.value + (actorSellerId to normalized)
        CommerceResult.Success(normalized)
    }

    private fun transition(id: String, target: OrderStatus, actor: Actor, actorId: String): CommerceResult<MarketplaceOrder> {
        val current = _orders.value.firstOrNull { it.id == id } ?: return CommerceResult.Failure(CommerceError.OrderNotFound)
        val ownsOrder = when (actor) {
            Actor.BUYER -> current.buyerAccountId == actorId
            Actor.SELLER -> current.sellerId == actorId
        }
        if (!ownsOrder) return CommerceResult.Failure(CommerceError.OrderNotFound)
        val valid = when (actor) {
            Actor.BUYER -> (current.status == OrderStatus.PLACED && target == OrderStatus.CANCELLED) || (current.status == OrderStatus.SHIPPED && target == OrderStatus.DELIVERED)
            Actor.SELLER -> target != OrderStatus.DELIVERED && target != OrderStatus.CANCELLED && target == current.status.nextSeller()
        }
        if (!valid) return CommerceResult.Failure(CommerceError.InvalidTransition)
        val updated = current.copy(status = target)
        if (actor == Actor.BUYER && target == OrderStatus.CANCELLED) restoreStock(current)
        _orders.value = _orders.value.map { if (it.id == id) updated else it }
        _sellerOrders.value = toSellerOrders(_orders.value, nusa.id)
        return CommerceResult.Success(updated)
    }

    private fun refreshProductsAndCart() {
        _products.value = toProducts(_listings.value)
        _carts.value = _carts.value.mapValues { (_, cart) ->
            cart.mapNotNull { line ->
                _products.value.firstOrNull { it.id == line.product.id && it.listingState == ListingState.PUBLISHED && it.stock > 0 }
                    ?.let { line.copy(product = it, quantity = line.quantity.coerceAtMost(it.stock)) }?.takeIf { it.quantity > 0 }
            }
        }
    }

    fun cartFor(buyerId: String): List<CartLine> = _carts.value[buyerId].orEmpty()
    fun ordersFor(buyerId: String): List<MarketplaceOrder> = _orders.value.filter { it.buyerAccountId == buyerId }

    private fun updateCart(buyerId: String, cart: List<CartLine>) {
        _carts.value = if (cart.isEmpty()) _carts.value - buyerId else _carts.value + (buyerId to cart)
    }

    private fun restoreStock(order: MarketplaceOrder) {
        val restored = order.lines.associate { it.product.id to it.quantity }
        _listings.value = _listings.value.map { listing ->
            val quantity = restored[listing.id] ?: return@map listing
            val state = if (listing.state == ListingState.SOLD_OUT) ListingState.PUBLISHED else listing.state
            listing.copy(stock = listing.stock + quantity, state = state)
        }
        refreshProductsAndCart()
    }

    private fun toProducts(listings: List<SellerListing>) = listings.filter { it.state in setOf(ListingState.PUBLISHED, ListingState.SOLD_OUT) }.map {
        MarketplaceProduct(it.id, it.title, it.materialOrigin, it.description, it.price, it.stock, it.location, it.fulfillment,
            it.imageRes, it.imageUri, sellers[it.sellerId] ?: nusa, it.category, it.featured, it.state)
    }

    fun sellerOrdersFor(sellerId: String) = toSellerOrders(_orders.value, sellerId)

    private fun toSellerOrders(orders: List<MarketplaceOrder>, sellerId: String) = orders.filter { it.sellerId == sellerId }.map {
        SellerOrder(it.id, it.lines.joinToString { line -> line.product.title }, it.buyer.name, it.buyer.whatsapp,
            it.lines.sumOf { line -> line.quantity }, it.total, it.address, it.status)
    }

    private fun listingErrors(listing: SellerListing) = buildList {
        if (listing.title.isBlank()) add("title")
        if (listing.materialOrigin.isBlank()) add("materialOrigin")
        if (listing.description.isBlank()) add("description")
        if (listing.price <= 0) add("price")
        if (listing.stock < 0) add("stock")
        if (listing.fulfillment.isBlank()) add("fulfillment")
        if (listing.location.isBlank()) add("location")
    }

    private fun seedListings() = listOf(
        SellerListing("p-copper", nusa.id, "Lampu Tembaga Senja", "Tembaga reclaimed dari kabel industri", "Kap lampu hangat dengan karakter permukaan yang tidak pernah sama.", 485000, 7, "Dibuat dan dikirim dalam 3–5 hari", "Bandung", imageRes = R.drawable.product_copper_lamp, category = "Logam", featured = true, state = ListingState.PUBLISHED),
        SellerListing("p-oak", nusa.id, "Tray Kayu Sisa", "Papan kayu jati dari bongkaran rumah", "Tray sederhana dengan tepi lembut, selesai dengan minyak natural.", 275000, 12, "Siap dikirim besok", "Bandung", imageRes = R.drawable.product_wood_tray, category = "Kayu", featured = true, state = ListingState.PUBLISHED),
        SellerListing("p-metal-book", nusa.id, "Bookend Arang", "Aluminium pasca produksi", "Sepasang bookend bertekstur yang menjaga rak tetap tenang.", 320000, 4, "Dibuat dalam 4 hari", "Bandung", imageRes = R.drawable.material_metal, category = "Logam", state = ListingState.PAUSED),
        SellerListing("p-weave", serat.id, "Tas Anyam Senyap", "Potongan tekstil deadstock", "Tas ringan yang disusun dari potongan kain terselamatkan.", 189000, 18, "Dikirim 1–2 hari", "Yogyakarta", imageRes = R.drawable.product_woven_bag, category = "Tekstil", featured = true, state = ListingState.PUBLISHED),
        SellerListing("p-textile-pouch", serat.id, "Pouch Sisa Tenun", "Sisa kain tenun produksi", "Pouch kecil berlapis kanvas untuk benda esensial.", 145000, 20, "Siap dikirim besok", "Yogyakarta", imageRes = R.drawable.product_textile_pouch, category = "Tekstil", state = ListingState.PUBLISHED),
    )

    private fun seedOrders(): List<MarketplaceOrder> {
        val product = toProducts(_listings.value).first { it.id == "p-oak" }
        return listOf(MarketplaceOrder("RM-2408-018", listOf(CartLine(product, 1)), product.price, "Jl. Riau No. 18, Bandung", "Reguler · 2–4 hari", "Transfer bank demo", OrderStatus.DELIVERED, "12 Agustus 2026", BuyerContext("Dika", "081234567890"), nusa.id, "demo-user"))
    }

    private fun invalid(field: String): CommerceResult.Failure = CommerceResult.Failure(CommerceError.InvalidInput(listOf(field)))
    private fun OrderStatus.nextSeller() = when (this) {
        OrderStatus.PLACED -> OrderStatus.CONFIRMED
        OrderStatus.CONFIRMED -> OrderStatus.PROCESSING
        OrderStatus.PROCESSING -> OrderStatus.READY_TO_SHIP
        OrderStatus.READY_TO_SHIP -> OrderStatus.SHIPPED
        else -> null
    }
    private enum class Actor { BUYER, SELLER }
}
