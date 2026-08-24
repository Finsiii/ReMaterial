package com.rematerial.app.core.designsystem

import androidx.compose.ui.graphics.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignPolicyTest {
    @Test
    fun `locked palette uses premium ReMaterial values`() {
        assertEquals(Color(0xFFE9E7E1), RematerialColors.Canvas)
        assertEquals(Color(0xFFFFFEFA), RematerialColors.Surface)
        assertEquals(Color(0xFF141A18), RematerialColors.Ink)
        assertEquals(Color(0xFF737672), RematerialColors.Muted)
        assertEquals(Color(0xFFE7E3DC), RematerialColors.Line)
        assertEquals(Color(0xFF073F37), RematerialColors.DeepForest)
        assertEquals(Color(0xFF052F2A), RematerialColors.ForestDark)
        assertEquals(Color(0xFFA87948), RematerialColors.Bronze)
        assertEquals(Color(0xFFEEE3D6), RematerialColors.BronzeSoft)
    }

    @Test
    fun `dock keeps the five locked User destinations in order`() {
        assertEquals(
            listOf("Beranda", "Produksi", "Scan", "Pasar", "Akun"),
            DockDestination.entries.map(DockDestination::label),
        )
        assertTrue(DockDestination.Scan.isPrimary)
        assertFalse(DockDestination.Beranda.isPrimary)
    }

    @Test
    fun `design system does not expose badge or pill status primitives`() {
        assertFalse(DesignSystemPolicy.usesDecorativeGradients)
        assertFalse(DesignSystemPolicy.usesPillStatuses)
        assertEquals(DesignSystemPolicy.iconSource, "Lucide")
    }

    @Test
    fun `main source keeps the locked implementation boundaries`() {
        val sourceRoot = Paths.get(
            System.getProperty("rematerial.mainSourceDir")
                ?: error("Gradle must provide rematerial.mainSourceDir for source policy checks"),
        )
        assertTrue("main source directory is missing: $sourceRoot", Files.isDirectory(sourceRoot))
        val source = readKotlin(sourceRoot)

        assertFalse(source.contains("androidx.compose.material.icons"))
        assertFalse(Regex("import\\s+[^\\n]*(fake|fixture|mock)", RegexOption.IGNORE_CASE).containsMatchIn(source))
        assertFalse(Regex("\\b(NavigationBar|NavigationRail|AssistChip|FilterChip|InputChip|SuggestionChip|Badge)\\b").containsMatchIn(source))
        assertFalse(Regex("Brush\\.(linearGradient|radialGradient|sweepGradient)").containsMatchIn(source))
    }

    @Test
    fun `resources and source retain typography palette and dock accessibility policy`() {
        val sourceRoot = Paths.get(
            System.getProperty("rematerial.mainSourceDir")
                ?: error("Gradle must provide rematerial.mainSourceDir for source policy checks"),
        )
        val resourceRoot = sourceRoot.parent.resolve("res")
        listOf("manrope_regular", "manrope_medium", "manrope_semibold", "manrope_bold", "manrope_extrabold")
            .forEach { font -> assertTrue("missing font resource: $font", Files.exists(resourceRoot.resolve("font/$font.ttf"))) }

        val tokens = readKotlin(sourceRoot).substringAfter("object RematerialColors").substringBefore("val Manrope")
        listOf("E9E7E1", "FFFEFA", "141A18", "737672", "E7E3DC", "073F37", "052F2A", "A87948", "EEE3D6")
            .forEach { color -> assertTrue("missing locked palette color: $color", tokens.contains(color)) }

        val dock = readKotlin(sourceRoot).substringAfter("fun RematerialDock")
        assertTrue(dock.contains("selectableGroup()"))
        assertTrue(dock.contains("selectable("))
        assertTrue(dock.contains("contentDescription = destination.label"))
        assertTrue(DockDestination.entries.all { it.label.isNotBlank() })
    }

    private fun readKotlin(root: Path): String = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
            .map { it.toFile().readText() }
            .collect(Collectors.joining("\n"))
    }
}
