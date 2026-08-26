package com.rematerial.app

import com.rematerial.app.core.designsystem.DockDestination
import com.rematerial.app.core.designsystem.HorizontalPageMotion
import com.rematerial.app.feature.identity.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserShellPolicyTest {
    @Test
    fun `user workspace is created only after a concrete user session exists`() {
        assertNull(userWorkspaceKey(null, null))
        assertNull(userWorkspaceKey(Role.USER, ""))
        assertNull(userWorkspaceKey(Role.ARTISAN, "artisan-01"))
        assertNull(userWorkspaceKey(Role.SELLER, "seller-01"))
        assertEquals("user:user-01", userWorkspaceKey(Role.USER, "user-01"))
    }

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
        assertEquals(DockDestination.Scan, userDockDestination("analysis"))
        assertEquals(DockDestination.Pasar, userDockDestination("market"))
        assertEquals(DockDestination.Akun, userDockDestination("account"))
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
