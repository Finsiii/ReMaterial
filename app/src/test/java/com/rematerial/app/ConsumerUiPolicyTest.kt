package com.rematerial.app

import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerUiPolicyTest {
    private val sourceRoot = Paths.get(
        System.getProperty("rematerial.mainSourceDir")
            ?: error("Gradle must provide rematerial.mainSourceDir for source policy checks"),
    )

    @Test
    fun `home and scan use concise action first copy`() {
        val home = sourceRoot.resolve("com/rematerial/app/feature/home/UserHomeScreen.kt").toFile().readText()
        val analysis = sourceRoot.resolve("com/rematerial/app/feature/analysis/presentation/AnalysisScreens.kt").toFile().readText()

        assertTrue(home.contains("Analisis bahanmu"))
        assertTrue(analysis.contains("Scan bahan"))
        assertFalse(home.contains("Lihat kemungkinan"))
        assertFalse(home.contains("height(390.dp)"))
        assertFalse(analysis.contains("Beri materialmu arah baru"))
        assertFalse(analysis.contains("Lima sudut, satu gambaran utuh"))
    }
}
