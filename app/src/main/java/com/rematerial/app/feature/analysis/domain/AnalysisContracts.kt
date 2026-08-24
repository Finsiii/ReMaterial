package com.rematerial.app.feature.analysis.domain

import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.Availability
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.FindingId
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.core.model.SourceId
import com.rematerial.app.core.model.UnitCode
import com.rematerial.app.core.model.ValueSource
import kotlinx.serialization.Serializable

@Serializable
data class PhotoReference(
    val privatePath: String,
    val contentType: String,
    val sizeBytes: Long,
) {
    init { require(privatePath.isNotBlank() && sizeBytes > 0) }
}

@Serializable
data class InitialAnalysisRequest(
    val analysisId: AnalysisId,
    val photo: PhotoReference? = null,
    val manualCategory: MaterialCategory? = null,
)

@Serializable
data class CategoryPrediction(
    val category: MaterialCategory,
    val confidence: Double,
    val alternatives: List<MaterialCategory> = emptyList(),
) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) }
}

@Serializable
enum class InspectionFieldType { TEXT, DECIMAL, WHOLE, BOOLEAN, CHOICE }

@Serializable
data class RequestedField(
    val id: FieldId,
    val label: String,
    val type: InspectionFieldType,
    val unit: UnitCode? = null,
    val required: Boolean,
    val choices: List<String> = emptyList(),
)

@Serializable
data class InitialAnalysisResponse(
    val analysisId: AnalysisId,
    val prediction: CategoryPrediction,
    val requestedFields: List<RequestedField>,
)

@Serializable
data class CompletedAnalysisRequest(
    val analysisId: AnalysisId,
    val category: MaterialCategory,
    val observations: List<Observation>,
)

@Serializable
data class ScienceFinding(
    val findingId: FindingId,
    val title: String,
    val observationRefs: List<FieldId>,
    val principle: String,
    val sourceRefs: List<SourceId>,
    val interpretation: String,
    val limitation: String,
    val recommendedVerification: String,
)

@Serializable
data class CalculationInput(
    val name: String,
    val value: Double,
    val unit: UnitCode,
)

@Serializable
data class MathCalculation(
    val calculationId: String,
    val inputs: List<CalculationInput>,
    val formulaId: String,
    val formulaExpression: String,
    val result: Double,
    val unit: UnitCode,
    val limitations: String,
)

@Serializable
data class SafetyAssessment(
    val outcome: SafetyOutcome,
    val reasons: List<String>,
)

@Serializable
data class ScoreComponents(
    val propertyFit: Double,
    val materialSufficiency: Double,
    val economicPotential: Double,
    val residueReduction: Double,
)

@Serializable
data class ProductOption(
    val optionId: ProductOptionId,
    val title: String,
    val explanation: String,
    val requiredMaterial: String,
    val requiredParameterIds: List<FieldId>,
    val minimumQuantity: Double,
    val minimumUnit: UnitCode,
    val requiredToolIds: List<String>,
    val requiredSkillIds: List<String>,
    val prerequisiteFieldIds: List<FieldId> = emptyList(),
    val scoreComponents: ScoreComponents,
    val provisionalProductScore: Double,
)

@Serializable
data class CompletedAnalysisResponse(
    val analysisId: AnalysisId,
    val category: MaterialCategory,
    val confidence: Double,
    val observations: List<Observation>,
    val science: List<ScienceFinding>,
    val mathematics: List<MathCalculation>,
    val safety: SafetyAssessment,
    val productOptions: List<ProductOption>,
) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) }
}

interface AiAnalysisGateway {
    suspend fun start(request: InitialAnalysisRequest): Result<InitialAnalysisResponse>
    suspend fun complete(
        request: CompletedAnalysisRequest,
    ): Result<CompletedAnalysisResponse>
}

object AnalysisCatalog {
    val commonFields = setOf(
        FieldId("source_location"), FieldId("quantity"), FieldId("condition"),
        FieldId("contamination"), FieldId("notes"),
    )
    val categoryFields = mapOf(
        MaterialCategory.METAL to setOf(FieldId("sharp_edges"), FieldId("corrosion"), FieldId("coating_oil")),
        MaterialCategory.CABLE to setOf(FieldId("powered"), FieldId("exposed_conductor"), FieldId("insulation")),
        MaterialCategory.PLASTIC to setOf(FieldId("resin_code"), FieldId("chemical_contact"), FieldId("melted_evidence")),
        MaterialCategory.WOOD to setOf(FieldId("treatment"), FieldId("rot_mold"), FieldId("embedded_metal")),
        MaterialCategory.TEXTILE to setOf(FieldId("wet_mold"), FieldId("oil_chemical"), FieldId("fiber_label")),
        MaterialCategory.ELECTRONICS to setOf(FieldId("battery"), FieldId("battery_damage"), FieldId("burn_marks"), FieldId("powered")),
    )
    val sourceIds = setOf(
        SourceId("rematerial-material-procedure"), SourceId("school-material-safety"),
    )
    fun recognized(category: MaterialCategory): Set<FieldId> = commonFields + categoryFields.getValue(category)
}

