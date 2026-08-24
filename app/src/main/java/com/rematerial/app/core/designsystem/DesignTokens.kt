package com.rematerial.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.R

object RematerialColors {
    val Canvas = Color(0xFFE9E7E1)
    val Surface = Color(0xFFFFFEFA)
    val Ink = Color(0xFF141A18)
    val Muted = Color(0xFF737672)
    val Line = Color(0xFFE7E3DC)
    val DeepForest = Color(0xFF073F37)
    val ForestDark = Color(0xFF052F2A)
    val Bronze = Color(0xFFA87948)
    val BronzeSoft = Color(0xFFEEE3D6)
}

val Manrope = FontFamily(
    Font(com.rematerial.app.R.font.manrope_regular, FontWeight.Normal),
    Font(com.rematerial.app.R.font.manrope_medium, FontWeight.Medium),
    Font(com.rematerial.app.R.font.manrope_semibold, FontWeight.SemiBold),
    Font(com.rematerial.app.R.font.manrope_bold, FontWeight.Bold),
    Font(com.rematerial.app.R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val RematerialTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.2.sp),
)

object DesignSystemPolicy {
    const val iconSource = "Lucide"
    const val usesDecorativeGradients = false
    const val usesPillStatuses = false
}

@Immutable
enum class DockDestination(
    val label: String,
    val icon: Int,
    val isPrimary: Boolean = false,
) {
    Beranda("Beranda", R.drawable.lucide_ic_house),
    Produksi("Produksi", R.drawable.lucide_ic_hammer),
    Scan("Scan", R.drawable.lucide_ic_scan, isPrimary = true),
    Pasar("Pasar", R.drawable.lucide_ic_store),
    Akun("Akun", R.drawable.lucide_ic_user_round),
}

object RematerialIcons {
    val Back = R.drawable.lucide_ic_arrow_left
    val Bell = R.drawable.lucide_ic_bell
    val Camera = R.drawable.lucide_ic_camera
    val ChevronRight = R.drawable.lucide_ic_chevron_right
    val Plus = R.drawable.lucide_ic_plus
    val Search = R.drawable.lucide_ic_search
    val History = R.drawable.lucide_ic_history
    val Upload = R.drawable.lucide_ic_upload
    val ArrowRight = R.drawable.lucide_ic_arrow_right
    val Eye = R.drawable.lucide_ic_eye
    val EyeOff = R.drawable.lucide_ic_eye_off
    val Hammer = R.drawable.lucide_ic_hammer
    val Store = R.drawable.lucide_ic_store
    val UserRound = R.drawable.lucide_ic_user_round
}
