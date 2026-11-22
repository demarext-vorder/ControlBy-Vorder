package com.vorder.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
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
import com.vorder.control.data.Device
import com.vorder.control.data.DeviceState
import com.vorder.control.data.RemoteKey

@Composable
fun RemoteScreen(
    device: Device,
    state: DeviceState?,
    onBack: () -> Unit,
    onKey: (RemoteKey) -> Unit,
    onRepair: (() -> Unit)?,
) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Todo se calcula según el alto disponible para que entre sin scroll.
        val dpad = minOf(280.dp, maxWidth - 56.dp, (maxHeight - 330.dp).coerceAtLeast(170.dp))
        val rockerH = minOf(180.dp, maxHeight * 0.27f)
        val smallBtn = ((rockerH - 20.dp) / 3).coerceAtMost(56.dp)

        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, onBack, size = 46.dp, iconSize = 22.dp, description = "Volver")
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(device.name, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (state?.online == true) Neon.Lime else p.sub.copy(alpha = 0.5f)))
                        Text("  ${device.brand.label} · ${if (state?.online == true) "en línea" else "sin conexión"}", color = p.sub, fontSize = 12.5.sp)
                    }
                }
                if (onRepair != null) {
                    Text("Vincular", color = Neon.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onRepair() }.padding(10.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.PowerSettingsNew, { onKey(RemoteKey.POWER) }, size = 56.dp, iconSize = 28.dp, tint = Neon.Red, glow = Neon.Red, description = "Encender / apagar")
                GlassIconButton(Icons.Rounded.VolumeOff, { onKey(RemoteKey.MUTE) }, size = 50.dp, description = "Silenciar")
                GlassIconButton(Icons.Rounded.Menu, { onKey(RemoteKey.MENU) }, size = 50.dp, description = "Menú")
            }

            DPad(dpad, onKey)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Rocker("VOL", Icons.Rounded.VolumeUp, Neon.Cyan, rockerH, { onKey(RemoteKey.VOL_UP) }, { onKey(RemoteKey.VOL_DOWN) })
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassIconButton(Icons.Rounded.ArrowBack, { onKey(RemoteKey.BACK) }, size = smallBtn, iconSize = smallBtn * 0.5f, description = "Atrás")
                    GlassIconButton(Icons.Rounded.Home, { onKey(RemoteKey.HOME) }, size = smallBtn, iconSize = smallBtn * 0.5f, tint = Neon.Cyan, glow = Neon.Cyan, description = "Inicio")
                    GlassIconButton(Icons.Rounded.PlayArrow, { onKey(RemoteKey.PLAY_PAUSE) }, size = smallBtn, iconSize = smallBtn * 0.5f, description = "Play / pausa")
                }
                Rocker("CH", null, Neon.Magenta, rockerH, { onKey(RemoteKey.CH_UP) }, { onKey(RemoteKey.CH_DOWN) })
            }
        }
    }
}

@Composable
private fun DPad(size: Dp, onKey: (RemoteKey) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val keySize = size * 0.26f
    Box(Modifier.size(size).glass(CircleShape, strong = true), contentAlignment = Alignment.Center) {
        val arrow = LocalPalette.current.text
        @Composable
        fun key(icon: ImageVector, align: Alignment, k: RemoteKey) {
            Box(
                Modifier.align(align).padding(size * 0.04f).size(keySize).clip(CircleShape)
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(k) },
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = arrow, modifier = Modifier.size(keySize * 0.62f)) }
        }
        key(Icons.Rounded.KeyboardArrowUp, Alignment.TopCenter, RemoteKey.UP)
        key(Icons.Rounded.KeyboardArrowDown, Alignment.BottomCenter, RemoteKey.DOWN)
        key(Icons.Rounded.KeyboardArrowLeft, Alignment.CenterStart, RemoteKey.LEFT)
        key(Icons.Rounded.KeyboardArrowRight, Alignment.CenterEnd, RemoteKey.RIGHT)
        Box(
            Modifier.size(size * 0.36f).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onKey(RemoteKey.OK) },
            contentAlignment = Alignment.Center
        ) { Text("OK", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Rocker(label: String, icon: ImageVector?, accent: Color, height: Dp, onUp: () -> Unit, onDown: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(40.dp)
    Column(
        Modifier.width(84.dp).height(height).glass(shape, strong = true).clip(shape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onUp() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(30.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = p.sub, modifier = Modifier.size(20.dp))
            else Text(label, color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDown() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, null, tint = accent, modifier = Modifier.size(30.dp))
        }
    }
}
