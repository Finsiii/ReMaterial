package com.rematerial.app.feature.identity.domain

import com.rematerial.app.core.model.AccountId
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import kotlinx.serialization.Serializable

typealias IdentityResult<T> = Result<T>
typealias Result<T> = com.rematerial.app.core.model.Result<T>

@Serializable
enum class Role { USER, ARTISAN, SELLER }

@Serializable
enum class ContactPreference { WHATSAPP, TELEPON }

@Serializable
data class ContactProfile(
    val phone: String,
    val whatsapp: String = phone,
    val preferred: ContactPreference = ContactPreference.WHATSAPP,
)

@Serializable
data class LocationProfile(
    val area: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "manual",
) {
    fun isResolved(): Boolean = area.isNotBlank() || address.isNotBlank()
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val role: Role,
)

@Serializable
data class RegistrationRequest(
    val displayName: String,
    val email: String,
    val password: String,
    val contact: ContactProfile? = null,
    val location: LocationProfile? = null,
    val role: Role = Role.USER,
)

@Serializable
data class Session(
    val accountId: AccountId,
    val email: String,
    val role: Role,
    val displayName: String,
    val contact: ContactProfile? = null,
    val location: LocationProfile? = null,
    val accessToken: String? = null,
)

interface IdentityRepository {
    suspend fun login(request: LoginRequest): Result<Session>
    suspend fun register(request: RegistrationRequest): Result<Session>
    suspend fun logout(): Result<Unit>
}
