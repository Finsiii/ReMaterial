package com.rematerial.app.feature.analysis.data

import com.rematerial.app.core.media.MediaPayloadReader
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisCatalog
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.RankedCategoryPrediction
import com.rematerial.app.feature.analysis.transport.AnalysisMappers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HttpAiAnalysisGateway(
    private val client: HttpClient,
    private val baseUrl: String,
    private val model: String = "gpt-5-6-mini",
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val mediaPayloadReader: MediaPayloadReader? = null,
    private val authorizationProvider: AnalysisAuthorizationProvider = AnalysisAuthorizationProvider.None,
) : AiAnalysisGateway {
    override suspend fun start(request: InitialAnalysisRequest): Result<InitialAnalysisResponse> {
        if (!isSecureBaseUrl()) return insecureUrlFailure()
        request.manualCategory?.let { category ->
            return Result.Success(initialResponse(request, category, confidence = 1.0))
        }

        val photo = request.photo ?: return Result.Failure(DomainFailure.UnsupportedImage)
        val reader = mediaPayloadReader ?: return Result.Failure(DomainFailure.Unavailable)
        val bytes = when (val media = reader.read(photo)) {
            is Result.Success -> media.value
            is Result.Failure -> return media
        }
        val reply = when (
            val response = ask(
                message = ReMaterialAiPrompts.initial(request.analysisId.value),
                image = Base64.getEncoder().encodeToString(bytes),
            )
        ) {
            is Result.Success -> response.value
            is Result.Failure -> return response
        }

        return try {
            val classification = decodeReply<ClassificationReply>(reply)
            val category = classification.category.toMaterialCategory()
            val confidence = classification.confidencePercent.toConfidence()
            val response = initialResponse(request, category, confidence)
            when (AnalysisValidator.validate(response)) {
                is Result.Success -> Result.Success(response)
                is Result.Failure -> Result.Failure(DomainFailure.UnsupportedSchema)
            }
        } catch (_: IllegalArgumentException) {
            Result.Failure(DomainFailure.MalformedResponse)
        } catch (_: SerializationException) {
            Result.Failure(DomainFailure.MalformedResponse)
        }
    }

    override suspend fun complete(request: CompletedAnalysisRequest): Result<CompletedAnalysisResponse> {
        if (!isSecureBaseUrl()) return insecureUrlFailure()
        val observationsJson = json.encodeToString(request.observations.map(AnalysisMappers::toDto))
        val reply = when (val response = ask(ReMaterialAiPrompts.complete(request.category, observationsJson))) {
            is Result.Success -> response.value
            is Result.Failure -> return response
        }

        return try {
            val enrichment = decodeReply<CompletionReply>(reply)
            val completed = AnalysisFixtures.completedFor(request).enrichedWith(enrichment)
            when (AnalysisValidator.validate(completed, AnalysisCatalog.schemaFor(request.category))) {
                is Result.Success -> Result.Success(completed)
                is Result.Failure -> Result.Failure(DomainFailure.UnsupportedSchema)
            }
        } catch (_: IllegalArgumentException) {
            Result.Failure(DomainFailure.MalformedResponse)
        } catch (_: SerializationException) {
            Result.Failure(DomainFailure.MalformedResponse)
        }
    }

    private suspend fun ask(message: String, image: String? = null): Result<String> = try {
        val response = client.post(baseUrl.trimEnd('/') + "/v1/chat") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            authorizationProvider.authorizationHeader()?.let { header(HttpHeaders.Authorization, it) }
            setBody(json.encodeToString(ChatRequest(message, image, model)))
        }
        when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                Result.Failure(DomainFailure.Unauthorized)
            response.status == HttpStatusCode.RequestTimeout || response.status.value == 504 ->
                Result.Failure(DomainFailure.Timeout)
            response.status.value == 400 || response.status.value == 413 || response.status.value == 415 || response.status.value == 422 ->
                Result.Failure(if (image != null) DomainFailure.UnsupportedImage else DomainFailure.MalformedResponse)
            response.status.value !in 200..299 -> Result.Failure(DomainFailure.Unavailable)
            else -> {
                val envelope = json.decodeFromString<ChatEnvelope>(response.bodyAsText())
                if (envelope.reply.isBlank()) Result.Failure(DomainFailure.MalformedResponse)
                else Result.Success(envelope.reply)
            }
        }
    } catch (_: HttpRequestTimeoutException) {
        Result.Failure(DomainFailure.Timeout)
    } catch (_: SocketTimeoutException) {
        Result.Failure(DomainFailure.Timeout)
    } catch (_: SerializationException) {
        Result.Failure(DomainFailure.MalformedResponse)
    } catch (_: IOException) {
        Result.Failure(DomainFailure.Offline)
    } catch (_: IllegalArgumentException) {
        Result.Failure(DomainFailure.MalformedResponse)
    }

    private fun initialResponse(
        request: InitialAnalysisRequest,
        category: MaterialCategory,
        confidence: Double,
    ) = InitialAnalysisResponse(
        analysisId = request.analysisId,
        prediction = CategoryPrediction(category, confidence, rankedCandidates(category, confidence)),
        requestedFields = AnalysisCatalog.schemaFor(category),
    )

    private fun rankedCandidates(category: MaterialCategory, confidence: Double): List<RankedCategoryPrediction> {
        if (confidence >= 0.80) return listOf(RankedCategoryPrediction(category, confidence))
        val alternatives = MaterialCategory.entries.filterNot { it == category }
        val count = if (confidence >= 0.50) 1 else 2
        return listOf(RankedCategoryPrediction(category, confidence)) + alternatives.take(count).mapIndexed { index, item ->
            RankedCategoryPrediction(item, (confidence - 0.15 - index * 0.15).coerceAtLeast(0.01))
        }
    }

    private inline fun <reified T> decodeReply(reply: String): T {
        val clean = reply.trim()
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start < 0 || end <= start) throw SerializationException("JSON object missing")
        return json.decodeFromString(clean.substring(start, end + 1))
    }

    private fun CompletedAnalysisResponse.enrichedWith(reply: CompletionReply): CompletedAnalysisResponse {
        val updatedScience = science.mapIndexed { index, finding ->
            val ai = reply.science.getOrNull(index) ?: return@mapIndexed finding
            finding.copy(
                title = ai.title.orFallback(finding.title),
                principle = ai.principle.orFallback(finding.principle),
                interpretation = ai.interpretation.orFallback(finding.interpretation),
                limitation = ai.limitation.orFallback(finding.limitation),
                recommendedVerification = ai.recommendedVerification.orFallback(finding.recommendedVerification),
            )
        }
        val updatedProducts = productOptions.mapIndexed { index, product ->
            val ai = reply.productOptions.getOrNull(index) ?: return@mapIndexed product
            product.copy(
                title = ai.title.orFallback(product.title),
                explanation = ai.explanation.orFallback(product.explanation),
            )
        }
        return copy(science = updatedScience, productOptions = updatedProducts)
    }

    private fun String?.orFallback(fallback: String): String = this?.trim()?.takeIf(String::isNotEmpty) ?: fallback
    private fun Double.toConfidence(): Double = (if (this > 1.0) this / 100.0 else this).coerceIn(0.01, 1.0)

    private fun String.toMaterialCategory(): MaterialCategory = when (trim().uppercase()) {
        "METAL", "LOGAM" -> MaterialCategory.METAL
        "CABLE", "KABEL" -> MaterialCategory.CABLE
        "PLASTIC", "PLASTIK" -> MaterialCategory.PLASTIC
        "WOOD", "KAYU" -> MaterialCategory.WOOD
        "TEXTILE", "TEKSTIL", "KAIN" -> MaterialCategory.TEXTILE
        "ELECTRONICS", "ELECTRONIC", "ELEKTRONIK" -> MaterialCategory.ELECTRONICS
        else -> throw IllegalArgumentException("Unsupported category")
    }

    private fun isSecureBaseUrl(): Boolean =
        baseUrl.trim().startsWith("https://") && baseUrl.trim().length > "https://".length

    private fun insecureUrlFailure(): Result.Failure =
        Result.Failure(DomainFailure.Validation(listOf("ReMaterial API URL must use HTTPS")))
}

fun interface AnalysisAuthorizationProvider {
    fun authorizationHeader(): String?

    data object None : AnalysisAuthorizationProvider {
        override fun authorizationHeader(): String? = null
    }
}

@Serializable
private data class ChatRequest(
    val message: String,
    val image: String? = null,
    val model: String,
    val stream: Boolean = false,
    @SerialName("reasoning_effort") val reasoningEffort: String = "low",
)

@Serializable
private data class ChatEnvelope(
    val reply: String,
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("message_id") val messageId: String? = null,
)

@Serializable
private data class ClassificationReply(
    val category: String,
    @SerialName("confidence_percent") val confidencePercent: Double,
)

@Serializable
private data class CompletionReply(
    val science: List<ScienceReply> = emptyList(),
    @SerialName("product_options") val productOptions: List<ProductReply> = emptyList(),
)

@Serializable
private data class ScienceReply(
    val title: String? = null,
    val principle: String? = null,
    val interpretation: String? = null,
    val limitation: String? = null,
    @SerialName("recommended_verification") val recommendedVerification: String? = null,
)

@Serializable
private data class ProductReply(
    val title: String? = null,
    val explanation: String? = null,
)
