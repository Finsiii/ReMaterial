package com.rematerial.app.feature.identity.data

import com.rematerial.app.core.model.AccountId
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.ContactProfile
import com.rematerial.app.feature.identity.domain.LocationProfile
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.RegistrationRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session

class DemoIdentityRepository : IdentityRepository {
    private val seeded = listOf(
        Session(AccountId("demo-user"), "user@rematerial.demo", Role.USER, "Dika", ContactProfile("081234567890"), LocationProfile("Bandung", "Jl. Merdeka 24, Bandung")),
        Session(AccountId("demo-artisan"), "artisan@rematerial.demo", Role.ARTISAN, "Bima", ContactProfile("081234567891"), LocationProfile("Bandung · Cicendo", "Workshop Bima, Bandung")),
        Session(AccountId("demo-seller"), "seller@rematerial.demo", Role.SELLER, "Alya", ContactProfile("081234567892"), LocationProfile("Bandung", "Jl. Braga 12, Bandung")),
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
        val phone = request.contact?.phone?.filter { it.isDigit() }.orEmpty()
        if (!Regex("^(?:62|0)[0-9]{8,13}$").matches(phone)) {
            return Result.Failure(DomainFailure.Validation(listOf("Nomor WhatsApp wajib diisi dengan angka yang valid.")))
        }
        if (request.location?.isResolved() != true) {
            return Result.Failure(DomainFailure.Validation(listOf("Area atau alamat wajib diisi agar pengrajin terdekat dapat ditemukan.")))
        }
        return Result.Success(
            Session(
                accountId = AccountId("local-${request.email.hashCode().toUInt()}"),
                email = request.email.trim(),
                role = request.role,
                displayName = request.displayName.trim(),
                contact = request.contact,
                location = request.location,
            ),
        )
    }

    override suspend fun logout(): Result<Unit> = Result.Success(Unit)
}
