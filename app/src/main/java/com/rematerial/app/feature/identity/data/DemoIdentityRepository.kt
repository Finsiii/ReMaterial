package com.rematerial.app.feature.identity.data

import com.rematerial.app.core.model.AccountId
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemorySessionStore(initial: Session? = null) : SessionStore {
    private val mutable = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = mutable
    override fun current(): Session? = mutable.value
    override suspend fun save(session: Session) { mutable.value = session }
    override suspend fun clear() { mutable.value = null }
}

class DemoIdentityRepository(
    private val sessions: SessionStore = InMemorySessionStore(),
    private val accounts: AccountStore = InMemoryAccountStore(),
) : IdentityRepository {
    private val seeded = listOf(
        Session(AccountId("demo-user"), "user@rematerial.demo", Role.USER, "Dika", ContactProfile("081234567890"), LocationProfile("Bandung", "Jl. Merdeka 24, Bandung"), verificationStatus = VerificationStatus.NOT_REQUIRED),
        Session(AccountId("demo-artisan"), "artisan@rematerial.demo", Role.ARTISAN, "Bima", ContactProfile("081234567891"), LocationProfile("Bandung · Cicendo", "Workshop Bima, Bandung"), verificationStatus = VerificationStatus.APPROVED),
        Session(AccountId("demo-seller"), "seller@rematerial.demo", Role.SELLER, "Alya", ContactProfile("081234567892"), LocationProfile("Bandung", "Jl. Braga 12, Bandung"), verificationStatus = VerificationStatus.APPROVED),
    )
    private val seededRecords = seeded.mapIndexed { index, session ->
        AccountRecord(
            session = session,
            passwordSalt = "demo-salt-${session.accountId.value.removePrefix("demo-")}",
            passwordDigest = DEMO_DIGESTS[index],
        )
    }

    override val session: StateFlow<Session?> = sessions.session
    override fun currentSession(): Session? = sessions.current()

    override suspend fun login(request: LoginRequest): Result<Session> {
        accounts.seedIfEmpty(seededRecords)
        val record = accounts.findByEmail(request.email)
        if (record == null || request.role != record.session.role ||
            !PasswordDigests.matches(request.password, record.passwordSalt, record.passwordDigest)
        ) return Result.Failure(DomainFailure.Unauthorized)
        val authenticated = record.session.copy(accessToken = "demo-token-${record.session.accountId.value}")
        sessions.save(authenticated)
        return Result.Success(authenticated)
    }

    override suspend fun register(request: RegistrationRequest): Result<Session> {
        accounts.seedIfEmpty(seededRecords)
        val violations = buildList {
            if (accounts.findByEmail(request.email) != null) add("Email sudah terdaftar.")
            if (!PHONE.matches(request.contact?.phone?.filter(Char::isDigit).orEmpty())) add("Nomor WhatsApp wajib diisi dengan angka yang valid.")
            if (request.location?.isResolved() != true) add("Area, alamat, atau koordinat lokasi wajib tersedia.")
            if (request.password.length < 8) add("Kata sandi minimal 8 karakter.")
            if (request.role != Role.USER && request.verification?.isCompleteFor(request.role) != true) {
                add(if (request.role == Role.ARTISAN) "NIK, KTP, selfie, dan portofolio wajib dilengkapi." else "NIK, KTP, selfie, dan bukti toko wajib dilengkapi.")
            }
        }
        if (violations.isNotEmpty()) return Result.Failure(DomainFailure.Validation(violations))
        val created = Session(
            AccountId("local-${request.email.trim().lowercase().hashCode().toUInt()}"), request.email.trim(), request.role, request.displayName.trim(),
            request.contact, request.location, null,
            if (request.role == Role.USER) VerificationStatus.NOT_REQUIRED else VerificationStatus.PENDING,
        )
        val salt = PasswordDigests.newSalt()
        val ownedRecord = AccountRecord(
            session = created,
            passwordSalt = salt,
            passwordDigest = PasswordDigests.digest(request.password, salt),
            verification = request.verification,
        )
        when (val saved = accounts.create(ownedRecord)) {
            is Result.Failure -> return saved
            is Result.Success -> Unit
        }
        val authenticated = created.copy(accessToken = "local-token-${created.accountId.value}")
        sessions.save(authenticated)
        return Result.Success(authenticated)
    }

    override suspend fun updateLocation(location: LocationProfile): Result<Session> {
        if (!location.isResolved()) return Result.Failure(DomainFailure.Validation(listOf("Lokasi belum berhasil ditentukan.")))
        val current = sessions.current() ?: return Result.Failure(DomainFailure.Unauthorized)
        val updated = current.copy(location = location)
        val account = accounts.findByEmail(current.email) ?: return Result.Failure(DomainFailure.Unauthorized)
        when (val saved = accounts.update(account.copy(session = account.session.copy(location = location)))) {
            is Result.Failure -> return saved
            is Result.Success -> Unit
        }
        sessions.save(updated)
        return Result.Success(updated)
    }

    override suspend fun logout(): Result<Unit> = runCatching {
        sessions.clear()
        Result.Success(Unit)
    }.getOrElse { Result.Failure(DomainFailure.Unavailable) }

    private companion object {
        val PHONE = Regex("^(?:62|0)[0-9]{8,13}$")
        val DEMO_DIGESTS = listOf(
            "8983a6e8a1ca16f261a602276714e43bf6b3ca1acb05311db71617a28168cd00",
            "c959f12e0a371a88ab3b47e04ae56b28f0ba90292c50718b095454dd0f54721e",
            "3d5114d7c34619c3fcb7c1b149c1629137cc99e773a565e799720eb422b3af00",
        )
    }
}
