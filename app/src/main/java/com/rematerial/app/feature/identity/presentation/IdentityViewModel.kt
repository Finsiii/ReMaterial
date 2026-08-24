package com.rematerial.app.feature.identity.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.ContactPreference
import com.rematerial.app.feature.identity.domain.ContactProfile
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.LocationProfile
import com.rematerial.app.feature.identity.domain.RegistrationRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session
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
    val locationResolved: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val isRegistering: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val session: Session? = null,
)

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
    data class LocationPermissionChanged(val granted: Boolean) : IdentityEvent
    data object Submit : IdentityEvent
    data object SignOut : IdentityEvent
}

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val repository: IdentityRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IdentityState())
    val state: StateFlow<IdentityState> = _state.asStateFlow()

    fun onEvent(event: IdentityEvent) {
        when (event) {
            is IdentityEvent.SelectRole -> _state.update { it.copy(role = event.role, errorMessage = null) }
            IdentityEvent.ChangeRole -> _state.value = IdentityState()
            IdentityEvent.ToggleRegistration -> _state.update {
                it.copy(isRegistering = !it.isRegistering, errorMessage = null)
            }
            is IdentityEvent.EmailChanged -> _state.update { it.copy(email = event.value, errorMessage = null) }
            is IdentityEvent.PasswordChanged -> _state.update { it.copy(password = event.value, errorMessage = null) }
            is IdentityEvent.DisplayNameChanged -> _state.update { it.copy(displayName = event.value, errorMessage = null) }
            is IdentityEvent.ConfirmPasswordChanged -> _state.update { it.copy(confirmPassword = event.value, errorMessage = null) }
            is IdentityEvent.PhoneChanged -> _state.update { it.copy(phone = event.value, errorMessage = null) }
            is IdentityEvent.WhatsappChanged -> _state.update { it.copy(whatsapp = event.value, errorMessage = null) }
            is IdentityEvent.PreferredContactChanged -> _state.update { it.copy(preferredContact = event.value, errorMessage = null) }
            is IdentityEvent.AreaChanged -> _state.update { it.copy(area = event.value, locationResolved = event.value.isNotBlank() || it.address.isNotBlank(), errorMessage = null) }
            is IdentityEvent.AddressChanged -> _state.update { it.copy(address = event.value, locationResolved = event.value.isNotBlank() || it.area.isNotBlank(), errorMessage = null) }
            is IdentityEvent.LocationPermissionChanged -> _state.update { it.copy(locationPermissionGranted = event.granted, locationResolved = event.granted || it.area.isNotBlank() || it.address.isNotBlank(), errorMessage = null) }
            IdentityEvent.Submit -> submit()
            IdentityEvent.SignOut -> _state.update { it.copy(session = null, errorMessage = null) }
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
                repository.register(
                    RegistrationRequest(
                        displayName = current.displayName.trim(),
                        email = current.email.trim(),
                        password = current.password,
                        contact = ContactProfile(current.phone.trim(), current.whatsapp.trim().ifBlank { current.phone.trim() }, current.preferredContact),
                        location = LocationProfile(current.area.trim(), current.address.trim(), source = if (current.locationPermissionGranted) "device" else "manual"),
                        role = role,
                    ),
                )
            } else {
                repository.login(LoginRequest(current.email.trim(), current.password, role))
            }
            _state.update { it.copy(isLoading = false) }
            when (result) {
                is Result.Success -> _state.update { it.copy(session = result.value, errorMessage = null) }
                is Result.Failure -> _state.update { it.copy(errorMessage = result.error.message()) }
            }
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
        !PHONE_REGEX.matches(current.phone.filter { it.isDigit() }) -> "Nomor WhatsApp wajib diisi dengan angka yang valid."
        !current.locationResolved -> "Pilih lokasi perangkat atau isi area secara manual."
        else -> null
    }

    private fun DomainFailure.message(): String = when (this) {
        DomainFailure.Unauthorized -> "Email, kata sandi, atau jenis akun tidak cocok."
        is DomainFailure.Validation -> violations.firstOrNull() ?: "Data belum dapat disimpan."
        else -> "Terjadi kendala. Coba lagi sebentar."
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        val PHONE_REGEX = Regex("^(?:62|0)[0-9]{8,13}$")
    }
}
