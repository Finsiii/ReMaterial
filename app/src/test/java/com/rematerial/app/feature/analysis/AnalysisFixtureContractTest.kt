package com.rematerial.app.feature.analysis

import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.feature.analysis.data.AnalysisFixtures
import com.rematerial.app.feature.analysis.data.MockAiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.AnalysisCatalog
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.ProductScorePolicy
import com.rematerial.app.feature.analysis.domain.SafetyPolicy
import kotlinx.coroutines.test.runTest
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.UnitCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFixtureContractTest {
    @Test
    fun everyCategoryHasSpecificSchemaEvidenceCalculationsAndThreeOptionsUnlessBlocked() {
        val fixtures = AnalysisFixtures.allCompleted()
        assertEquals(MaterialCategory.entries.toSet(), fixtures.map { it.category }.toSet())
        fixtures.forEach { fixture ->
            assertTrue(fixture.initial.requestedFields.any { it.id in com.rematerial.app.feature.analysis.domain.AnalysisCatalog.categoryFields.getValue(fixture.category) })
            assertTrue(fixture.completed.science.all { it.observationRefs.isNotEmpty() && it.sourceRefs.isNotEmpty() && it.limitation.isNotBlank() && it.recommendedVerification.isNotBlank() })
            assertTrue(fixture.completed.mathematics.size >= 3)
            assertTrue(fixture.completed.mathematics.any { it.formulaId.contains("usable") })
            assertTrue(fixture.completed.mathematics.any { it.formulaId.contains("cost") || it.formulaId.contains("margin") || it.formulaId.contains("yield") })
            if (fixture.completed.safety.outcome == SafetyOutcome.BLOCK) assertTrue(fixture.completed.productOptions.isEmpty())
            else assertTrue(fixture.completed.productOptions.size >= 3)
            assertTrue(AnalysisValidator.validate(fixture.completed) is Result.Success)
        }
    }

    @Test
    fun manualCategoryControlsTheReturnedSchema() = runTest {
        val gateway = MockAiAnalysisGateway()
        val request = AnalysisFixtures.initialRequest().copy(photo = null, manualCategory = MaterialCategory.WOOD)
        val response = gateway.start(request) as Result.Success
        assertEquals(MaterialCategory.WOOD, response.value.prediction.category)
        assertTrue(response.value.requestedFields.any { it.id.value == "treatment" })
        assertTrue(response.value.requestedFields.none { it.id.value == "sharp_edges" })
    }

    @Test
    fun mediumConfidenceAlternativesAreOrderedAndCarryConfidence() {
        val prediction = AnalysisFixtures.cableMedium().initial.prediction
        assertTrue(prediction.rankedCandidates.size in 2..3)
        assertEquals(MaterialCategory.CABLE, prediction.rankedCandidates.first().category)
        assertTrue(prediction.rankedCandidates.zipWithNext().all { (left, right) -> left.confidence >= right.confidence })
    }

    @Test
    fun schemasUseCategoryQuantityUnitsAndOnlyCatalogMetadata() {
        AnalysisFixtures.allCompleted().forEach { fixture ->
            val expected = com.rematerial.app.feature.analysis.domain.AnalysisCatalog.schemaFor(fixture.category)
            assertEquals(expected, fixture.initial.requestedFields)
        }
        assertEquals(UnitCode.M, com.rematerial.app.feature.analysis.domain.AnalysisCatalog.schemaFor(MaterialCategory.CABLE).first { it.id.value == "quantity" }.unit)
        assertTrue(com.rematerial.app.feature.analysis.domain.AnalysisCatalog.schemaFor(MaterialCategory.TEXTILE).first { it.id.value == "quantity" }.unit in setOf(UnitCode.M, UnitCode.M2))
        assertEquals(UnitCode.PCS, com.rematerial.app.feature.analysis.domain.AnalysisCatalog.schemaFor(MaterialCategory.ELECTRONICS).first { it.id.value == "quantity" }.unit)
    }

    @Test
    fun measurementFieldsAndAlternateQuantityContractsHaveExactMetadata() {
        listOf(MaterialCategory.METAL, MaterialCategory.PLASTIC).forEach { category ->
            val dimensions = AnalysisCatalog.schemaFor(category).first { it.id.value == "dimensions_cm" }
            assertEquals(InspectionFieldType.DECIMAL, dimensions.type)
            assertEquals(UnitCode.CM, dimensions.unit)
        }
        val textileWidth = AnalysisCatalog.schemaFor(MaterialCategory.TEXTILE).first { it.id.value == "width_m" }
        assertEquals(InspectionFieldType.DECIMAL, textileWidth.type)
        assertEquals(UnitCode.M, textileWidth.unit)

        val plasticPieces = AnalysisCatalog.schemaFor(MaterialCategory.PLASTIC, UnitCode.PCS)
            .first { it.id.value == "quantity" }
        assertEquals("Jumlah potong bahan", plasticPieces.label)
        assertTrue(AnalysisCatalog.isCompatible(MaterialCategory.PLASTIC, plasticPieces))
        assertTrue(!AnalysisCatalog.isCompatible(MaterialCategory.PLASTIC, plasticPieces.copy(label = "Perkiraan berat bahan")))

        val textileArea = AnalysisCatalog.schemaFor(MaterialCategory.TEXTILE, UnitCode.M2)
            .first { it.id.value == "quantity" }
        assertEquals("Perkiraan luas bahan", textileArea.label)
        assertTrue(AnalysisCatalog.isCompatible(MaterialCategory.TEXTILE, textileArea))
        assertTrue(!AnalysisCatalog.isCompatible(MaterialCategory.TEXTILE, textileArea.copy(label = "Perkiraan panjang bahan")))
    }

    @Test
    fun fixtureCollectionContainsAllowCautionAndBlockFromActualObservations() {
        val fixtures = AnalysisFixtures.allCompleted()
        assertEquals(SafetyOutcome.entries.toSet(), fixtures.map { it.completed.safety.outcome }.toSet())
        fixtures.forEach { fixture ->
            assertEquals(
                fixture.completed.safety.outcome,
                SafetyPolicy.assess(fixture.category, fixture.completed.observations.associateBy(Observation::fieldId)),
            )
        }
        assertEquals(SafetyOutcome.CAUTION, AnalysisFixtures.woodHigh().completed.safety.outcome)
        assertEquals(SafetyOutcome.CAUTION, AnalysisFixtures.textileMedium().completed.safety.outcome)
    }

    @Test
    fun scoreEvidenceUsesPercentValuesAndIsRecomputedFromActualEvidence() {
        val fixture = AnalysisFixtures.metalHigh()
        val response = fixture.completed
        val usable = response.mathematics.first { it.formulaId == "usable_mass" }.result
        val initial = (response.observations.first { it.fieldId.value == "quantity" }.value as InspectionValue.Decimal).value
        response.productOptions.forEach { option ->
            val evidence = option.scoreEvidenceInputs.associateBy { it.name }
            assertTrue(evidence.values.all { it.unit == UnitCode.PERCENT })
            assertEquals(option.scoreComponents.propertyFit, evidence.getValue("propertyFit").value, .001)
            assertEquals(option.scoreComponents.materialSufficiency, evidence.getValue("materialSufficiency").value, .001)
            assertEquals(option.scoreComponents.economicPotential, evidence.getValue("economicPotential").value, .001)
            assertEquals(option.scoreComponents.residueReduction, evidence.getValue("residueReduction").value, .001)
            assertEquals((usable / option.minimumQuantity).coerceAtMost(1.0) * 100.0, option.scoreComponents.materialSufficiency, .001)
            assertEquals(option.requiredParameterIds, option.parameterRequirements.map { it.fieldId })
            assertEquals(option.minimumUnit, option.estimatedUsedUnit)
            assertTrue(option.estimatedUsedQuantity in 0.0..usable)
            assertEquals(option.estimatedUsedQuantity / initial * 100.0, option.scoreComponents.residueReduction, .001)
            assertEquals(ProductScorePolicy.calculate(option.scoreComponents), option.provisionalProductScore, .001)
        }
        assertTrue(response.productOptions.map { it.estimatedUsedQuantity }.distinct().size > 1)
        assertTrue(response.productOptions.map { it.scoreComponents.residueReduction }.distinct().size > 1)

        val option = response.productOptions.first()
        val invalidOption = option.copy(
            scoreEvidenceInputs = option.scoreEvidenceInputs.map {
                if (it.name == "economicPotential") it.copy(value = it.value - 1.0) else it
            },
        )
        assertTrue(AnalysisValidator.validate(response.copy(productOptions = listOf(invalidOption))) is Result.Failure)
    }

    @Test
    fun providedButUnsatisfiedProductRequirementLowersPropertyFitAndIsRejectedWhenScoreIsStale() = runTest {
        val response = AnalysisFixtures.metalHigh().completed
        val changedObservations = response.observations.map { observation ->
            if (observation.fieldId.value == "condition") {
                observation.copy(value = InspectionValue.Choice("damaged"))
            } else observation
        }
        val stale = response.copy(observations = changedObservations)
        val staleValidation = AnalysisValidator.validate(stale) as Result.Failure
        assertTrue((staleValidation.error as DomainFailure.Validation).violations.any { it == "Property fit mismatch" })

        val option = response.productOptions.first()
        assertTrue(option.parameterRequirements.any {
            it.fieldId.value == "condition" && "damaged" !in it.acceptedChoices
        })

        val recomputed = MockAiAnalysisGateway().complete(
            com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest(
                response.analysisId,
                MaterialCategory.METAL,
                changedObservations,
            ),
        ) as Result.Success
        assertTrue(recomputed.value.productOptions.first().scoreComponents.propertyFit < option.scoreComponents.propertyFit)
        assertTrue(AnalysisValidator.validate(recomputed.value) is Result.Success)
    }

    @Test
    fun estimatedUsedQuantityCannotExceedUsableQuantityEvenSlightly() {
        val response = AnalysisFixtures.metalHigh().completed
        val usable = response.mathematics.first { it.formulaId == "usable_mass" }.result
        val initial = (response.observations.first { it.fieldId.value == "quantity" }.value as InspectionValue.Decimal).value
        val original = response.productOptions.first()
        val components = original.scoreComponents.copy(residueReduction = (usable + .001) / initial * 100.0)
        val option = original.copy(
            estimatedUsedQuantity = usable + .001,
            scoreComponents = components,
            scoreEvidenceInputs = original.scoreEvidenceInputs.map {
                if (it.name == "residueReduction") it.copy(value = components.residueReduction) else it
            },
            provisionalProductScore = ProductScorePolicy.calculate(components),
        )
        assertTrue(AnalysisValidator.validate(response.copy(productOptions = listOf(option))) is Result.Failure)
    }

    @Test
    fun marginReadinessMustUseTheSelectedProviderEconomicAndSufficiencyEvidence() {
        val response = AnalysisFixtures.metalHigh().completed
        val hardcodedMargin = response.mathematics.first { it.formulaId == "margin_readiness" }.copy(
            inputs = listOf(
                com.rematerial.app.feature.analysis.domain.CalculationInput(
                    "potensi ekonomi", 74.0, UnitCode.PERCENT, evidenceSource = "ai-api:economic-model",
                ),
                com.rematerial.app.feature.analysis.domain.CalculationInput(
                    "kecukupan bahan", 82.0, UnitCode.PERCENT, evidenceSource = "derived:usable-minimum",
                ),
            ),
            result = 78.0,
        )
        val changed = response.copy(
            mathematics = response.mathematics.map {
                if (it.formulaId == "margin_readiness") hardcodedMargin else it
            },
        )
        assertTrue(AnalysisValidator.validate(changed) is Result.Failure)
    }

    @Test fun mockCompletionUsesActualQuantityAndSafetyAnswers() = runTest {
        val gateway = MockAiAnalysisGateway(MockAiAnalysisGateway.Scenario.ELECTRONICS_BLOCK)
        val fixture = AnalysisFixtures.electronicsHigh()
        val safe = fixture.completed.observations.map { observation ->
            when (observation.fieldId.value) {
                "quantity" -> Observation(FieldId("quantity"), InspectionValue.Decimal(7.0), UnitCode.PCS)
                "powered", "battery_damage", "burn_marks", "battery" -> Observation(observation.fieldId, InspectionValue.Choice("no"))
                else -> observation
            }
        }
        val response = gateway.complete(com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest(fixture.completed.analysisId, MaterialCategory.ELECTRONICS, safe)) as Result.Success
        assertEquals(7.0, response.value.mathematics.first().inputs.first().value, .001)
        assertEquals(com.rematerial.app.core.model.SafetyOutcome.ALLOW, response.value.safety.outcome)
    }

    @Test fun smallActualQuantityLowersMaterialSufficiencyFromUsableQuantity() = runTest {
        val gateway = MockAiAnalysisGateway(MockAiAnalysisGateway.Scenario.METAL_HIGH)
        val fixture = AnalysisFixtures.metalHigh()
        val observations = fixture.completed.observations.map { observation ->
            if (observation.fieldId.value == "quantity") {
                Observation(FieldId("quantity"), InspectionValue.Decimal(.5), UnitCode.KG)
            } else observation
        }
        val response = gateway.complete(
            com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest(
                fixture.completed.analysisId,
                MaterialCategory.METAL,
                observations,
            ),
        ) as Result.Success
        val usable = response.value.mathematics.first { it.formulaId == "usable_mass" }.result
        val option = response.value.productOptions.first()
        assertEquals(.45, usable, .001)
        assertEquals((usable / option.minimumQuantity).coerceAtMost(1.0) * 100.0, option.scoreComponents.materialSufficiency, .001)
        assertEquals(
            option.scoreComponents.materialSufficiency,
            option.scoreEvidenceInputs.first { it.name == "materialSufficiency" }.value,
            .001,
        )
    }
}