object AnalysisValidator {
    fun validate(response: InitialAnalysisResponse): Result<Unit> {
        val ids = response.requestedFields.map { it.id }
        val unknown = ids.filterNot { it in AnalysisCatalog.recognized(response.prediction.category) }
        return if (unknown.isNotEmpty() || ids.size != ids.toSet().size) {
            Result.Failure(DomainFailure.Validation(listOf("Unknown or duplicate inspection field: $unknown")))
        } else {
            Result.Success(Unit)
        }
    }

    fun validate(response: CompletedAnalysisResponse): Result<Unit> {
        val violations = mutableListOf<String>()
        if (!response.confidence.isFinite() || response.confidence !in 0.0..1.0) violations += "Invalid confidence"
        val observations = response.observations.associateBy { it.fieldId }
        response.observations.forEach { observation ->
            if (observation.fieldId !in AnalysisCatalog.recognized(response.category)) violations += "Unknown field ${observation.fieldId.value}"
            if (observation.value is InspectionValue.Decimal && !observation.value.value.isFinite()) violations += "Non-finite observation"
            if (observation.availability == Availability.PROVIDED && observation.value == null) violations += "Provided observation has no value"
            if (observation.availability == Availability.NOT_AVAILABLE && observation.value != null) violations += "Unavailable observation cannot have a value"
        }
        val quantity = observations[FieldId("quantity")]
        if (quantity?.availability == Availability.PROVIDED) {
            val value = (quantity.value as? InspectionValue.Decimal)?.value
            if (value == null || !value.isFinite() || value !in 0.01..100_000.0) violations += "Quantity must be between 0.01 and 100000"
            if (quantity.unit !in setOf(UnitCode.KG, UnitCode.G, UnitCode.M, UnitCode.M2, UnitCode.PCS)) violations += "Quantity unit invalid"
        }
        val location = observations[FieldId("source_location")]?.value as? InspectionValue.Text
        if (location != null && location.value.length !in 1..120) violations += "Source location length invalid"
        val notes = observations[FieldId("notes")]?.value as? InspectionValue.Text
        if (notes != null && notes.value.length > 500) violations += "Notes length invalid"
        response.science.forEach { finding ->
            if (finding.findingId.value.isBlank() || finding.title.isBlank()) violations += "Science identity missing"
            if (finding.observationRefs.isEmpty() || finding.observationRefs.any { it !in observations }) violations += "Science observation reference unresolved"
            if (finding.sourceRefs.isEmpty() || finding.sourceRefs.any { it !in AnalysisCatalog.sourceIds }) violations += "Science source reference unresolved"
            if (finding.principle.isBlank()) violations += "Science principle missing"
            if (finding.interpretation.isBlank()) violations += "Science interpretation missing"
            if (finding.limitation.isBlank()) violations += "Science limitation missing"
            if (finding.recommendedVerification.isBlank()) violations += "Science verification missing"
        }
        response.mathematics.forEach { calculation ->
            if (calculation.inputs.isEmpty() || calculation.inputs.any { !it.value.isFinite() }) violations += "Math input evidence missing"
            if (calculation.formulaId.isBlank() || calculation.formulaExpression.isBlank()) violations += "Math formula missing"
            if (!calculation.result.isFinite()) violations += "Math result invalid"
            if (calculation.unit == UnitCode.NONE) violations += "Math result unit missing"
            if (calculation.limitations.isBlank()) violations += "Math limitation missing"
        }
        if (response.safety.outcome != SafetyOutcome.ALLOW && response.safety.reasons.isEmpty()) violations += "Safety reasons missing"
        if (response.safety.outcome == SafetyOutcome.BLOCK && response.productOptions.isNotEmpty()) violations += "Block cannot contain product options"
        response.productOptions.forEach { option ->
            if (option.requiredMaterial.isBlank()) violations += "Required material missing"
            if (option.requiredParameterIds.isEmpty()) violations += "Property-fit denominator is zero"
            if (option.minimumQuantity <= 0.0 || !option.minimumQuantity.isFinite()) violations += "Minimum quantity invalid"
            if (option.requiredToolIds.any(String::isBlank) || option.requiredToolIds.isEmpty()) violations += "Required tools missing"
            if (option.requiredSkillIds.any(String::isBlank) || option.requiredSkillIds.isEmpty()) violations += "Required skills missing"
            if (option.explanation.isBlank()) violations += "Product explanation missing"
            val components = listOf(option.scoreComponents.propertyFit, option.scoreComponents.materialSufficiency, option.scoreComponents.economicPotential, option.scoreComponents.residueReduction)
            if (components.any { !it.isFinite() || it !in 0.0..100.0 }) violations += "Score component invalid"
            if (!option.provisionalProductScore.isFinite() || option.provisionalProductScore !in 0.0..100.0) violations += "Provisional score invalid"
            if (response.safety.outcome == SafetyOutcome.CAUTION && option.prerequisiteFieldIds.any { observations[it]?.availability != Availability.PROVIDED }) violations += "Caution prerequisite unmet"
        }
        return if (violations.isEmpty()) Result.Success(Unit) else Result.Failure(DomainFailure.Validation(violations))
    }
}
