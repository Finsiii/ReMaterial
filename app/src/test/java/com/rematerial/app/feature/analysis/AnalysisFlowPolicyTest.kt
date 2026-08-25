package com.rematerial.app.feature.analysis

import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.data.AnalysisFixtures
import com.rematerial.app.feature.analysis.domain.AnalysisConfirmation
import com.rematerial.app.feature.analysis.domain.AnalysisInputValidator
import com.rematerial.app.feature.analysis.domain.AnalysisResponseValidator
import com.rematerial.app.feature.analysis.domain.AnalysisConfirmationPolicy
import com.rematerial.app.feature.analysis.domain.FieldAnswer
import com.rematerial.app.feature.analysis.domain.ProductScorePolicy
import com.rematerial.app.feature.analysis.domain.ScoreComponents
import com.rematerial.app.feature.analysis.presentation.AnalysisMotionDirection
import com.rematerial.app.feature.analysis.presentation.AnalysisMotionPolicy
import com.rematerial.app.feature.analysis.presentation.AnalysisStep
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.SafetyPolicy
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.SafetyOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFlowPolicyTest {
    @Test
    fun confidenceThresholdsRequireTheRightConfirmation() {
        assertEquals(AnalysisConfirmation.CORRECTION_OPTIONAL, AnalysisConfirmation.from(.80))
        assertEquals(AnalysisConfirmation.ALTERNATIVE_REQUIRED, AnalysisConfirmation.from(.79))
        assertEquals(AnalysisConfirmation.MANUAL_REQUIRED, AnalysisConfirmation.from(.49))
    }

    @Test
    fun evenHighConfidenceRequiresAnExplicitUserConfirmation() {
        assertTrue(!AnalysisConfirmationPolicy.canContinue(schemaReady = true, selected = MaterialCategory.METAL, explicitlyConfirmed = false))
        assertTrue(AnalysisConfirmationPolicy.canContinue(schemaReady = true, selected = MaterialCategory.METAL, explicitlyConfirmed = true))
    }

    @Test fun lockedScoreFormulaNormalizesTheWeightedComponents() {
        val components = ScoreComponents(80.0, 60.0, 40.0, 20.0)
        assertEquals(58.75, ProductScorePolicy.calculate(components), .001)
    }

    @Test fun savedIdeaMotionUsesExplicitNavigationIntent() {
        assertEquals(AnalysisMotionDirection.FORWARD, AnalysisMotionPolicy.direction(AnalysisStep.SCAN, AnalysisStep.IDEAS))
        assertEquals(AnalysisMotionDirection.FORWARD, AnalysisMotionPolicy.direction(AnalysisStep.IDEAS, AnalysisStep.RESULT))
        assertEquals(AnalysisMotionDirection.BACKWARD, AnalysisMotionPolicy.backDirection(AnalysisStep.RESULT, AnalysisStep.IDEAS))
    }

    @Test
    fun responseMustMatchActiveAnalysisAndSelectedCategory() {
        val fixture = AnalysisFixtures.woodHigh().completed
        assertTrue(
            AnalysisResponseValidator.completed(
                expectedAnalysisId = AnalysisId("different"),
                expectedCategory = MaterialCategory.WOOD,
                response = fixture,
            ) is Result.Failure,
        )
        assertTrue(
            AnalysisResponseValidator.completed(
                expectedAnalysisId = fixture.analysisId,
                expectedCategory = MaterialCategory.METAL,
                response = fixture,
            ) is Result.Failure,
        )
    }

    @Test
    fun optionalUnavailableIsValidButRequiredAndQuantityBoundsAreEnforced() {
        val fields = AnalysisFixtures.metalHigh().initial.requestedFields
        val unavailableOptional = fields.associate { it.id.value to FieldAnswer.Unavailable }
        val invalid = AnalysisInputValidator.validate(fields, unavailableOptional)
        assertTrue(invalid.fieldErrors.isNotEmpty())

        val valid = fields.associate { field ->
            field.id.value to when {
                field.id.value == "quantity" -> FieldAnswer.Value("2.5")
                field.id.value == "source_location" -> FieldAnswer.Value("Workshop sekolah")
                !field.required -> FieldAnswer.Unavailable
                field.type == InspectionFieldType.CHOICE -> FieldAnswer.Value(field.choices.first())
                field.type == InspectionFieldType.BOOLEAN -> FieldAnswer.Value("false")
                field.type == InspectionFieldType.DECIMAL -> FieldAnswer.Value((field.minimum ?: 1.0).toString())
                else -> FieldAnswer.Value("Terlihat sesuai")
            }
        }
        assertTrue(AnalysisInputValidator.validate(fields, valid).isValid)

        val tooLarge = valid + ("quantity" to FieldAnswer.Value("100000.01"))
        assertEquals("Masukkan nilai 0,01 sampai 100.000.", AnalysisInputValidator.validate(fields, tooLarge).fieldErrors["quantity"])
    }

    @Test
    fun safetyPolicyUsesTheExactUnknownAndMetalBoundaries() {
        val base = AnalysisFixtures.metalHigh().completed.observations
        assertEquals(SafetyOutcome.CAUTION, assess(MaterialCategory.METAL, base, "condition", "unknown"))
        assertEquals(SafetyOutcome.ALLOW, assess(MaterialCategory.METAL, base, "corrosion", "light"))
        assertEquals(SafetyOutcome.CAUTION, assess(MaterialCategory.METAL, base, "corrosion", "heavy"))
        assertEquals(SafetyOutcome.ALLOW, assess(MaterialCategory.METAL, base, "coating_oil", "present"))
        assertEquals(SafetyOutcome.CAUTION, assess(MaterialCategory.METAL, base, "coating_oil", "unknown"))
        assertEquals(SafetyOutcome.CAUTION, assess(MaterialCategory.METAL, base, "sharp_edges", "yes"))
    }

    @Test
    fun unknownElectronicsBatteryDamageBlocksRecommendations() {
        val safe = AnalysisFixtures.electronicsHigh().completed.observations.map { observation ->
            when (observation.fieldId.value) {
                "battery", "powered", "burn_marks", "battery_damage" -> observation.copy(value = InspectionValue.Choice("no"))
                else -> observation
            }
        }
        assertEquals(SafetyOutcome.BLOCK, assess(MaterialCategory.ELECTRONICS, safe, "battery_damage", "unknown"))
    }

    private fun assess(
        category: MaterialCategory,
        observations: List<Observation>,
        field: String,
        value: String,
    ): SafetyOutcome {
        val changed = observations.map { observation ->
            if (observation.fieldId == FieldId(field)) observation.copy(value = InspectionValue.Choice(value)) else observation
        }
        return SafetyPolicy.assess(category, changed.associateBy(Observation::fieldId))
    }
}
