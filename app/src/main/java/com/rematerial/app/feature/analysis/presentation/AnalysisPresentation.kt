package com.rematerial.app.feature.analysis.presentation

import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.core.model.UnitCode

/** Turns API vocabulary into words that make sense to someone making things. */
object AnalysisPresentation {
    fun safetyTitle(outcome: SafetyOutcome): String = when (outcome) {
        SafetyOutcome.ALLOW -> "Bisa mulai dengan aman"
        SafetyOutcome.CAUTION -> "Perlu cek tambahan"
        SafetyOutcome.BLOCK -> "Tunda dulu prosesnya"
    }

    fun safetyBody(outcome: SafetyOutcome): String = when (outcome) {
        SafetyOutcome.ALLOW -> "Bahan ini cukup aman untuk eksplorasi awal. Tetap gunakan alat pelindung yang sesuai."
        SafetyOutcome.CAUTION -> "Bahan ini masih bisa dimanfaatkan setelah beberapa hal penting diperiksa."
        SafetyOutcome.BLOCK -> "Jangan diproses sebelum sumber risikonya diperiksa dan dinyatakan aman."
    }

    fun choice(value: String): String = when (value.lowercase()) {
        "good" -> "Masih bagus"
        "worn" -> "Ada bekas pakai"
        "damaged" -> "Rusak atau berubah bentuk"
        "unknown" -> "Belum yakin"
        "none" -> "Tidak terlihat"
        "low" -> "Sedikit"
        "suspected_hazardous" -> "Diduga berbahaya"
        "true" -> "Ya"
        "false" -> "Tidak"
        else -> value.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun tool(value: String): String = when (value) {
        "hand-tools" -> "Alat tangan"
        "measuring-tools" -> "Alat ukur"
        "finishing-tools" -> "Alat finishing"
        "cutting-tools" -> "Alat potong"
        else -> value.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    fun skill(value: String): String = when (value) {
        "basic-making" -> "Keterampilan dasar"
        "precision-making" -> "Pengerjaan presisi"
        "surface-finishing" -> "Finishing permukaan"
        else -> value.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    fun source(value: String): String = when (value) {
        "rematerial-material-procedure" -> "Prosedur pemeriksaan material ReMaterial"
        "school-material-safety" -> "Panduan keselamatan material"
        else -> "Referensi pemeriksaan material"
    }

    fun unit(unit: UnitCode): String = when (unit) {
        UnitCode.KG -> "kg"
        UnitCode.G -> "gram"
        UnitCode.M -> "meter"
        UnitCode.CM -> "cm"
        UnitCode.MM -> "mm"
        UnitCode.M2 -> "m²"
        UnitCode.PERCENT -> "%"
        UnitCode.PCS -> "buah"
        UnitCode.L -> "liter"
        UnitCode.NONE -> "nilai"
    }

    fun categoryIntro(category: MaterialCategory): String = when (category) {
        MaterialCategory.METAL -> "Kuat dan mudah dibentuk menjadi benda rumah tangga yang tahan lama."
        MaterialCategory.CABLE -> "Fleksibel dan cocok untuk aksen, detail, atau anyaman dekoratif."
        MaterialCategory.PLASTIC -> "Ringan dan serbaguna, dengan perhatian pada jenis plastiknya."
        MaterialCategory.WOOD -> "Punya karakter hangat dan bisa diberi fungsi baru dengan perapian sederhana."
        MaterialCategory.TEXTILE -> "Lentur dan cocok diolah menjadi aksesori atau elemen interior."
        MaterialCategory.ELECTRONICS -> "Komponen elektronik perlu pemeriksaan keselamatan sebelum disentuh atau diolah."
    }
}
