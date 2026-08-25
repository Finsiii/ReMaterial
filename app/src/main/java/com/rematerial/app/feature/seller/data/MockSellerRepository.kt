package com.rematerial.app.feature.seller.data

import com.rematerial.app.core.commerce.CommerceError
import com.rematerial.app.core.commerce.CommerceResult
import com.rematerial.app.core.commerce.DemoCommerceStore
import com.rematerial.app.core.commerce.ListingState
import com.rematerial.app.feature.marketplace.domain.OrderStatus
import com.rematerial.app.feature.seller.domain.SellerListing
import com.rematerial.app.feature.seller.domain.SellerOrder
import com.rematerial.app.feature.seller.domain.SellerProfile
import com.rematerial.app.feature.seller.domain.SellerRepository
import com.rematerial.app.feature.seller.domain.VerificationState
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.domain.SessionStore
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.VerificationStatus
import com.rematerial.app.core.model.AccountId
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
class MockSellerRepository @Inject constructor(
    private val store: DemoCommerceStore,
    private val sessions: SessionStore,
) : SellerRepository {
    constructor(store: DemoCommerceStore) : this(
        store,
        InMemorySessionStore(Session(AccountId("demo-seller"), "seller@rematerial.demo", Role.SELLER, "Alya", verificationStatus = VerificationStatus.APPROVED)),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    override val listings: StateFlow<List<SellerListing>> = combine(sessions.session, store.listings) { session, all ->
        sellerId(session)?.let { id -> all.filter { it.sellerId == id } }.orEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, store.listings.value.filter { it.sellerId == sellerId(sessions.current()) })
    override val orders: StateFlow<List<SellerOrder>> = combine(sessions.session, store.orders) { session, _ ->
        sellerId(session)?.let(store::sellerOrdersFor).orEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, sellerId(sessions.current())?.let(store::sellerOrdersFor).orEmpty())
    override val profile: StateFlow<SellerProfile> = combine(sessions.session, store.profiles) { session, profiles ->
        profileFor(session, profiles)
    }.stateIn(scope, SharingStarted.Eagerly, profileFor(sessions.current(), store.profiles.value))
    override suspend fun saveListing(listing: SellerListing): CommerceResult<SellerListing> {
        val sellerId = sellerId(sessions.current()) ?: return noSession()
        if (listing.state == ListingState.PUBLISHED && !canPublish()) return CommerceResult.Failure(CommerceError.SellerNotVerified)
        return store.saveListing(sellerId, canPublish(), listing)
    }
    override suspend fun setListingState(id: String, state: ListingState): CommerceResult<SellerListing> {
        val sellerId = sellerId(sessions.current()) ?: return noSession()
        if (state == ListingState.PUBLISHED && !canPublish()) return CommerceResult.Failure(CommerceError.SellerNotVerified)
        return store.setListingState(sellerId, canPublish(), id, state)
    }
    override suspend fun transitionOrder(id: String, status: OrderStatus): CommerceResult<SellerOrder> {
        val sellerId = sellerId(sessions.current()) ?: return noSession()
        return when (val result = store.transitionSellerOrder(sellerId, id, status)) {
        is CommerceResult.Failure -> result
        is CommerceResult.Success -> store.sellerOrdersFor(sellerId).firstOrNull { it.id == id }?.let { CommerceResult.Success(it) }
            ?: CommerceResult.Failure(CommerceError.OrderNotFound)
        }
    }
    override suspend fun saveProfile(profile: SellerProfile): CommerceResult<SellerProfile> {
        val session = sessions.current()
        val sellerId = sellerId(session) ?: return noSession()
        return store.saveProfile(sellerId, profile.copy(verification = verificationFor(session)))
    }
    private fun canPublish(): Boolean = sessions.current()?.let { it.role == Role.SELLER && it.verificationStatus == VerificationStatus.APPROVED } == true

    private fun sellerId(session: Session?): String? = session?.takeIf { it.role == Role.SELLER }?.accountId?.value?.let {
        if (it == "demo-seller") "seller-nusa" else it
    }

    private fun profileFor(session: Session?, profiles: Map<String, SellerProfile>): SellerProfile {
        val id = sellerId(session) ?: return SellerProfile(sellerId = "")
        return (profiles[id]?.copy(verification = verificationFor(session))) ?: SellerProfile(
            sellerId = id,
            name = session?.displayName.orEmpty(),
            storeName = session?.displayName.orEmpty(),
            location = session?.location?.area.orEmpty(),
            verification = verificationFor(session),
        )
    }

    private fun verificationFor(session: Session?): VerificationState = when (session?.verificationStatus) {
        VerificationStatus.APPROVED -> VerificationState.VERIFIED
        VerificationStatus.PENDING -> VerificationState.SUBMITTED
        VerificationStatus.NEEDS_CORRECTION -> VerificationState.NEEDS_CORRECTION
        else -> VerificationState.NOT_SUBMITTED
    }

    private fun <T> noSession(): CommerceResult<T> = CommerceResult.Failure(CommerceError.InvalidInput(listOf("session")))
}
