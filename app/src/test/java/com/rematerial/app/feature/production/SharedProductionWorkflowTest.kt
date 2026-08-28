package com.rematerial.app.feature.production

import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.artisan.data.MockArtisanRepository
import com.rematerial.app.feature.identity.data.InMemorySessionStore
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.feature.identity.domain.Session
import com.rematerial.app.feature.identity.domain.VerificationStatus
import com.rematerial.app.core.model.AccountId
import com.rematerial.app.feature.production.data.DemoProductionStore
import com.rematerial.app.feature.production.data.MockProductionRepository
import com.rematerial.app.feature.production.domain.ProductDraft
import com.rematerial.app.feature.production.domain.ProductionRequestInput
import com.rematerial.app.feature.production.domain.ProductionStatus
import com.rematerial.app.feature.production.domain.userFacingMaterialSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedProductionWorkflowTest {
    @Test
    fun userFacingMaterialSummaryNeverExposesAnalysisId() {
        val summary = userFacingMaterialSummary("Tekstil", "analysis-2a2c162e")

        assertEquals("Tekstil · Dipilih dari hasil analisis material", summary)
        assertTrue(!summary.contains("2a2c162e"))
    }

    @Test
    fun textileDraftFindsVerifiedBogorArtisan() = runTest {
        val repository = MockProductionRepository(DemoProductionStore(seedDemoRequest = false), InMemorySessionStore(demoUser()))
        repository.saveDraft(
            ProductDraft(
                optionId = ProductOptionId("repair-textile"), title = "Perbaikan sweatshirt", analysisId = "analysis-textile",
                safetyAllowed = true, requiredCapabilities = listOf("textile"), requiredTools = listOf("sewing-tools"), requiredSkills = listOf("sewing"),
            ),
        )

        val artisans = (repository.searchArtisans("Bogor") as Result.Success).value

        assertEquals(listOf("artisan-nadira"), artisans.map { it.id })
        assertTrue(artisans.single().verified)
    }

    @Test
    fun submittedUserRequestAppearsForAssignedArtisanAndUpdatesUserTimeline() = runTest {
        val workflow = DemoProductionStore(seedDemoRequest = false)
        val userSessions = InMemorySessionStore(demoUser())
        val artisanSessions = InMemorySessionStore(demoArtisan(VerificationStatus.APPROVED))
        val production = MockProductionRepository(workflow, userSessions)
        val artisan = MockArtisanRepository(workflow, artisanSessions)

        val submitted = production.submit(validInput()) as Result.Success
        assertEquals(submitted.value.id, artisan.observeJobs().first().single().id)

        assertTrue(artisan.updateJob(submitted.value.id, ProductionStatus.ACCEPTED) is Result.Success)
        assertEquals(ProductionStatus.ACCEPTED, production.getRequest(submitted.value.id).let { (it as Result.Success).value.status })
        assertTrue(artisan.updateJob(submitted.value.id, ProductionStatus.COMPLETED) is Result.Failure)
    }

    @Test
    fun pendingArtisanCannotAcceptJob() = runTest {
        val workflow = DemoProductionStore(seedDemoRequest = false)
        val production = MockProductionRepository(workflow, InMemorySessionStore(demoUser()))
        val submitted = production.submit(validInput()) as Result.Success
        val artisan = MockArtisanRepository(workflow, InMemorySessionStore(demoArtisan(VerificationStatus.PENDING)))

        assertTrue(artisan.updateJob(submitted.value.id, ProductionStatus.ACCEPTED) is Result.Failure)
    }

    @Test
    fun matchingUsesVerifiedCapabilitiesToolsAndSkills() = runTest {
        val repository = MockProductionRepository(DemoProductionStore(seedDemoRequest = false), InMemorySessionStore(demoUser()))
        repository.saveDraft(validInput().draft)

        val artisans = repository.searchArtisans("Bandung") as Result.Success

        assertTrue(artisans.value.isNotEmpty())
        assertEquals("artisan-bima", artisans.value.first().id)
    }

    @Test
    fun requestsAreScopedToOwnerAndUserCannotReadAnotherAccountsRequest() = runTest {
        val workflow = DemoProductionStore(seedDemoRequest = false)
        val userA = InMemorySessionStore(demoUser())
        val userB = InMemorySessionStore(demoUser().copy(accountId = AccountId("other-user"), email = "other@rematerial.demo", displayName = "Other"))
        val productionA = MockProductionRepository(workflow, userA)
        val productionB = MockProductionRepository(workflow, userB)

        val submitted = productionA.submit(validInput()) as Result.Success

        assertEquals(listOf(submitted.value.id), productionA.observeRequests().first().map { it.id })
        assertTrue(productionB.observeRequests().first().isEmpty())
        assertTrue(productionB.getRequest(submitted.value.id) is Result.Failure)
        assertTrue(productionB.cancelRequest(submitted.value.id) is Result.Failure)
    }

    @Test
    fun customerCanApproveOrRequestRevisionOnlyAfterArtisanReadyForReview() = runTest {
        val workflow = DemoProductionStore(seedDemoRequest = false)
        val userSessions = InMemorySessionStore(demoUser())
        val artisanSessions = InMemorySessionStore(demoArtisan(VerificationStatus.APPROVED))
        val production = MockProductionRepository(workflow, userSessions)
        val artisan = MockArtisanRepository(workflow, artisanSessions)
        val submitted = production.submit(validInput()) as Result.Success

        assertTrue(production.completeRequest(submitted.value.id) is Result.Failure)
        artisan.updateJob(submitted.value.id, ProductionStatus.ACCEPTED)
        artisan.updateJob(submitted.value.id, ProductionStatus.IN_PRODUCTION)
        artisan.updateJob(submitted.value.id, ProductionStatus.READY_FOR_REVIEW)
        assertEquals(ProductionStatus.READY_FOR_REVIEW, (production.getRequest(submitted.value.id) as Result.Success).value.status)
        assertEquals(ProductionStatus.REVISION_REQUESTED, (production.requestRevision(submitted.value.id) as Result.Success).value.status)
        assertTrue(production.completeRequest(submitted.value.id) is Result.Failure)
        artisan.updateJob(submitted.value.id, ProductionStatus.IN_PRODUCTION)
        artisan.updateJob(submitted.value.id, ProductionStatus.READY_FOR_REVIEW)
        assertEquals(ProductionStatus.COMPLETED, (production.completeRequest(submitted.value.id) as Result.Success).value.status)
        assertTrue(artisan.updateJob(submitted.value.id, ProductionStatus.IN_PRODUCTION) is Result.Failure)
    }

    private fun validInput() = ProductionRequestInput(
        artisanId = "artisan-bima",
        draft = ProductDraft(
            optionId = ProductOptionId("lampu-kabel"),
            title = "Lampu meja kabel tembaga",
            materialSummary = "Kabel tembaga 2,45 kg",
            minimumQuantity = "1 kg",
            analysisId = "analysis-real",
            safetyAllowed = true,
            requiredCapabilities = listOf("cable", "metal"),
            requiredTools = listOf("hand-tools", "finishing-tools"),
            requiredSkills = listOf("basic-making", "surface-finishing"),
            provisionalScore = 89.0,
            estimatedUsage = "2.2 kg",
        ),
        quantity = 1,
        notes = "Pertahankan tekstur kabel.",
        address = "Jl. Merdeka 24, Bandung",
        targetDateIso = "2026-09-20",
        phone = "081234567890",
        whatsapp = "081234567890",
    )

    private fun demoUser() = Session(
        AccountId("demo-user"), "user@rematerial.demo", Role.USER, "Dika",
        verificationStatus = VerificationStatus.NOT_REQUIRED,
    )

    private fun demoArtisan(status: VerificationStatus) = Session(
        AccountId("demo-artisan"), "artisan@rematerial.demo", Role.ARTISAN, "Bima",
        verificationStatus = status,
    )
}
