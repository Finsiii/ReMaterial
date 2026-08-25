package com.rematerial.app.feature.analysis.transport

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
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.analysis.domain.CalculationInput
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.MathCalculation
import com.rematerial.app.feature.analysis.domain.ProductOption
import com.rematerial.app.feature.analysis.domain.ProductParameterRequirement
import com.rematerial.app.feature.analysis.domain.RequestedField
import com.rematerial.app.feature.analysis.domain.SafetyAssessment
import com.rematerial.app.feature.analysis.domain.ScoreComponents
import com.rematerial.app.feature.analysis.domain.ScienceFinding
import com.rematerial.app.feature.analysis.domain.RankedCategoryPrediction
import kotlinx.serialization.Serializable

object AnalysisHttpDtos {
    @Serializable
    data class InitialResponseDto(
        val analysisId: String,
        val category: String,
        val confidence: Double,
        val predictions: List<CategoryPredictionDto> = emptyList(),
        val requestedFields: List<RequestedFieldDto> = emptyList(),
    )

    @Serializable
    data class CategoryPredictionDto(val category: String, val confidence: Double)

    @Serializable
    data class RequestedFieldDto(
        val id: String,
        val label: String,
        val type: String,
        val unit: String? = null,
        val required: Boolean,
        val choices: List<String> = emptyList(),
        val minimum: Double? = null,
        val maximum: Double? = null,
        val maximumLength: Int? = null,
        val allowUnavailable: Boolean = !required,
    )

    @Serializable
    data class InitialRequestDto(
        val analysisId: String,
        val mediaId: String? = null,
        val manualCategory: String? = null,
    )

    @Serializable
    data class CompletedRequestDto(
        val analysisId: String,
        val category: String,
        val observations: List<ObservationDto>,
    )

    @Serializable
    data class ObservationDto(
        val fieldId: String,
        val valueKind: String? = null,
        val textValue: String? = null,
        val decimalValue: Double? = null,
        val wholeValue: Int? = null,
        val booleanValue: Boolean? = null,
        val choiceValue: String? = null,
        val unit: String? = null,
        val source: String,
        val availability: String,
    )

    @Serializable
    data class CompletedResponseDto(
        val analysisId: String,
        val category: String,
        val confidence: Double,
        val observations: List<ObservationDto>,
        val science: List<ScienceFindingDto>,
        val mathematics: List<MathCalculationDto>,
        val safety: SafetyDto,
        val productOptions: List<ProductOptionDto>,
    )

    @Serializable
    data class ScienceFindingDto(
        val findingId: String,
        val title: String,
        val observationRefs: List<String>,
        val principle: String,
        val sourceRefs: List<String>,
        val interpretation: String,
        val limitation: String,
        val recommendedVerification: String,
    )

    @Serializable
    data class MathInputDto(val name: String, val value: Double, val unit: String, val observationRef: String? = null, val evidenceSource: String? = null)

    @Serializable
    data class MathCalculationDto(
        val calculationId: String,
        val inputs: List<MathInputDto>,
        val formulaId: String,
        val formulaExpression: String,
        val result: Double,
        val unit: String,
        val limitations: String,
    )

    @Serializable
    data class SafetyDto(val outcome: String, val reasons: List<String>)

    @Serializable
    data class ScoreComponentsDto(
        val propertyFit: Double,
        val materialSufficiency: Double,
        val economicPotential: Double,
        val residueReduction: Double,
    )

    @Serializable
    data class ProductOptionDto(
        val optionId: String,
        val title: String,
        val explanation: String,
        val requiredMaterial: String,
        val requiredParameterIds: List<String>,
        val parameterRequirements: List<ProductParameterRequirementDto>,
        val minimumQuantity: Double,
        val minimumUnit: String,
        val estimatedUsedQuantity: Double,
        val estimatedUsedUnit: String,
        val requiredToolIds: List<String>,
        val requiredSkillIds: List<String>,
        val prerequisiteFieldIds: List<String> = emptyList(),
        val scoreFormulaId: String,
        val scoreFormulaExpression: String,
        val scoreEvidenceInputs: List<MathInputDto>,
        val scoreComponents: ScoreComponentsDto,
        val provisionalProductScore: Double,
    )

