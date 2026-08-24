package com.rematerial.app.feature.identity.data

import com.rematerial.app.core.model.AccountId
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.RegistrationRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session

class DemoIdentityRepository : IdentityRepository {
    private val seeded = listOf(
        Session(AccountId("demo-user"), "user@rematerial.demo", Role.USER, "Dika"),
        Session(AccountId("demo-artisan"), "artisan@rematerial.demo", Role.ARTISAN, "Bima"),
        Session(AccountId("demo-seller"), "seller@rematerial.demo", Role.SELLER, "Alya"),
    )

    override suspend fun login(request: LoginRequest): Result<Session> = when {
        request.email !in seeded.map { it.email } || request.password != "Demo123!" ->
            Result.Failure(DomainFailure.Unauthorized)
        else -> seeded.first { it.email == request.email }.let { session ->
            if (request.role != session.role) Result.Failure(DomainFailure.Unauthorized)
            else Result.Success(session)
        }
    }

    override suspend fun register(request: RegistrationRequest): Result<Session> {
        if (seeded.any { it.email.equals(request.email.trim(), ignoreCase = true) }) {
            return Result.Failure(DomainFailure.Validation(listOf("Email sudah terdaftar.")))
        }
        return Result.Success(
            Session(
                accountId = AccountId("local-${request.email.hashCode().toUInt()}"),
                email = request.email.trim(),
                role = Role.USER,
                displayName = request.displayName.trim(),
            ),
        )
    }

    override suspend fun logout(): Result<Unit> = Result.Success(Unit)
}
