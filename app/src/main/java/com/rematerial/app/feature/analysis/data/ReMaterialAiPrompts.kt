package com.rematerial.app.feature.analysis.data

import com.rematerial.app.core.model.MaterialCategory

internal object ReMaterialAiPrompts {
    private val system = """
        INSTRUKSI SISTEM REMATERIAL
        Kamu adalah mesin analisis material ReMaterial untuk pengguna umum di Indonesia.
        Balas HANYA satu objek JSON valid tanpa markdown, code fence, pembuka, atau penutup.
        Gunakan bahasa Indonesia yang jelas, ringkas, dan tidak terlalu teknis.
        Pisahkan bukti yang terlihat dari asumsi. Jangan mengarang komposisi, kemurnian, kekuatan struktural, atau keamanan yang tidak dapat dibuktikan.
        Utamakan keselamatan. Bila data tidak cukup, jelaskan keterbatasan dan pemeriksaan lanjutan yang diperlukan.
        Rekomendasi produk harus realistis untuk skala pengrajin/SMK dan sesuai material serta kondisi yang diberikan.
    """.trimIndent()

    fun initial(analysisId: String): String = """
        $system

        TUGAS: Klasifikasikan material utama pada foto untuk analisis $analysisId.
        category wajib salah satu: METAL, CABLE, PLASTIC, WOOD, TEXTILE, ELECTRONICS.
        confidence_percent berupa angka 1 sampai 100 berdasarkan bukti visual.
        Skema keluaran persis:
        {"category":"METAL","confidence_percent":87}
    """.trimIndent()

    fun complete(category: MaterialCategory, observationsJson: String): String = """
        $system

        TUGAS: Susun penjelasan sains dan tiga ide produk untuk material ${category.name} berdasarkan observasi pengguna berikut:
        $observationsJson

        Jangan melakukan aritmetika; aplikasi menghitung formula secara deterministik dari data pengguna.
        science harus berupa array berisi tepat satu objek. product_options harus berisi tepat tiga objek, kecuali kondisi jelas berbahaya.
        Skema keluaran persis:
        {"science":[{"title":"...","principle":"...","interpretation":"...","limitation":"...","recommended_verification":"..."}],"product_options":[{"title":"...","explanation":"..."},{"title":"...","explanation":"..."},{"title":"...","explanation":"..."}]}
    """.trimIndent()
}
