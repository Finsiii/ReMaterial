package com.rematerial.app.feature.identity

import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.identity.data.DemoIdentityRepository
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.data.InMemoryAccountStore
import com.rematerial.app.feature.identity.domain.ContactProfile
import com.rematerial.app.feature.identity.domain.LocationProfile
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.RegistrationRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.VerificationDocuments
import com.rematerial.app.feature.identity.domain.VerificationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentitySessionPolicyTest {
    @Test
    fun logoutClearsPersistedSessionAndCredentials() = runTest {
        val sessions = InMemorySessionStore()
        val repository = DemoIdentityRepository(sessions)

        assertTrue(repository.login(LoginRequest("user@rematerial.demo", "Demo123!", Role.USER)) is Result.Success)
        assertEquals("user@rematerial.demo", sessions.current()?.email)

        assertTrue(repository.logout() is Result.Success)
        assertNull(sessions.current())
    }

    @Test
    fun newArtisanRequiresOwnedVerificationDocumentsAndStartsPending() = runTest {
        val sessions = InMemorySessionStore()
        val repository = DemoIdentityRepository(sessions)
        val base = RegistrationRequest(
            displayName = "Rani Workshop",
            email = "rani.artisan@example.com",
            password = "password123",
            contact = ContactProfile("081234567890"),
            location = LocationProfile("Bandung"),
            role = Role.ARTISAN,
        )

        assertTrue(repository.register(base) is Result.Failure)

        val result = repository.register(
            base.copy(
                verification = VerificationDocuments(
                    nik = "3273010101010001",
                    ktpPrivatePath = "/private/ktp.jpg",
                    selfiePrivatePath = "/private/selfie.jpg",
                    portfolioPrivatePaths = listOf("/private/portfolio.jpg"),
                ),
            ),
        )

        assertTrue(result is Result.Success)
        assertEquals(VerificationStatus.PENDING, (result as Result.Success).value.verificationStatus)
        assertEquals(VerificationStatus.PENDING, sessions.current()?.verificationStatus)
    }

    @Test
    fun registeredAccountCanLoginAgainAfterLogoutWithoutPlaintextCredentialStorage() = runTest {
        val sessions = InMemorySessionStore()
        val accounts = InMemoryAccountStore()
        val repository = DemoIdentityRepository(sessions, accounts)
        val request = RegistrationRequest(
            displayName = "Rani",
            email = "rani.relogin@example.com",
            password = "password123",
            contact = ContactProfile("081234567890"),
            location = LocationProfile("Bandung"),
        )

        assertTrue(repository.register(request) is Result.Success)
        assertTrue(repository.logout() is Result.Success)
        assertNull(repository.currentSession())
        val loggedIn = repository.login(LoginRequest(request.email, request.password, Role.USER))
        assertTrue(loggedIn is Result.Success)
        assertTrue(accounts.findByEmail(request.email)?.passwordDigest?.contains(request.password) == false)
        assertNull(accounts.findByEmail(request.email)?.session?.accessToken)
    }

    @Test
    fun registeredVerificationMetadataRemainsOwnedByAccountRecord() = runTest {
        val accounts = InMemoryAccountStore()
        val repository = DemoIdentityRepository(InMemorySessionStore(), accounts)
        val verification = VerificationDocuments(
            nik = "3273010101010001",
            ktpPrivatePath = "/private/ktp.jpg",
            selfiePrivatePath = "/private/selfie.jpg",
            portfolioPrivatePaths = listOf("/private/portfolio.jpg"),
        )
        val result = repository.register(
            RegistrationRequest(
                displayName = "Rani Workshop",
                email = "rani.metadata@example.com",
                password = "password123",
                contact = ContactProfile("081234567890"),
                location = LocationProfile("Bandung"),
                role = Role.ARTISAN,
                verification = verification,
            ),
        )
        assertTrue(result is Result.Success)
        assertEquals(verification, accounts.findByEmail("rani.metadata@example.com")?.verification)
    }

    @Test
    fun manualLocationUpdateSurvivesLogoutAndRelogin() = runTest {
        val sessions = InMemorySessionStore()
        val accounts = InMemoryAccountStore()
        val repository = DemoIdentityRepository(sessions, accounts)
        assertTrue(repository.login(LoginRequest("user@rematerial.demo", "Demo123!", Role.USER)) is Result.Success)
        assertTrue(repository.updateLocation(LocationProfile("Cimahi", "Jl. Cihanjuang")) is Result.Success)
        assertTrue(repository.logout() is Result.Success)
        val relogin = repository.login(LoginRequest("user@rematerial.demo", "Demo123!", Role.USER)) as Result.Success
        assertEquals("Cimahi", relogin.value.location?.area)
    }

    @Test
    fun locationPermissionAloneDoesNotResolveLocation() {
        val location = LocationProfile(area = "", address = "", source = "device_permission")
        assertTrue(!location.isResolved())
    }
}
