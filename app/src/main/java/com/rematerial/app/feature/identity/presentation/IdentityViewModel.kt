package com.rematerial.app.feature.identity.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.ContactPreference
import com.rematerial.app.feature.identity.domain.ContactProfile
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.LocationProfile
import com.rematerial.app.feature.identity.domain.LocationResolver
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.RegistrationRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.VerificationDocumentKind
import com.rematerial.app.feature.identity.domain.VerificationDocumentStore
import com.rematerial.app.feature.identity.domain.VerificationDocuments
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdentityState(
    val role: Role? = null,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val confirmPassword: String = "",
    val phone: String = "",
    val whatsapp: String = "",
    val preferredContact: ContactPreference = ContactPreference.WHATSAPP,
    val area: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationPermissionGranted: Boolean = false,
    val isResolvingLocation: Boolean = false,
    val nik: String = "",
    val ktpPrivatePath: String? = null,
    val selfiePrivatePath: String? = null,
    val roleEvidencePrivatePath: String? = null,
    val isRegistering: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: Session? = null,
) {
    val locationResolved: Boolean
        get() = area.isNotBlank() || address.isNotBlank() || (latitude != null && longitude != null)
}

sealed interface IdentityEvent {
    data class SelectRole(val role: Role) : IdentityEvent
    data object ChangeRole : IdentityEvent
    data object ToggleRegistration : IdentityEvent
    data class EmailChanged(val value: String) : IdentityEvent
    data class PasswordChanged(val value: String) : IdentityEvent
    data class DisplayNameChanged(val value: String) : IdentityEvent
    data class ConfirmPasswordChanged(val value: String) : IdentityEvent
    data class PhoneChanged(val value: String) : IdentityEvent
    data class WhatsappChanged(val value: String) : IdentityEvent
    data class PreferredContactChanged(val value: ContactPreference) : IdentityEvent
    data class AreaChanged(val value: String) : IdentityEvent
    data class AddressChanged(val value: String) : IdentityEvent
    data class NikChanged(val value: String) : IdentityEvent
    data class VerificationDocumentSelected(val kind: VerificationDocumentKind, val uri: String) : IdentityEvent
    data class UpdateSessionLocation(val area: String, val address: String) : IdentityEvent
    data class LocationPermissionChanged(val granted: Boolean) : IdentityEvent
    data object Submit : IdentityEvent
    data object SignOut : IdentityEvent
}

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val repository: IdentityRepository,
    private val locationResolver: LocationResolver = UnavailableLocationResolver,
    private val documentStore: VerificationDocumentStore = UnavailableDocumentStore,
) : ViewModel() {
    private val _state = MutableStateFlow(IdentityState(session = repository.currentSession()))
    val state: StateFlow<IdentityState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.session.collect { session -> _state.update { it.copy(session = session) } }
        }
    }

    fun onEvent(event: IdentityEvent) {
        when (event) {
            is IdentityEvent.SelectRole -> _state.update { it.copy(role = event.role, errorMessage = null) }
            IdentityEvent.ChangeRole -> _state.value = IdentityState(session = repository.currentSession())
            IdentityEvent.ToggleRegistration -> _state.update { it.copy(isRegistering = !it.isRegistering, errorMessage = null) }
            is IdentityEvent.EmailChanged -> _state.update { it.copy(email = event.value, errorMessage = null) }
            is IdentityEvent.PasswordChanged -> _state.update { it.copy(password = event.value, errorMessage = null) }
            is IdentityEvent.DisplayNameChanged -> _state.update { it.copy(displayName = event.value, errorMessage = null) }
            is IdentityEvent.ConfirmPasswordChanged -> _state.update { it.copy(confirmPassword = event.value, errorMessage = null) }
            is IdentityEvent.PhoneChanged -> _state.update { it.copy(phone = event.value.filter(Char::isDigit), errorMessage = null) }
            is IdentityEvent.WhatsappChanged -> _state.update { it.copy(whatsapp = event.value.filter(Char::isDigit), errorMessage = null) }
            is IdentityEvent.PreferredContactChanged -> _state.update { it.copy(preferredContact = event.value, errorMessage = null) }
            is IdentityEvent.AreaChanged -> _state.update { it.copy(area = event.value, errorMessage = null) }
            is IdentityEvent.AddressChanged -> _state.update { it.copy(address = event.value, errorMessage = null) }
            is IdentityEvent.NikChanged -> _state.update { it.copy(nik = event.value.filter(Char::isDigit).take(16), errorMessage = null) }
            is IdentityEvent.LocationPermissionChanged -> {
                _state.update { it.copy(locationPermissionGranted = event.granted, errorMessage = null) }
                if (event.granted) resolveLocation()
            }
            is IdentityEvent.VerificationDocumentSelected -> importDocument(event.kind, event.uri)
            is IdentityEvent.UpdateSessionLocation -> updateSessionLocation(event.area, event.address)
            IdentityEvent.Submit -> submit()
            IdentityEvent.SignOut -> signOut()
        }
    }

    private fun resolveLocation() = viewModelScope.launch {
        _state.update { it.copy(isResolvingLocation = true, errorMessage = null) }
        when (val result = locationResolver.resolveCoarseLocation()) {
            is Result.Success -> _state.update {
                it.copy(
                    area = if (it.area.isBlank()) result.value.area else it.area,
                    latitude = result.value.latitude,
                    longitude = result.value.longitude,
                    isResolvingLocation = false,
                )
            }
            is Result.Failure -> _state.update {
                it.copy(isResolvingLocation = false, errorMessage = "Lokasi perangkat belum terbaca. Isi kota atau area secara manual.")
            }
        }
    }

    private fun importDocument(kind: VerificationDocumentKind, uri: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val previousPath = when (kind) {
            VerificationDocumentKind.KTP -> state.value.ktpPrivatePath
            VerificationDocumentKind.SELFIE -> state.value.selfiePrivatePath
            VerificationDocumentKind.PORTFOLIO, VerificationDocumentKind.STORE_EVIDENCE -> state.value.roleEvidencePrivatePath
        }
        when (val result = documentStore.import(uri, kind)) {
            is Result.Success -> {
                _state.update {
                    when (kind) {
                        VerificationDocumentKind.KTP -> it.copy(ktpPrivatePath = result.value.privatePath)
                        VerificationDocumentKind.SELFIE -> it.copy(selfiePrivatePath = result.value.privatePath)
                        VerificationDocumentKind.PORTFOLIO, VerificationDocumentKind.STORE_EVIDENCE -> it.copy(roleEvidencePrivatePath = result.value.privatePath)
                    }.copy(isLoading = false)
                }
                if (!previousPath.isNullOrBlank() && previousPath != result.value.privatePath) documentStore.delete(previousPath)
            }
            is Result.Failure -> _state.update { it.copy(isLoading = false, errorMessage = "Foto tidak dapat disimpan. Gunakan JPG, PNG, atau HEIC di bawah 10 MB.") }
        }
    }

    private fun submit() {
        val current = state.value
        if (current.isLoading) return
        val validation = if (current.isRegistering) validateRegistration(current) else validateLogin(current)
        if (validation != null) {
            _state.update { it.copy(errorMessage = validation) }
            return
        }
        val role = current.role ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = if (current.isRegistering) {
                val verification = if (role == Role.USER) null else VerificationDocuments(
                    nik = current.nik,
                    ktpPrivatePath = current.ktpPrivatePath.orEmpty(),
                    selfiePrivatePath = current.selfiePrivatePath.orEmpty(),
                    portfolioPrivatePaths = if (role == Role.ARTISAN) listOfNotNull(current.roleEvidencePrivatePath) else emptyList(),
                    storeEvidencePrivatePaths = if (role == Role.SELLER) listOfNotNull(current.roleEvidencePrivatePath) else emptyList(),
                )
                repository.register(
                    RegistrationRequest(
                        displayName = current.displayName.trim(),
                        email = current.email.trim(),
                        password = current.password,
                        contact = ContactProfile(current.phone, current.whatsapp.ifBlank { current.phone }, current.preferredContact),
                        location = LocationProfile(
                            current.area.trim(), current.address.trim(), current.latitude, current.longitude,
                            source = if (current.latitude != null) "device_coarse" else "manual",
                        ),
                        role = role,
                        verification = verification,
                    ),
                )
            } else repository.login(LoginRequest(current.email.trim(), current.password, role))
            when (result) {
                is Result.Success -> _state.update { it.copy(isLoading = false, password = "", confirmPassword = "", session = result.value, errorMessage = null) }
                is Result.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message()) }
            }
        }
    }

    /** Navigation callers must wait for this callback; logout is not complete before repository success. */
    fun signOut(onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = repository.logout()) {
                is Result.Success -> {
                    _state.value = IdentityState()
                    onSuccess()
                }
                is Result.Failure -> {
                    val message = result.error.message()
                    _state.update { it.copy(isLoading = false, errorMessage = message) }
                    onFailure(message)
                }
            }
        }
    }

    private fun updateSessionLocation(area: String, address: String) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        when (val result = repository.updateLocation(LocationProfile(area.trim(), address.trim(), source = "manual_profile"))) {
            is Result.Success -> _state.update { it.copy(isLoading = false, session = result.value) }
            is Result.Failure -> _state.update { it.copy(isLoading = false, errorMessage = result.error.message()) }
        }
    }

    private fun validateLogin(current: IdentityState): String? = when {
        current.role == null -> "Pilih jenis akun terlebih dahulu."
        !EMAIL_REGEX.matches(current.email.trim()) -> "Masukkan email yang valid."
        current.password.isBlank() -> "Masukkan kata sandi."
        else -> null
    }

    private fun validateRegistration(current: IdentityState): String? = when {
        current.displayName.trim().length < 2 -> "Masukkan nama minimal 2 karakter."
        !EMAIL_REGEX.matches(current.email.trim()) -> "Masukkan email yang valid."
        current.password.length < 8 -> "Kata sandi minimal 8 karakter."
        current.password != current.confirmPassword -> "Konfirmasi kata sandi belum sama."
        !PHONE_REGEX.matches(current.phone) -> "Nomor WhatsApp wajib diisi dengan angka yang valid."
        !current.locationResolved -> "Gunakan lokasi perangkat atau isi area secara manual."
        current.role != Role.USER && !current.nik.matches(Regex("\\d{16}")) -> "Masukkan NIK 16 digit."
        current.role != Role.USER && current.ktpPrivatePath == null -> "Foto KTP wajib dipilih."
        current.role != Role.USER && current.selfiePrivatePath == null -> "Foto selfie wajib dipilih."
        current.role != Role.USER && current.roleEvidencePrivatePath == null -> if (current.role == Role.ARTISAN) "Portofolio wajib dipilih." else "Bukti toko wajib dipilih."
        else -> null
    }

    private fun DomainFailure.message(): String = when (this) {
        DomainFailure.Unauthorized -> "Email, kata sandi, atau jenis akun tidak cocok."
        is DomainFailure.Validation -> violations.firstOrNull() ?: "Data belum dapat disimpan."
        DomainFailure.PermissionDenied -> "Izin lokasi belum diberikan."
        DomainFailure.Unavailable -> "Layanan belum tersedia."
        else -> "Terjadi kendala. Coba lagi sebentar."
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val PHONE_REGEX = Regex("^(?:62|0)[0-9]{8,13}$")
    }
}

private object UnavailableLocationResolver : LocationResolver {
    override suspend fun resolveCoarseLocation(): Result<LocationProfile> = Result.Failure(DomainFailure.Unavailable)
}

private object UnavailableDocumentStore : VerificationDocumentStore {
    override suspend fun import(sourceUri: String, kind: VerificationDocumentKind) = Result.Failure(DomainFailure.Unavailable)
    override suspend fun delete(privatePath: String) = Unit
}
