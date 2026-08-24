package com.rematerial.app.domain

import com.rematerial.app.feature.analysis.data.AnalysisFixtures
import com.rematerial.app.feature.analysis.data.MockAiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.identity.data.FakeIdentityRepository
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.getOrThrow
import com.rematerial.app.feature.analysis.transport.AnalysisHttpDtos
import com.rematerial.app.feature.analysis.transport.AnalysisMappers
import com.rematerial.app.feature.analysis.data.HttpAiAnalysisGateway
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.MaterialCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainContractsTest {
    @Test
    fun `seeded user has immutable USER role`() = runTest {
        val repository: IdentityRepository = FakeIdentityRepository()
        val result = repository.login(LoginRequest("user@rematerial.demo", "Demo123!", Role.USER))
        assertTrue(result is Result.Success)
        assertEquals(Role.USER, (result as Result.Success).value.role)
    }

    @Test
    fun `wrong role cannot use seeded user`() = runTest {
        val result = FakeIdentityRepository().login(
            LoginRequest("user@rematerial.demo", "Demo123!", Role.ARTISAN),
        )
        assertTrue(result is Result.Failure)
        assertEquals(DomainFailure.Unauthorized, (result as Result.Failure).error)
    }

    @Test
    fun `fixtures cover every supported category and confidence threshold`() {
        val fixtures = AnalysisFixtures.allCompleted()
        assertEquals(
            setOf(MaterialCategory.METAL, MaterialCategory.CABLE, MaterialCategory.PLASTIC,
                MaterialCategory.WOOD, MaterialCategory.TEXTILE, MaterialCategory.ELECTRONICS),
            fixtures.map { it.category }.toSet(),
        )
        assertTrue(fixtures.any { it.confidence >= 0.8 })
        assertTrue(fixtures.any { it.confidence in 0.5..0.79 })
        assertTrue(fixtures.any { it.confidence < 0.5 })
        fixtures.forEach { AnalysisValidator.validate(it.completed).getOrThrow() }
    }

    @Test
    fun `validator rejects unresolved science references and invalid product score`() {
        val valid = AnalysisFixtures.metalHigh().completed
        val invalidScience = valid.copy(
            science = listOf(valid.science.first().copy(observationRefs = listOf(FieldId("missing")))),
        )
        assertTrue(AnalysisValidator.validate(invalidScience) is Result.Failure)
        val invalidOption = valid.copy(
            productOptions = listOf(valid.productOptions.first().copy(provisionalProductScore = Double.NaN)),
        )
        assertTrue(AnalysisValidator.validate(invalidOption) is Result.Failure)
    }

    @Test
    fun `mock gateway exposes typed error branches`() = runTest {
        val gateway: AiAnalysisGateway = MockAiAnalysisGateway(MockAiAnalysisGateway.Scenario.TIMEOUT)
        val result = gateway.start(AnalysisFixtures.initialRequest())
        assertEquals(DomainFailure.Timeout, (result as Result.Failure).error)
    }

    @Test
    fun `http gateway requires https`() = runTest {
        val client = HttpClient(MockEngine { respond("{}", HttpStatusCode.OK) })
        val result = HttpAiAnalysisGateway(client, "http://localhost:8080")
            .start(AnalysisFixtures.initialRequest())
        assertTrue((result as Result.Failure).error is DomainFailure.Validation)
    }

    @Test
    fun `http gateway maps typed response and unauthorized`() = runTest {
        val body = Json.encodeToString(AnalysisHttpDtos.initialResponseSerializer, AnalysisMappers.toDto(AnalysisFixtures.metalHigh().initial))
        val engine = MockEngine(MockEngineConfig().apply {
            addHandler { request ->
                if (request.url.encodedPath.endsWith("/initial")) {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respondError(HttpStatusCode.Unauthorized)
                }
            }
        })
        val gateway = HttpAiAnalysisGateway(HttpClient(engine), "https://api.example.test")
        val response = gateway.start(AnalysisFixtures.initialRequest())
        assertTrue(response is Result.Success)
        assertEquals(MaterialCategory.METAL, (response as Result.Success).value.prediction.category)
    }

    @Test
    fun `http gateway maps unauthorized and malformed payload`() = runTest {
        val unauthorized = HttpAiAnalysisGateway(
            HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) }),
            "https://api.example.test",
        ).start(AnalysisFixtures.initialRequest())
        assertEquals(DomainFailure.Unauthorized, (unauthorized as Result.Failure).error)

        val malformed = HttpAiAnalysisGateway(
            HttpClient(MockEngine { respond("{not-json", HttpStatusCode.OK) }),
            "https://api.example.test",
        ).start(AnalysisFixtures.initialRequest())
        assertEquals(DomainFailure.MalformedResponse, (malformed as Result.Failure).error)
    }
}
