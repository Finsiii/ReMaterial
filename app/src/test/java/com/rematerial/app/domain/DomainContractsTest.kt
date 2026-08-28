package com.rematerial.app.domain

import com.rematerial.app.feature.analysis.data.AnalysisFixtures
import com.rematerial.app.feature.analysis.data.MockAiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.identity.data.DemoIdentityRepository
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.LoginRequest
import com.rematerial.app.feature.identity.domain.Role
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.getOrThrow
import com.rematerial.app.feature.analysis.data.HttpAiAnalysisGateway
import com.rematerial.app.core.media.MediaPayloadReader
import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.PhotoReference
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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
        val repository: IdentityRepository = DemoIdentityRepository()
        val result = repository.login(LoginRequest("user@rematerial.demo", "Demo123!", Role.USER))
        assertTrue(result is Result.Success)
        assertEquals(Role.USER, (result as Result.Success).value.role)
    }

    @Test
    fun `wrong role cannot use seeded user`() = runTest {
        val result = DemoIdentityRepository().login(
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
    fun `http gateway accepts three photos and keeps ai conversation metadata`() = runTest {
        val reply = """{"category":"WOOD","confidence_percent":91,"object_name":"Kursi kayu berlapis kain","object_state":"REPAIRABLE","visible_components":[{"part":"rangka","material":"kayu","evidence":"VISIBLE"},{"part":"pelapis dudukan","material":"kain","evidence":"VISIBLE"}],"inferred_hidden_materials":[{"material":"busa","reason":"Dudukan berlapis biasanya memiliki bantalan yang tertutup kain"}],"primary_strategy":"REPAIR","science":[{"title":"Kondisi kursi","principle":"Sambungan dan pelapis menentukan kelayakan perbaikan.","interpretation":"Bentuk kursi masih utuh dan layak diperbaiki.","limitation":"Bagian dalam belum terlihat.","recommended_verification":"Periksa sambungan dan bantalan."}],"product_options":[{"title":"Perbaikan pelapis kursi","explanation":"Ganti kain dan periksa busa."},{"title":"Penguatan sambungan kursi","explanation":"Kencangkan sambungan rangka."},{"title":"Pembaruan finishing kursi","explanation":"Bersihkan dan lapisi ulang permukaan."}]}"""
        val body = Json.encodeToString(mapOf("reply" to reply, "conversation_id" to "conv-1", "message_id" to "msg-1"))
        val engine = MockEngine { request ->
            assertEquals("/v1/chat", request.url.encodedPath)
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val gateway = HttpAiAnalysisGateway(
            HttpClient(engine),
            "https://api.example.test",
            mediaPayloadReader = mediaReader(),
        )
        val response = gateway.start(photoRequest())
        assertTrue(response is Result.Success)
        assertEquals(MaterialCategory.WOOD, (response as Result.Success).value.prediction.category)
        assertTrue(response.value.requestedFields.size <= 2)
        assertEquals("1", response.value.suggestedValues["quantity"])
        assertEquals("unknown", response.value.suggestedValues["condition"])
        assertEquals("unknown", response.value.suggestedValues["contamination"])
        assertEquals("conv-1", response.value.conversationId)
        assertEquals("msg-1", response.value.messageId)
        assertEquals("Kursi kayu berlapis kain", response.value.objectAnalysis?.objectName)
        assertEquals(2, response.value.objectAnalysis?.visibleComponents?.size)
        assertEquals("busa", response.value.objectAnalysis?.inferredHiddenMaterials?.first()?.material)
    }

    @Test
    fun `http gateway enriches validated final result with ai science and products`() = runTest {
        val reply = """{"science":[{"title":"Serat kayu masih layak","principle":"Arah serat dan kelembapan memengaruhi kekuatan sambungan.","interpretation":"Jawaban pengguna mendukung pemanfaatan awal sebagai produk non-struktural.","limitation":"Foto tidak membuktikan kadar air internal.","recommended_verification":"Ukur kelembapan dan periksa retak sebelum dipotong."}],"product_options":[{"title":"Rak Rempah Modular","explanation":"Potongan kayu dapat dirakit menjadi rak kecil dengan sambungan sederhana."},{"title":"Lampu Meja Kayu","explanation":"Kayu dapat menjadi dudukan lampu setelah keamanan kelistrikan diperiksa."},{"title":"Organizer Meja","explanation":"Sisa potongan cocok untuk kompartemen penyimpanan kecil."}]}"""
        val body = Json.encodeToString(mapOf("reply" to reply, "conversation_id" to "conv-2", "message_id" to "msg-2"))
        val gateway = HttpAiAnalysisGateway(
            HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }),
            "https://api.example.test",
        )
        val fixture = AnalysisFixtures.woodHigh()
        val request = CompletedAnalysisRequest(AnalysisId("analysis-ai"), MaterialCategory.WOOD, fixture.completed.observations)

        val response = gateway.complete(request)

        assertTrue(response is Result.Success)
        val completed = (response as Result.Success).value
        assertEquals("Serat kayu masih layak", completed.science.first().title)
        assertEquals("Rak Rempah Modular", completed.productOptions.first().title)
        AnalysisValidator.validate(completed, fixture.initial.requestedFields).getOrThrow()
    }

    @Test
    fun `http gateway maps unauthorized and malformed chat payload`() = runTest {
        val unauthorized = HttpAiAnalysisGateway(
            HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) }),
            "https://api.example.test",
            mediaPayloadReader = mediaReader(),
        ).start(photoRequest())
        assertEquals(DomainFailure.Unauthorized, (unauthorized as Result.Failure).error)

        val malformed = HttpAiAnalysisGateway(
            HttpClient(MockEngine { respond("{not-json", HttpStatusCode.OK) }),
            "https://api.example.test",
            mediaPayloadReader = mediaReader(),
        ).start(photoRequest())
        assertEquals(DomainFailure.MalformedResponse, (malformed as Result.Failure).error)
    }

    private fun photoRequest() = InitialAnalysisRequest(
        analysisId = AnalysisId("analysis-photo"),
        photo = PhotoReference("media-1", "/private/photo.jpg", "image/jpeg", 3),
        additionalPhotos = (2..3).map { index -> PhotoReference("media-$index", "/private/photo-$index.jpg", "image/jpeg", 3) },
    )

    private fun mediaReader() = object : MediaPayloadReader {
        override suspend fun read(photo: PhotoReference): Result<ByteArray> =
            Result.Success(byteArrayOf(1, 2, 3))
    }
}
