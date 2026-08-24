package com.rematerial.app.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AccountId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
@JvmInline
value class AnalysisId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
@JvmInline
value class FieldId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
@JvmInline
value class FindingId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
@JvmInline
value class SourceId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
@JvmInline
value class ProductOptionId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
enum class MaterialCategory(val displayName: String) {
    METAL("Metal"),
    CABLE("Kabel"),
    PLASTIC("Plastik"),
    WOOD("Kayu"),
    TEXTILE("Tekstil"),
    ELECTRONICS("Elektronik"),
}

@Serializable
enum class UnitCode {
    KG, G, M, CM, MM, M2, PERCENT, PCS, L, NONE,
}

@Serializable
enum class ValueSource { USER, DEVICE, AI_API }

@Serializable
enum class Availability { PROVIDED, NOT_AVAILABLE }

@Serializable
enum class SafetyOutcome { ALLOW, CAUTION, BLOCK }

@Serializable
sealed interface InspectionValue {
    @Serializable
    data class Text(val value: String) : InspectionValue

    @Serializable
    data class Decimal(val value: Double) : InspectionValue

    @Serializable
    data class Whole(val value: Int) : InspectionValue

    @Serializable
    data class BooleanValue(val value: Boolean) : InspectionValue

    @Serializable
    data class Choice(val value: String) : InspectionValue
}

@Serializable
data class Observation(
    val fieldId: FieldId,
    val value: InspectionValue? = null,
    val unit: UnitCode? = null,
    val source: ValueSource = ValueSource.USER,
    val availability: Availability = Availability.PROVIDED,
)

sealed interface Result<out T> {
    data class Success<T>(val value: T) : Result<T>
    data class Failure(val error: DomainFailure) : Result<Nothing>
}

sealed interface DomainFailure {
    data object Offline : DomainFailure
    data object Timeout : DomainFailure
    data object Unauthorized : DomainFailure
    data object MalformedResponse : DomainFailure
    data object UnsupportedSchema : DomainFailure
    data object UnsupportedImage : DomainFailure
    data object PermissionDenied : DomainFailure
    data object Unavailable : DomainFailure
    data class Validation(val violations: List<String>) : DomainFailure
}

fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> value
    is Result.Failure -> error("Domain failure: $error")
}
