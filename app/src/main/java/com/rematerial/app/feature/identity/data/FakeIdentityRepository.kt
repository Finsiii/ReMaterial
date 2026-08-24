package com.rematerial.app.feature.identity.data

import com.rematerial.app.core.model.AccountId
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session

class FakeIdentityRepository : IdentityRepository {
    private val seeded = Session(
        accountId = AccountId("demo-user"),
        email = "user@rematerial.demo",
        role = Role.USER,
        displayName = "Dika",
    )

    override suspend fun login(request: LoginRequest): Result<Session> = when {
        request.email != seeded.email || request.password != "Demo123!" ->
            Result.Failure(DomainFailure.Unauthorized)
        request.role != seeded.role -> Result.Failure(DomainFailure.Unauthorized)
        else -> Result.Success(seeded)
    }

    override suspend fun logout(): Result<Unit> = Result.Success(Unit)
}
