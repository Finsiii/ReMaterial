package com.rematerial.app.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared footprint used by every floating dock and by screens reserving space for it. */
object RematerialDockMetrics {
    val horizontalPadding = 0.dp
    val outerVerticalPadding = 0.dp
    val surfaceHeight = 64.dp
    val bottomGap = 0.dp
    val minHitTarget = 48.dp
    val reservedBottom = surfaceHeight + outerVerticalPadding + bottomGap

    fun contentBottomPadding(navigationBarInset: Dp): Dp = reservedBottom + navigationBarInset
    fun screenBottomPadding(navigationBarInset: Dp, dockVisible: Boolean): Dp =
        navigationBarInset + if (dockVisible) reservedBottom else 24.dp
}

enum class HorizontalPageMotion { FORWARD, BACKWARD }

fun horizontalPageMotion(initialPosition: Int, targetPosition: Int): HorizontalPageMotion =
    if (targetPosition >= initialPosition) HorizontalPageMotion.FORWARD else HorizontalPageMotion.BACKWARD

internal fun dockUsesFilledSelection(destination: DockDestination, selected: Boolean): Boolean =
    selected && destination == DockDestination.Scan

@Composable
fun RematerialIcon(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun RematerialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Int? = null,
) {
    val background = if (enabled) RematerialColors.DeepForest else RematerialColors.Line
    val foreground = if (enabled) RematerialColors.Surface else RematerialColors.Muted
    Surface(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text },
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leadingIcon?.let {
                RematerialIcon(it, null, Modifier.size(18.dp), foreground)
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun RematerialField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = RematerialColors.Muted,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { contentDescription = "Label $label" },
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = RematerialColors.Ink),
            cursorBrush = SolidColor(RematerialColors.Bronze),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RematerialColors.Surface)
                .border(BorderStroke(1.dp, RematerialColors.Line), RoundedCornerShape(12.dp))
                .semantics { contentDescription = label }
                .padding(horizontal = 16.dp, vertical = 15.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = RematerialColors.Muted)
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
fun RematerialTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionIcon: Int? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { callback ->
            Box(
                modifier = Modifier
                    .size(RematerialDockMetrics.minHitTarget)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(role = Role.Button, onClick = callback)
                    .semantics { contentDescription = "Kembali" },
                contentAlignment = Alignment.Center,
            ) {
                RematerialIcon(RematerialIcons.Back, null, Modifier.size(20.dp), RematerialColors.Ink)
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = RematerialColors.Ink,
            modifier = Modifier.weight(1f),
        )
        if (actionIcon != null && onAction != null) {
            Box(
                modifier = Modifier
                    .size(RematerialDockMetrics.minHitTarget)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .semantics { contentDescription = actionDescription ?: "Aksi" },
                contentAlignment = Alignment.Center,
            ) {
                RematerialIcon(actionIcon, null, Modifier.size(20.dp), RematerialColors.Ink)
            }
        }
    }
}

@Composable
fun RematerialListRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingIcon: Int? = null,
    trailingIcon: Int? = RematerialIcons.ChevronRight,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(vertical = 12.dp)
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        leadingIcon?.let {
            RematerialIcon(it, null, Modifier.size(20.dp), RematerialColors.DeepForest)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RematerialColors.Ink)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
        }
        trailingIcon?.let { RematerialIcon(it, null, Modifier.size(18.dp), RematerialColors.Muted) }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = RematerialColors.Ink)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted) }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = RematerialColors.DeepForest,
                modifier = Modifier.sizeIn(minHeight = 44.dp).clickable(role = Role.Button, onClick = onAction).padding(start = 12.dp, top = 12.dp),
            )
        }
    }
}

@Composable
fun CompactCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = RematerialColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, RematerialColors.Line),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = RematerialColors.Muted, modifier = Modifier.weight(1f))
        Text(value, style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, color = if (emphasized) RematerialColors.DeepForest else RematerialColors.Ink)
    }
}

@Composable
fun RematerialProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                val safeProgress = progress.coerceIn(0f, 1f)
                contentDescription = "Kemajuan ${(safeProgress * 100).toInt()} persen"
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
            },
    ) {
        val fraction = progress.coerceIn(0f, 1f)
        drawRoundRect(RematerialColors.Line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f))
        drawRoundRect(
            RematerialColors.Bronze,
            size = size.copy(width = size.width * fraction),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
        )
    }
}

@Composable
fun RematerialDock(
    selected: DockDestination,
    onDestinationSelected: (DockDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = RematerialDockMetrics.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(modifier = Modifier.fillMaxWidth().height(RematerialDockMetrics.surfaceHeight), color = RematerialColors.Surface) {
            Column {
                HorizontalDivider(color = RematerialColors.Line)
                Row(
                modifier = Modifier.fillMaxWidth().weight(1f).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DockDestination.entries.forEach { destination ->
                    val active = selected == destination
                    val filled = dockUsesFilledSelection(destination, active)
                    val activeColor by animateColorAsState(
                        targetValue = if (filled) RematerialColors.DeepForest else Color.Transparent,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 650f),
                        label = "dock-active-color",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .sizeIn(minHeight = RematerialDockMetrics.minHitTarget)
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = { onDestinationSelected(destination) },
                            )
                            .semantics { contentDescription = destination.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val foreground = if (active && destination == DockDestination.Scan) RematerialColors.Surface else if (active) RematerialColors.DeepForest else RematerialColors.Muted
                        Box(Modifier.width(28.dp).height(2.dp).background(if (active && !filled) RematerialColors.DeepForest else Color.Transparent))
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(width = 34.dp, height = 28.dp).clip(RoundedCornerShape(9.dp)).background(activeColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            RematerialIcon(destination.icon, null, Modifier.size(20.dp), foreground)
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(destination.label, style = MaterialTheme.typography.labelSmall, color = foreground)
                    }
                }
            }
            }
        }
    }
}
