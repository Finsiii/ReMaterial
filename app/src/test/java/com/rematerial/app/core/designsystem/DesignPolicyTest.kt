package com.rematerial.app.core.designsystem

import androidx.compose.ui.graphics.Color
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
}