    @Serializable
    data class ProductParameterRequirementDto(
        val fieldId: String,
        val acceptedChoices: List<String> = emptyList(),
        val minimumNumeric: Double? = null,
        val maximumNumeric: Double? = null,
        val anyProvided: Boolean = false,
    )

    val initialResponseSerializer = InitialResponseDto.serializer()
}

object AnalysisMappers {
    fun toDto(response: InitialAnalysisResponse): AnalysisHttpDtos.InitialResponseDto = AnalysisHttpDtos.InitialResponseDto(
        analysisId = response.analysisId.value,
        category = response.prediction.category.name,
        confidence = response.prediction.confidence,
        predictions = response.prediction.rankedCandidates.map { AnalysisHttpDtos.CategoryPredictionDto(it.category.name, it.confidence) },
        requestedFields = response.requestedFields.map { field ->
            AnalysisHttpDtos.RequestedFieldDto(
                field.id.value, field.label, field.type.name, field.unit?.name, field.required,
                field.choices, field.minimum, field.maximum, field.maximumLength, field.allowUnavailable,
            )
        },
    )

    fun toDto(request: InitialAnalysisRequest): AnalysisHttpDtos.InitialRequestDto = AnalysisHttpDtos.InitialRequestDto(
        request.analysisId.value,
        request.photo?.mediaId,
        request.manualCategory?.name,
    )

    fun toDto(request: CompletedAnalysisRequest): AnalysisHttpDtos.CompletedRequestDto = AnalysisHttpDtos.CompletedRequestDto(
        request.analysisId.value,
        request.category.name,
        request.observations.map(::toDto),
    )

    fun toDto(observation: Observation): AnalysisHttpDtos.ObservationDto {
        val value = observation.value
        return AnalysisHttpDtos.ObservationDto(
            fieldId = observation.fieldId.value,
            valueKind = when (value) {
                is InspectionValue.Text -> "text"
                is InspectionValue.Decimal -> "decimal"
                is InspectionValue.Whole -> "whole"
                is InspectionValue.BooleanValue -> "boolean"
                is InspectionValue.Choice -> "choice"
                null -> null
            },
            textValue = (value as? InspectionValue.Text)?.value,
            decimalValue = (value as? InspectionValue.Decimal)?.value,
            wholeValue = (value as? InspectionValue.Whole)?.value,
            booleanValue = (value as? InspectionValue.BooleanValue)?.value,
            choiceValue = (value as? InspectionValue.Choice)?.value,
            unit = observation.unit?.name,
            source = observation.source.name,
            availability = observation.availability.name,
        )
    }

    fun fromDto(dto: AnalysisHttpDtos.InitialResponseDto): Result<InitialAnalysisResponse> = try {
        val category = MaterialCategory.valueOf(dto.category)
        val fields = dto.requestedFields.map {
            RequestedField(
                id = FieldId(it.id), label = it.label, type = InspectionFieldType.valueOf(it.type),
                unit = it.unit?.let(UnitCode::valueOf), required = it.required, choices = it.choices,
                minimum = it.minimum, maximum = it.maximum, maximumLength = it.maximumLength,
                allowUnavailable = it.allowUnavailable,
            )
        }
        val response = InitialAnalysisResponse(
            AnalysisId(dto.analysisId), CategoryPrediction(
                category,
                dto.confidence,
                dto.predictions.ifEmpty { listOf(AnalysisHttpDtos.CategoryPredictionDto(dto.category, dto.confidence)) }
                    .map { RankedCategoryPrediction(MaterialCategory.valueOf(it.category), it.confidence) },
            ), fields,
        )
        when (val validation = AnalysisValidator.validate(response)) {
            is Result.Success -> Result.Success(response)
            is Result.Failure -> Result.Failure(DomainFailure.UnsupportedSchema)
        }
    } catch (_: IllegalArgumentException) {
        Result.Failure(DomainFailure.UnsupportedSchema)
    }

