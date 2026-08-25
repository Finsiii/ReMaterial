package com.rematerial.app.feature.identity.domain

import com.rematerial.app.core.model.AccountId
import com.rematerial.app.core.model.Result
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

typealias IdentityResult<T> = Result<T>

@Serializable
enum class Role { USER, ARTISAN, SELLER }

@Serializable
enum class ContactPreference { WHATSAPP, TELEPON }

@Serializable
enum class VerificationStatus { NOT_REQUIRED, PENDING, APPROVED, NEEDS_CORRECTION }

@Serializable
data class ContactProfile(val phone: String, val whatsapp: String = phone, val preferred: ContactPreference = ContactPreference.WHATSAPP)

@Serializable
data class LocationProfile(
    val area: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "manual",
    val capturedAtEpochMillis: Long? = null,
    val accuracyMeters: Float? = null,
) {
    fun isResolved(): Boolean = area.isNotBlank() || address.isNotBlank() || (latitude != null && longitude != null)
}

@Serializable
data class VerificationDocuments(
    val nik: String,
    val ktpPrivatePath: String,
    val selfiePrivatePath: String,
    val portfolioPrivatePaths: List<String> = emptyList(),
    val storeEvidencePrivatePaths: List<String> = emptyList(),
) {
    fun isCompleteFor(role: Role): Boolean =
        nik.length == 16 && nik.all(Char::isDigit) && ktpPrivatePath.isNotBlank() && selfiePrivatePath.isNotBlank() &&
            when (role) {
                Role.USER -> true
                Role.ARTISAN -> portfolioPrivatePaths.isNotEmpty()
                Role.SELLER -> storeEvidencePrivatePaths.isNotEmpty()
            }
}

@Serializable
data class LoginRequest(val email: String, val password: String, val role: Role)

@Serializable
data class RegistrationRequest(
    val displayName: String,
    val email: String,
    val password: String,
    val contact: ContactProfile? = null,
    val location: LocationProfile? = null,
    val role: Role = Role.USER,
    val verification: VerificationDocuments? = null,
)

@Serializable
data class Session(
    val accountId: AccountId,
    val email: String,
    val role: Role,
    val displayName: String,
    val contact: ContactProfile? = null,
    val location: LocationProfile? = null,
    @Transient val accessToken: String? = null,
    val verificationStatus: VerificationStatus = if (role == Role.USER) VerificationStatus.NOT_REQUIRED else VerificationStatus.PENDING,
)

interface SessionStore {
    val session: StateFlow<Session?>
    fun current(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}

/** Credentials and verification metadata owned by one registered account.
 * The password is represented only by a salted digest; the raw password never enters this model.
 */
@Serializable
data class AccountRecord(
    val session: Session,
    val passwordSalt: String,
    val passwordDigest: String,
    val verification: VerificationDocuments? = null,
)

interface AccountStore {
    suspend fun seedIfEmpty(records: List<AccountRecord>)
    suspend fun findByEmail(email: String): AccountRecord?
    suspend fun create(record: AccountRecord): Result<Unit>
    suspend fun update(record: AccountRecord): Result<Unit>
}

interface LocationResolver { suspend fun resolveCoarseLocation(): Result<LocationProfile> }

enum class VerificationDocumentKind { KTP, SELFIE, PORTFOLIO, STORE_EVIDENCE }
data class OwnedDocument(val privatePath: String)

interface VerificationDocumentStore {
    suspend fun import(sourceUri: String, kind: VerificationDocumentKind): Result<OwnedDocument>
    suspend fun delete(privatePath: String)
}

interface IdentityRepository {
    val session: StateFlow<Session?>
    fun currentSession(): Session?
    suspend fun login(request: LoginRequest): Result<Session>
    suspend fun register(request: RegistrationRequest): Result<Session>
    suspend fun updateLocation(location: LocationProfile): Result<Session>
    suspend fun logout(): Result<Unit>
}
