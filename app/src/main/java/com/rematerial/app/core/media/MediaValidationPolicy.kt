package com.rematerial.app.core.media

enum class MediaValidationFailure { UNSUPPORTED_TYPE, EMPTY, TOO_LARGE, UNREADABLE }

object MediaValidationPolicy {
    const val MAX_BYTES: Long = 15L * 1024L * 1024L
    val supportedTypes = setOf("image/jpeg", "image/png", "image/heic", "image/heif")

    fun validate(contentType: String?, sizeBytes: Long): MediaValidationFailure? = when {
        contentType?.lowercase()?.substringBefore(';') !in supportedTypes -> MediaValidationFailure.UNSUPPORTED_TYPE
        sizeBytes <= 0L -> MediaValidationFailure.EMPTY
        sizeBytes > MAX_BYTES -> MediaValidationFailure.TOO_LARGE
        else -> null
    }

    fun validateHeader(contentType: String, header: ByteArray): MediaValidationFailure? {
        val matches = when (contentType.lowercase().substringBefore(';')) {
            "image/jpeg" -> header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
            "image/png" -> header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            "image/heic", "image/heif" -> header.size >= 12 && header.sliceArray(4..7).contentEquals(byteArrayOf(0x66, 0x74, 0x79, 0x70)) &&
                String(header.sliceArray(8..11), Charsets.US_ASCII) in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
            else -> return MediaValidationFailure.UNSUPPORTED_TYPE
        }
        return if (matches) null else MediaValidationFailure.UNREADABLE
    }
}
