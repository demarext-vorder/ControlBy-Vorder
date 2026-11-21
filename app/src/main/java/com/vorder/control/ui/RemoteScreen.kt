package com.vorder.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.PowerSettingsNew, { onKey(RemoteKey.POWER) }, size = 58.dp, iconSize = 28.dp, tint = Neon.Red, glow = Neon.Red, description = "Encender / apagar")
            GlassIconButton(Icons.Rounded.VolumeOff, { onKey(RemoteKey.MUTE) }, size = 52.dp, description = "Silenciar")
            GlassIconButton(Icons.Rounded.Menu, { onKey(RemoteKey.MENU) }, size = 52.dp, description = "Menú")
        }

        Spacer(Modifier.height(26.dp))
        DPad(onKey)

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.ArrowBack, { onKey(RemoteKey.BACK) }, size = 60.dp, description = "Atrás")
            GlassIconButton(Icons.Rounded.Home, { onKey(RemoteKey.HOME) }, size = 60.dp, tint = Neon.Cyan, glow = Neon.Cyan, description = "Inicio")
            GlassIconButton(Icons.Rounded.PlayArrow, { onKey(RemoteKey.PLAY_PAUSE) }, size = 60.dp, description = "Play / pausa")
        }

        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Rocker("VOL", Icons.Rounded.VolumeUp, Neon.Cyan, { onKey(RemoteKey.VOL_UP) }, { onKey(RemoteKey.VOL_DOWN) })
            Rocker("CH", null, Neon.Magenta, { onKey(RemoteKey.CH_UP) }, { onKey(RemoteKey.CH_DOWN) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DPad(onKey: (RemoteKey) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(Modifier.size(260.dp).glass(CircleShape, strong = true), contentAlignment = Alignment.Center) {
        val arrow = LocalPalette.current.text
        @Composable
        fun key(icon: ImageVector, align: Alignment, k: RemoteKey) {
            Box(
                Modifier.align(align).padding(14.dp).size(62.dp).clip(CircleShape)
                    .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(k) },
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = arrow, modifier = Modifier.size(38.dp)) }
        }
        key(Icons.Rounded.KeyboardArrowUp, Alignment.TopCenter, RemoteKey.UP)
        key(Icons.Rounded.KeyboardArrowDown, Alignment.BottomCenter, RemoteKey.DOWN)
        key(Icons.Rounded.KeyboardArrowLeft, Alignment.CenterStart, RemoteKey.LEFT)
        key(Icons.Rounded.KeyboardArrowRight, Alignment.CenterEnd, RemoteKey.RIGHT)
        Box(
            Modifier.size(92.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta)))
                .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onKey(RemoteKey.OK) },
            contentAlignment = Alignment.Center
        ) { Text("OK", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Rocker(label: String, icon: ImageVector?, accent: Color, onUp: () -> Unit, onDown: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(40.dp)
    Column(
        Modifier.width(92.dp).height(190.dp).glass(shape, strong = true).clip(shape),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onUp() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = accent, modifier = Modifier.size(32.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = p.sub, modifier = Modifier.size(22.dp))
            else Text(label, color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth().clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onDown() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, null, tint = accent, modifier = Modifier.size(32.dp))
        }
    }
}
