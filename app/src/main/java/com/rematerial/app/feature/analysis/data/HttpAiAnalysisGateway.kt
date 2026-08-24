package com.rematerial.app.feature.analysis.data

import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.transport.AnalysisHttpDtos
import com.rematerial.app.feature.analysis.transport.AnalysisMappers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.SocketTimeoutException

class HttpAiAnalysisGateway(
    private val client: HttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = false; explicitNulls = false },
) : AiAnalysisGateway {
    override suspend fun start(request: InitialAnalysisRequest): Result<InitialAnalysisResponse> {
        if (!isSecureBaseUrl()) return Result.Failure(DomainFailure.Validation(listOf("ReMaterial API URL must use HTTPS")))
        return execute("/v1/analysis/initial", json.encodeToString(AnalysisHttpDtos.InitialRequestDto.serializer(), AnalysisMappers.toDto(request))) { body ->
            AnalysisMappers.fromDto(json.decodeFromString<AnalysisHttpDtos.InitialResponseDto>(body))
        }
    }

    override suspend fun complete(request: CompletedAnalysisRequest): Result<CompletedAnalysisResponse> {
        if (!isSecureBaseUrl()) return Result.Failure(DomainFailure.Validation(listOf("ReMaterial API URL must use HTTPS")))
        return execute("/v1/analysis/complete", json.encodeToString(AnalysisHttpDtos.CompletedRequestDto.serializer(), AnalysisMappers.toDto(request))) { body ->
            AnalysisMappers.fromDto(json.decodeFromString<AnalysisHttpDtos.CompletedResponseDto>(body))
        }
    }

    private suspend fun <Response> execute(
        path: String,
        requestBody: String,
        decode: (String) -> Result<Response>,
    ): Result<Response> = try {
        val response = client.post(baseUrl.trimEnd('/') + path) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(requestBody)
        }
        when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden -> Result.Failure(DomainFailure.Unauthorized)
            response.status == HttpStatusCode.RequestTimeout || response.status.value == 504 -> Result.Failure(DomainFailure.Timeout)
            response.status.value == 400 || response.status.value == 422 -> Result.Failure(DomainFailure.MalformedResponse)
            response.status.value !in 200..299 -> Result.Failure(DomainFailure.Unavailable)
            else -> decode(response.bodyAsText())
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

    private fun isSecureBaseUrl(): Boolean = baseUrl.trim().startsWith("https://") && baseUrl.trim().length > "https://".length
}
