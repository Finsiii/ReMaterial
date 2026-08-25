package com.rematerial.app.feature.artisan.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtisanNavigationPolicyTest {
    @Test
    fun `profile returns to the page that opened it`() {
        assertEquals(ArtisanPage.HOME, artisanProfileBackTarget(ArtisanProfileOrigin.HOME))
        assertEquals(ArtisanPage.SETTINGS, artisanProfileBackTarget(ArtisanProfileOrigin.SETTINGS))
    }
}
