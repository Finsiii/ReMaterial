package com.rematerial.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaValidationPolicyTest {
    @Test
    fun acceptsSupportedNonEmptyImagesWithinLimit() {
        assertNull(MediaValidationPolicy.validate("image/jpeg", 2048))
        assertNull(MediaValidationPolicy.validate("image/heic", MediaValidationPolicy.MAX_BYTES))
    }

    @Test
    fun rejectsUnknownEmptyAndOversizedInput() {
        assertEquals(MediaValidationFailure.UNSUPPORTED_TYPE, MediaValidationPolicy.validate("application/pdf", 2048))
        assertEquals(MediaValidationFailure.EMPTY, MediaValidationPolicy.validate("image/jpeg", 0))
        assertEquals(MediaValidationFailure.TOO_LARGE, MediaValidationPolicy.validate("image/jpeg", MediaValidationPolicy.MAX_BYTES + 1))
    }

    @Test
    fun validatesSupportedFileSignaturesAndRejectsWebp() {
        assertNull(MediaValidationPolicy.validateHeader("image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertNull(MediaValidationPolicy.validateHeader("image/png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertNull(MediaValidationPolicy.validateHeader("image/heic", byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63)))
        assertEquals(MediaValidationFailure.UNSUPPORTED_TYPE, MediaValidationPolicy.validate("image/webp", 10))
        assertEquals(MediaValidationFailure.UNREADABLE, MediaValidationPolicy.validateHeader("image/jpeg", byteArrayOf(1, 2, 3)))
    }
}
