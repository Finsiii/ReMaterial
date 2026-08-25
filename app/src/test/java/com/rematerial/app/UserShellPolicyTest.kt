package com.rematerial.app

import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.HorizontalPageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserShellPolicyTest {
    @Test
    fun `user routes map to the locked dock order`() {
        assertEquals(0, userTabIndex("user-home"))
        assertEquals(1, userTabIndex("production"))
        assertEquals(2, userTabIndex("analysis"))
        assertEquals(3, userTabIndex("market"))
        assertEquals(4, userTabIndex("account"))
    }

    @Test
    fun `dock selection is derived only from dock visible routes`() {
        assertEquals(DockDestination.Beranda, userDockDestination("user-home"))
        assertEquals(DockDestination.Produksi, userDockDestination("production"))
        assertEquals(DockDestination.Pasar, userDockDestination("market"))
        assertEquals(DockDestination.Akun, userDockDestination("account"))
        assertNull(userDockDestination("analysis"))
        assertNull(userDockDestination("orders"))
        assertNull(userDockDestination("identity"))
    }

    @Test
    fun `tab motion follows spatial order for both navigate and pop`() {
        assertEquals(HorizontalPageMotion.FORWARD, userRouteMotion("user-home", "production"))
        assertEquals(HorizontalPageMotion.BACKWARD, userRouteMotion("production", "user-home"))
        assertEquals(HorizontalPageMotion.FORWARD, userRouteMotion("production", "analysis"))
        assertEquals(HorizontalPageMotion.BACKWARD, userRouteMotion("analysis", "production"))
    }
}
