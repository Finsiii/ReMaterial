package com.rematerial.app.feature.analysis.data

import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.feature.analysis.domain.ObjectAnalysis

internal object ReMaterialAiPrompts {
    private val system = """
        INSTRUKSI SISTEM REMATERIAL
        Kamu menganalisis barang atau material bekas dari beberapa foto untuk pengguna umum di Indonesia.
        Balas HANYA satu objek JSON valid tanpa markdown, code fence, pembuka, atau penutup.
        Identifikasi objek utuh terlebih dahulu, lalu seluruh komponen dan materialnya. Jangan mereduksi kursi berlapis menjadi hanya KAYU: sebutkan rangka, kain, bantalan/busa bila beralasan, dan pengikat yang benar-benar terlihat atau lazim tetapi tersembunyi.
        Bedakan tegas bukti VISIBLE dari dugaan LIKELY. Material tersembunyi hanya boleh ditulis bersama alasan dan tidak boleh dinyatakan pasti.
        Jika bentuk asli masih INTACT atau REPAIRABLE, prioritaskan REPAIR atau UPGRADE. Jangan menyarankan pembongkaran menjadi meja, rak, atau benda baru bila perbaikan barang asli lebih masuk akal.
        REPURPOSE hanya untuk benda PARTIAL; RECYCLE untuk SCRAP. Utamakan keselamatan dan pemeriksaan pengrajin untuk hal yang tidak terlihat.
        Gunakan bahasa Indonesia yang ringkas, alami, dan mudah dipahami.
    """.trimIndent()

    fun initial(analysisId: String): String = """
        $system

        TUGAS: Gabungkan bukti dari semua foto untuk analisis $analysisId. Foto merupakan sudut berbeda dari objek yang sama.
        category adalah material utama dan wajib salah satu METAL, CABLE, PLASTIC, WOOD, TEXTILE, ELECTRONICS.
        confidence_percent 1..100. quantity_estimate adalah jumlah objek atau bagian utama yang nyata terlihat, bukan jumlah potongan hipotetis setelah dibongkar.
        condition: good/worn/damaged/unknown. contamination: none/low/unknown/suspected_hazardous.
        object_state: INTACT/REPAIRABLE/PARTIAL/SCRAP/UNKNOWN. primary_strategy: REPAIR/UPGRADE/REPURPOSE/RECYCLE.
        visible_components hanya bukti yang terlihat. inferred_hidden_materials hanya komponen tertutup yang masuk akal dan wajib punya reason.
        follow_up_ids berisi 0..2 dari quantity, condition, contamination, hanya bila jawaban pengguna benar-benar dibutuhkan.
        Skema keluaran persis:
        {"category":"WOOD","confidence_percent":91,"quantity_estimate":1,"condition":"worn","contamination":"none","object_name":"Kursi kayu berlapis kain","object_state":"REPAIRABLE","visible_components":[{"part":"rangka","material":"kayu","evidence":"VISIBLE"},{"part":"pelapis dudukan","material":"kain","evidence":"VISIBLE"}],"inferred_hidden_materials":[{"material":"busa","reason":"bantalan tertutup kain"}],"primary_strategy":"REPAIR","follow_up_ids":[]}
    """.trimIndent()

    fun complete(category: MaterialCategory, observationsJson: String, objectAnalysis: ObjectAnalysis?): String {
        val context = objectAnalysis?.let {
            "objek=${it.objectName}; kondisi=${it.state}; strategi=${it.primaryStrategy}; komponen terlihat=${it.visibleComponents.joinToString { component -> "${component.part}:${component.material}" }}; material tersembunyi dugaan=${it.inferredHiddenMaterials.joinToString { inferred -> inferred.material }}"
        } ?: "objek tidak teridentifikasi"
        return """
            $system

            TUGAS LANJUTAN DALAM SESI YANG SAMA: sempurnakan penjelasan dan tiga opsi tindakan konkret untuk kategori ${category.name}.
            Konteks visual: $context
            Observasi tambahan pengguna: $observationsJson

            Untuk INTACT/REPAIRABLE, tiga opsi wajib berupa perbaikan atau peningkatan pada objek yang sama, misalnya ganti pelapis, perkuat sambungan, atau perbarui finishing. Jangan mengarang ketersediaan material hasil bongkar.
            Untuk PARTIAL/SCRAP, baru boleh beri satu benda spesifik per opsi sesuai jumlah nyata.
            Jangan melakukan aritmetika; aplikasi menghitung formula deterministik. science tepat satu objek dan product_options tepat tiga objek, kecuali kondisi berbahaya.
            Skema keluaran persis:
            {"science":[{"title":"...","principle":"...","interpretation":"...","limitation":"...","recommended_verification":"..."}],"product_options":[{"title":"...","explanation":"..."},{"title":"...","explanation":"..."},{"title":"...","explanation":"..."}]}
        """.trimIndent()
    }
}
