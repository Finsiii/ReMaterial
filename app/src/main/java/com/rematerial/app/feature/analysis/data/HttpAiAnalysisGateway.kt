package com.rematerial.app.feature.analysis.data

import com.rematerial.app.core.media.MediaPayloadReader
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisCatalog
import com.rematerial.app.feature.analysis.domain.AnalysisProgress
import com.rematerial.app.feature.analysis.domain.AnalysisProgressStage
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.EvidenceLevel
import com.rematerial.app.feature.analysis.domain.InferredMaterial
import com.rematerial.app.feature.analysis.domain.MaterialComponent
import com.rematerial.app.feature.analysis.domain.ObjectAnalysis
import com.rematerial.app.feature.analysis.domain.ObjectState
import com.rematerial.app.feature.analysis.domain.RankedCategoryPrediction
import com.rematerial.app.feature.analysis.domain.ReuseStrategy
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
    private val visionModel: String = "gpt-5-6-instant",
    private val refinementModel: String = "gpt-5-6",
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val mediaPayloadReader: MediaPayloadReader? = null,
    private val authorizationProvider: AnalysisAuthorizationProvider = AnalysisAuthorizationProvider.None,
) : AiAnalysisGateway {
    override suspend fun start(request: InitialAnalysisRequest, onProgress: suspend (AnalysisProgress) -> Unit): Result<InitialAnalysisResponse> {
        if (!isSecureBaseUrl()) return insecureUrlFailure()
        request.manualCategory?.let { category ->
            return Result.Success(initialResponse(request, category, confidence = 1.0, listOf("quantity", "condition"), mapOf("quantity" to "1", "condition" to "unknown", "contamination" to "unknown"), request.conversationId, request.parentMessageId, request.objectAnalysis))
        }

        if (request.photos.size !in 3..6) return Result.Failure(DomainFailure.UnsupportedImage)
        val reader = mediaPayloadReader ?: return Result.Failure(DomainFailure.Unavailable)
        val images = mutableListOf<String>()
        request.photos.forEachIndexed { index, photo ->
            onProgress(AnalysisProgress(AnalysisProgressStage.PREPARING_PHOTOS, index.toFloat() / request.photos.size, "Menyiapkan foto ${index + 1} dari ${request.photos.size}"))
            when (val media = reader.read(photo)) {
                is Result.Success -> images += Base64.getEncoder().encodeToString(media.value)
                is Result.Failure -> return media
            }
        }
        onProgress(AnalysisProgress(AnalysisProgressStage.UPLOADING_PHOTOS, 0f, "Mengirim foto ke server"))
        onProgress(AnalysisProgress(AnalysisProgressStage.UPLOADING_PHOTOS, 1f, "Foto siap dikirim"))
        onProgress(AnalysisProgress(AnalysisProgressStage.ANALYZING_OBJECT, null, "AI mengenali objek dan komponennya"))
        val turn = when (
            val response = ask(
                message = ReMaterialAiPrompts.initial(request.analysisId.value),
                images = images,
                model = visionModel,
            )
        ) {
            is Result.Success -> response.value
            is Result.Failure -> return response
        }
        onProgress(AnalysisProgress(AnalysisProgressStage.PREPARING_RESULT, 1f, "Menyiapkan hasil analisis"))

        return try {
            val classification = decodeReply<ClassificationReply>(turn.reply)
            val category = classification.category.toMaterialCategory()
            val confidence = classification.confidencePercent.toConfidence()
            val schema = AnalysisCatalog.schemaFor(category)
            val suggested = mapOf(
                "quantity" to classification.quantityEstimate.toQuantity(category),
                "condition" to classification.condition.takeIf { it in setOf("good", "worn", "damaged", "unknown") }.orEmpty().ifBlank { "unknown" },
                "contamination" to classification.contamination.takeIf { it in setOf("none", "low", "unknown", "suspected_hazardous") }.orEmpty().ifBlank { "unknown" },
            )
            val followUps = classification.followUpIds.distinct().filter { id -> schema.any { it.id.value == id } }.take(2)
            val objectAnalysis = classification.toObjectAnalysis()
            val response = initialResponse(request, category, confidence, followUps, suggested, turn.conversationId, turn.messageId, objectAnalysis)
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

    override suspend fun complete(request: CompletedAnalysisRequest, onProgress: suspend (AnalysisProgress) -> Unit): Result<CompletedAnalysisResponse> {
        if (!isSecureBaseUrl()) return insecureUrlFailure()
        onProgress(AnalysisProgress(AnalysisProgressStage.ANALYZING_OBJECT, null, "AI menyempurnakan rekomendasi"))
        val observationsJson = json.encodeToString(request.observations.map(AnalysisMappers::toDto))
        val turn = when (val response = ask(
            message = ReMaterialAiPrompts.complete(request.category, observationsJson, request.objectAnalysis),
            model = refinementModel,
            conversationId = request.conversationId,
            parentMessageId = request.parentMessageId,
        )) {
            is Result.Success -> response.value
            is Result.Failure -> return response
        }
        onProgress(AnalysisProgress(AnalysisProgressStage.PREPARING_RESULT, 1f, "Menyiapkan hasil analisis"))

        return try {
            val enrichment = decodeReply<CompletionReply>(turn.reply)
            val completed = AnalysisFixtures.completedFor(request).enrichedWith(enrichment).copy(objectAnalysis = request.objectAnalysis)
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

    private suspend fun ask(
        message: String,
        image: String? = null,
        images: List<String> = emptyList(),
        model: String,
        conversationId: String? = null,
        parentMessageId: String? = null,
    ): Result<ChatTurn> = try {
        val response = client.post(baseUrl.trimEnd('/') + "/v1/chat") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            authorizationProvider.authorizationHeader()?.let { header(HttpHeaders.Authorization, it) }
            setBody(json.encodeToString(ChatRequest(message, image, images, model, conversationId = conversationId, parentMessageId = parentMessageId)))
        }
        when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden ->
                Result.Failure(DomainFailure.Unauthorized)
            response.status == HttpStatusCode.RequestTimeout || response.status.value == 504 ->
                Result.Failure(DomainFailure.Timeout)
            response.status.value == 400 || response.status.value == 413 || response.status.value == 415 || response.status.value == 422 ->
                Result.Failure(if (image != null || images.isNotEmpty()) DomainFailure.UnsupportedImage else DomainFailure.MalformedResponse)
            response.status.value !in 200..299 -> Result.Failure(DomainFailure.Unavailable)
            else -> {
                val envelope = json.decodeFromString<ChatEnvelope>(response.bodyAsText())
                if (envelope.reply.isBlank()) Result.Failure(DomainFailure.MalformedResponse)
                else Result.Success(ChatTurn(envelope.reply, envelope.conversationId, envelope.messageId))
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
        followUpIds: List<String>,
        suggestedValues: Map<String, String>,
        conversationId: String?,
        messageId: String?,
        objectAnalysis: ObjectAnalysis?,
    ) = InitialAnalysisResponse(
        analysisId = request.analysisId,
        prediction = CategoryPrediction(category, confidence, rankedCandidates(category, confidence)),
        requestedFields = AnalysisCatalog.schemaFor(category).filter { it.id.value in followUpIds },
        suggestedValues = suggestedValues,
        conversationId = conversationId,
        messageId = messageId,
        objectAnalysis = objectAnalysis,
    )

    private fun ClassificationReply.toObjectAnalysis(): ObjectAnalysis = ObjectAnalysis(
        objectName = objectName.trim().ifBlank { "Material bekas" },
        state = objectState.toEnumOr(ObjectState.UNKNOWN),
        visibleComponents = visibleComponents.mapNotNull { component ->
            val part = component.part.trim()
            val material = component.material.trim()
            if (part.isBlank() || material.isBlank()) null else MaterialComponent(part, material, component.evidence.toEnumOr(EvidenceLevel.VISIBLE))
        },
        inferredHiddenMaterials = inferredHiddenMaterials.mapNotNull { inferred ->
            val material = inferred.material.trim()
            val reason = inferred.reason.trim()
            if (material.isBlank() || reason.isBlank()) null else InferredMaterial(material, reason)
        },
        primaryStrategy = primaryStrategy.toEnumOr(ReuseStrategy.REPURPOSE),
    )

    private inline fun <reified T : Enum<T>> String.toEnumOr(fallback: T): T =
        enumValues<T>().firstOrNull { it.name == trim().uppercase() } ?: fallback

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
    private fun Double.toQuantity(category: MaterialCategory): String {
        val safe = takeIf { isFinite() && this > 0.0 } ?: 1.0
        return if (AnalysisCatalog.contractFor(category, com.rematerial.app.core.model.FieldId("quantity"))?.unit == com.rematerial.app.core.model.UnitCode.PCS) kotlin.math.round(safe).coerceAtLeast(1.0).toInt().toString() else "%.2f".format(java.util.Locale.US, safe)
    }

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
    val images: List<String> = emptyList(),
    val model: String,
    val stream: Boolean = false,
    @SerialName("reasoning_effort") val reasoningEffort: String = "low",
    @SerialName("conversation_id") val conversationId: String? = null,
    @SerialName("parent_message_id") val parentMessageId: String? = null,
)

private data class ChatTurn(val reply: String, val conversationId: String?, val messageId: String?)

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
    @SerialName("quantity_estimate") val quantityEstimate: Double = 1.0,
    val condition: String = "unknown",
    val contamination: String = "unknown",
    @SerialName("follow_up_ids") val followUpIds: List<String> = emptyList(),
    @SerialName("object_name") val objectName: String = "Material bekas",
    @SerialName("object_state") val objectState: String = "UNKNOWN",
    @SerialName("visible_components") val visibleComponents: List<ComponentReply> = emptyList(),
    @SerialName("inferred_hidden_materials") val inferredHiddenMaterials: List<InferredMaterialReply> = emptyList(),
    @SerialName("primary_strategy") val primaryStrategy: String = "REPURPOSE",
)

@Serializable
private data class ComponentReply(val part: String = "", val material: String = "", val evidence: String = "VISIBLE")

@Serializable
private data class InferredMaterialReply(val material: String = "", val reason: String = "")

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
