package com.rematerial.app.feature.analysis

import com.rematerial.app.feature.analysis.data.AnalysisFixtures
import com.rematerial.app.feature.analysis.presentation.AnalysisPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPresentationTest {
    @Test
    fun everyNonBlockedFixtureHasThreeLocalizedIdeas() {
        AnalysisFixtures.allCompleted()
            .filter { it.completed.productOptions.isNotEmpty() }
            .forEach { fixture ->
                assertTrue(fixture.completed.productOptions.size >= 3)
                fixture.completed.productOptions.forEach { option ->
                    assertTrue(option.explanation.isNotBlank())
                    assertTrue(option.requiredToolIds.all { AnalysisPresentation.tool(it).isNotBlank() })
                    assertTrue(option.requiredSkillIds.all { AnalysisPresentation.skill(it).isNotBlank() })
                }
            }
    }

    @Test
    fun userFacingLabelsDoNotExposeApiVocabulary() {
        assertEquals("Masih bagus", AnalysisPresentation.choice("good"))
        assertEquals("Sedikit", AnalysisPresentation.choice("low"))
        assertEquals("Bisa mulai dengan aman", AnalysisPresentation.safetyTitle(com.rematerial.app.core.model.SafetyOutcome.ALLOW))
    }
}
