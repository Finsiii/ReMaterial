package com.rematerial.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.rematerial.app.core.designsystem.RematerialColors
import com.rematerial.app.core.designsystem.RematerialTypography

private val LightColors = lightColorScheme(
    primary = RematerialColors.DeepForest,
    onPrimary = RematerialColors.Surface,
    secondary = RematerialColors.Bronze,
    onSecondary = RematerialColors.Surface,
    background = RematerialColors.Canvas,
    onBackground = RematerialColors.Ink,
    surface = RematerialColors.Surface,
    onSurface = RematerialColors.Ink,
    outline = RematerialColors.Line,
)
private val DarkColors = darkColorScheme(
    primary = RematerialColors.Bronze,
    onPrimary = RematerialColors.ForestDark,
    background = RematerialColors.ForestDark,
    onBackground = RematerialColors.Surface,
    surface = RematerialColors.DeepForest,
    onSurface = RematerialColors.Surface,
)

@Composable
fun ReMaterialTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RematerialTypography,
        shapes = androidx.compose.material3.Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