    fun fromDto(dto: AnalysisHttpDtos.CompletedResponseDto): Result<CompletedAnalysisResponse> = try {
        val response = CompletedAnalysisResponse(
            AnalysisId(dto.analysisId), MaterialCategory.valueOf(dto.category), dto.confidence,
            dto.observations.map(::fromDto).fold(emptyList()) { acc, result -> acc + result },
            dto.science.map { ScienceFinding(FindingId(it.findingId), it.title, it.observationRefs.map(::FieldId), it.principle, it.sourceRefs.map(::SourceId), it.interpretation, it.limitation, it.recommendedVerification) },
            dto.mathematics.map { MathCalculation(it.calculationId, it.inputs.map { input -> CalculationInput(input.name, input.value, UnitCode.valueOf(input.unit), input.observationRef?.let(::FieldId), input.evidenceSource) }, it.formulaId, it.formulaExpression, it.result, UnitCode.valueOf(it.unit), it.limitations) },
            SafetyAssessment(SafetyOutcome.valueOf(dto.safety.outcome), dto.safety.reasons),
            dto.productOptions.map { option ->
                ProductOption(
                    optionId = ProductOptionId(option.optionId),
                    title = option.title,
                    explanation = option.explanation,
                    requiredMaterial = option.requiredMaterial,
                    requiredParameterIds = option.requiredParameterIds.map(::FieldId),
                    parameterRequirements = option.parameterRequirements.map { requirement ->
                        ProductParameterRequirement(
                            FieldId(requirement.fieldId), requirement.acceptedChoices,
                            requirement.minimumNumeric, requirement.maximumNumeric, requirement.anyProvided,
                        )
                    },
                    minimumQuantity = option.minimumQuantity,
                    minimumUnit = UnitCode.valueOf(option.minimumUnit),
                    estimatedUsedQuantity = option.estimatedUsedQuantity,
                    estimatedUsedUnit = UnitCode.valueOf(option.estimatedUsedUnit),
                    requiredToolIds = option.requiredToolIds,
                    requiredSkillIds = option.requiredSkillIds,
                    prerequisiteFieldIds = option.prerequisiteFieldIds.map(::FieldId),
                    scoreFormulaId = option.scoreFormulaId,
                    scoreFormulaExpression = option.scoreFormulaExpression,
                    scoreEvidenceInputs = option.scoreEvidenceInputs.map { CalculationInput(it.name, it.value, UnitCode.valueOf(it.unit), it.observationRef?.let(::FieldId), it.evidenceSource) },
                    scoreComponents = ScoreComponents(option.scoreComponents.propertyFit, option.scoreComponents.materialSufficiency, option.scoreComponents.economicPotential, option.scoreComponents.residueReduction),
                    provisionalProductScore = option.provisionalProductScore,
                )
            },
        )
        when (AnalysisValidator.validate(response)) {
            is Result.Success -> Result.Success(response)
            is Result.Failure -> Result.Failure(DomainFailure.UnsupportedSchema)
        }
    } catch (_: IllegalArgumentException) {
        Result.Failure(DomainFailure.UnsupportedSchema)
    }

    fun fromDto(dto: AnalysisHttpDtos.ObservationDto): Observation {
        val value = when (dto.valueKind) {
            "text" -> dto.textValue?.let(InspectionValue::Text)
            "decimal" -> dto.decimalValue?.let(InspectionValue::Decimal)
            "whole" -> dto.wholeValue?.let(InspectionValue::Whole)
            "boolean" -> dto.booleanValue?.let(InspectionValue::BooleanValue)
            "choice" -> dto.choiceValue?.let(InspectionValue::Choice)
            else -> null
        }
        return Observation(FieldId(dto.fieldId), value, dto.unit?.let(UnitCode::valueOf), ValueSource.valueOf(dto.source), Availability.valueOf(dto.availability))
    }
}
