package com.rematerial.app.feature.identity

import com.rematerial.app.feature.identity.data.DemoIdentityRepository
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.presentation.IdentityEvent
import com.rematerial.app.feature.identity.presentation.IdentityViewModel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.RegistrationRequest
import org.junit.Test
import com.rematerial.app.feature.identity.domain.ContactProfile
import com.rematerial.app.feature.identity.domain.LocationProfile

class IdentityViewModelTest {
    @Test
    fun registrationKeepsSelectedAccountRole() = runTest {
        val result = DemoIdentityRepository().register(
            RegistrationRequest(
                displayName = "Bima",
                email = "bima-baru@example.com",
                password = "password123",
                contact = ContactProfile("081234567890"),
                location = LocationProfile("Bandung"),
                role = Role.ARTISAN,
            ),
        )

        assertTrue(result is Result.Success && result.value.role == Role.ARTISAN)
    }

    @Test
    fun seededLoginRejectsWrongRoleAndRegistrationCreatesUserSession() = runTest {
        val repository = DemoIdentityRepository()
        val artisan = repository.login(LoginRequest("artisan@rematerial.demo", "Demo123!", Role.ARTISAN))
        assertEquals(Role.ARTISAN, (artisan as Result.Success).value.role)
        assertTrue(repository.login(LoginRequest("user@rematerial.demo", "Demo123!", Role.ARTISAN)) is Result.Failure)

        val registration = repository.register(RegistrationRequest("Rani", "rani@example.com", "password123", ContactProfile("081234567890"), LocationProfile("Bandung")))
        assertEquals(Role.USER, (registration as Result.Success).value.role)

        val invalidRegistration = IdentityViewModel(repository)
        invalidRegistration.onEvent(IdentityEvent.SelectRole(Role.USER))
        invalidRegistration.onEvent(IdentityEvent.ToggleRegistration)
        invalidRegistration.onEvent(IdentityEvent.Submit)
        assertTrue(invalidRegistration.state.value.errorMessage.orEmpty().isNotBlank())
    }

    @Test
    fun registrationRejectsMissingContactAndLocation() = runTest {
        val result = DemoIdentityRepository().register(RegistrationRequest("Rani", "new@example.com", "password123"))
        assertTrue(result is Result.Failure)
    }
}
