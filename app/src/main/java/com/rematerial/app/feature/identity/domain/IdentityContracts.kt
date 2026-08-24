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
data class LoginRequest(
    val email: String,
    val password: String,
    val role: Role,
)

@Serializable
data class Session(
    val accountId: AccountId,
    val email: String,
    val role: Role,
    val displayName: String,
    val accessToken: String? = null,
)

interface IdentityRepository {
    suspend fun login(request: LoginRequest): Result<Session>
    suspend fun logout(): Result<Unit>
}
