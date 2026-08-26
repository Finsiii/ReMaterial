package com.rematerial.app.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DockLayoutPolicyTest {
    @Test
    fun `bottom navigation reserves only its compact surface height`() {
        assertEquals(64.dp, RematerialDockMetrics.reservedBottom)
    }

    @Test
    fun `dock content reserve adds the navigation inset once`() {
        assertEquals(88.dp, RematerialDockMetrics.contentBottomPadding(24.dp))
    }

    @Test
    fun `standalone content does not reserve a hidden dock`() {
        assertEquals(88.dp, RematerialDockMetrics.screenBottomPadding(24.dp, dockVisible = true))
        assertEquals(48.dp, RematerialDockMetrics.screenBottomPadding(24.dp, dockVisible = false))
    }

    @Test
    fun `shared horizontal motion policy has push and pop semantics`() {
        assertEquals(HorizontalPageMotion.FORWARD, horizontalPageMotion(0, 1))
        assertEquals(HorizontalPageMotion.BACKWARD, horizontalPageMotion(2, 1))
        assertEquals(HorizontalPageMotion.FORWARD, horizontalPageMotion(1, 1))
    }
}
