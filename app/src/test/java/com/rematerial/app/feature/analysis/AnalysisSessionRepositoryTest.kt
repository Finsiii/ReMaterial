package com.rematerial.app.feature.analysis

import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.feature.analysis.data.InMemoryAnalysisSessionRepository
import com.rematerial.app.feature.analysis.domain.SavedAnalysisIdea
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisSessionRepositoryTest {
    @Test
    fun savedIdeaCanBeLoadedByANewConsumer() = runTest {
        val repository = InMemoryAnalysisSessionRepository()
        val fixture = AnalysisFixturesHolder.metal
        val idea = SavedAnalysisIdea(fixture.analysisId, ProductOptionId("option-metal-1"), fixture)
        repository.saveIdea(idea)
        assertEquals(idea, (repository.savedIdeas() as com.rematerial.app.core.model.Result.Success).value.first())
    }

    private object AnalysisFixturesHolder {
        val metal = com.rematerial.app.feature.analysis.data.AnalysisFixtures.metalHigh().completed
    }
}
