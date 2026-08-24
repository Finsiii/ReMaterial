package com.rematerial.app.feature.production

import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.production.data.MockProductionRepository
import com.rematerial.app.feature.production.domain.ProductDraft
import com.rematerial.app.feature.production.domain.ProductionRequestInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionRepositoryTest {
    @Test
    fun submitRejectsDraftWithoutAnalysisProvenance() = runTest {
        val repository = MockProductionRepository()
        val result = repository.submit(
            ProductionRequestInput(
                artisanId = "artisan-bima",
                draft = ProductDraft(),
                quantity = "1 unit",
                notes = "",
                address = "Jl. Demo 1",
                targetDate = "20 September 2026",
                phone = "081234567890",
            ),
        )
        assertTrue(result is Result.Failure)
    }

    @Test
    fun submitAcceptsCompletedRecommendationWithContact() = runTest {
        val repository = MockProductionRepository()
        val result = repository.submit(
            ProductionRequestInput(
                artisanId = "artisan-bima",
                draft = ProductDraft(
                    optionId = ProductOptionId("lampu-kabel"),
                    title = "Lampu meja kabel tembaga",
                    materialSummary = "Kabel tembaga · 2,45 kg",
                    minimumQuantity = "1 unit",
                    analysisId = "analysis-demo",
                    safetyAllowed = true,
                ),
                quantity = "1 unit",
                notes = "",
                address = "Jl. Demo 1",
                targetDate = "20 September 2026",
                phone = "081234567890",
            ),
        )
        assertTrue(result is Result.Success)
    }
}
