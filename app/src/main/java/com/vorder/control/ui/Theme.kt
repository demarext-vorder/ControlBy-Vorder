package com.vorder.control.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object Neon {
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF3DCB)
    val Lime = Color(0xFFB6FF3B)
    val Violet = Color(0xFF8B5CFF)
    val Amber = Color(0xFFFFC145)
    val Red = Color(0xFFFF4D6D)
    val Blue = Color(0xFF3D7BFF)
}

data class Palette(
    val dark: Boolean,
    val bg: List<Color>,
    val blobs: List<Color>,
    val glass: Color,
    val glassStrong: Color,
    val edgeHi: Color,
    val edgeLo: Color,
    val text: Color,
    val sub: Color,
    val dialog: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg = listOf(Color(0xFF0A0F2C), Color(0xFF1B1050), Color(0xFF2A0F4F)),
    blobs = listOf(Neon.Cyan.copy(alpha = 0.35f), Neon.Magenta.copy(alpha = 0.30f), Neon.Violet.copy(alpha = 0.40f)),
    glass = Color.White.copy(alpha = 0.08f),
    glassStrong = Color.White.copy(alpha = 0.16f),
    edgeHi = Color.White.copy(alpha = 0.40f),
    edgeLo = Color.White.copy(alpha = 0.06f),
    text = Color.White,
    sub = Color.White.copy(alpha = 0.66f),
    dialog = Color(0xFF1A1448),
)

val LightPalette = Palette(
    dark = false,
    bg = listOf(Color(0xFFF2F7FF), Color(0xFFEFE9FF), Color(0xFFFFEAF7)),
    blobs = listOf(Neon.Cyan.copy(alpha = 0.40f), Neon.Magenta.copy(alpha = 0.28f), Neon.Violet.copy(alpha = 0.30f)),
    glass = Color.White.copy(alpha = 0.55f),
    glassStrong = Color.White.copy(alpha = 0.80f),
    edgeHi = Color.White.copy(alpha = 0.95f),
    edgeLo = Color(0xFF8B5CFF).copy(alpha = 0.18f),
    text = Color(0xFF1A1F3D),
    sub = Color(0xFF5B6288),
    dialog = Color(0xFFF7F4FF),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun ControlTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = Neon.Cyan, secondary = Neon.Magenta, surface = p.dialog, onSurface = p.text)
    else lightColorScheme(primary = Color(0xFF009BB3), secondary = Neon.Magenta, surface = p.dialog, onSurface = p.text)
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Fondo "aurora": degradado + manchas de neón que se desplazan lentamente. */
@Composable
fun AuroraBackground(content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(1f, 0f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(p.bg))
            .drawBehind {
                val w = size.width; val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float) =
                    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
                blob(p.blobs[0], w * (0.15f + 0.25f * a), h * (0.10f + 0.10f * b), w * 0.75f)
                blob(p.blobs[1], w * (0.90f - 0.25f * b), h * (0.45f + 0.10f * a), w * 0.70f)
                blob(p.blobs[2], w * (0.30f + 0.20f * b), h * (0.92f - 0.10f * a), w * 0.85f)
            }
    ) { content() }
}

/** Efecto "vidrio": fondo translúcido + borde con brillo + (opcional) halo de neón. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), glow: Color? = null, strong: Boolean = false): Modifier {
    val p = LocalPalette.current
    val fill = if (strong) p.glassStrong else p.glass
    val edge = if (glow != null) Brush.linearGradient(listOf(glow.copy(alpha = 0.95f), glow.copy(alpha = 0.25f)))
    else Brush.linearGradient(listOf(p.edgeHi, p.edgeLo))
    return this
        .then(if (glow != null) Modifier.drawBehind {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.28f), Color.Transparent), center, size.maxDimension * 0.75f), size.maxDimension * 0.75f, center)
        } else Modifier)
        .background(Brush.verticalGradient(listOf(fill.copy(alpha = (fill.alpha * 1.5f).coerceAtMost(1f)), fill)), shape)
        .border(1.dp, edge, shape)
}
