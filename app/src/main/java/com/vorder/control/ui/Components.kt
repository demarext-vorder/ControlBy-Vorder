package com.vorder.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Botón redondo de vidrio con ícono (opcionalmente con color neón). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 26.dp,
    tint: Color? = null,
    glow: Color? = null,
    description: String? = null,
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(size)
            .glass(CircleShape, glow = glow)
            .clip(CircleShape)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint ?: p.text, modifier = Modifier.size(iconSize))
    }
}

/** Botón principal con degradado neón. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: List<Color> = listOf(Neon.Cyan, Neon.Violet, Neon.Magenta),
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(if (enabled) colors else colors.map { it.copy(alpha = 0.35f) }))
            .clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(PaddingValues(horizontal = 22.dp, vertical = 13.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Box(Modifier.size(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun SoftChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(Neon.Cyan.copy(alpha = 0.85f), Neon.Violet.copy(alpha = 0.85f))), shape) else Modifier.glass(shape))
            .clip(shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.White else p.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
