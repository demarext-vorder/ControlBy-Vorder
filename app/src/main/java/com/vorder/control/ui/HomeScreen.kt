@file:OptIn(ExperimentalFoundationApi::class)

package com.vorder.control.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vorder.control.data.Brand
import com.vorder.control.data.Device
import com.vorder.control.data.DeviceState
import com.vorder.control.data.Kind

fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.TV -> Icons.Rounded.Tv
    Kind.LIGHT -> Icons.Rounded.Lightbulb
    Kind.RELAY -> Icons.Rounded.Bolt
}

fun kindColor(k: Kind): Color = when (k) {
    Kind.TV -> Neon.Cyan
    Kind.LIGHT -> Neon.Amber
    Kind.RELAY -> Neon.Lime
}

@Composable
fun HomeScreen(
    devices: List<Device>,
    states: Map<String, DeviceState>,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenAdd: () -> Unit,
    onOpenTv: (Device) -> Unit,
    onToggle: (Device) -> Unit,
    onTvPower: (Device) -> Unit,
    onTune: (Device) -> Unit,
    onEdit: (Device) -> Unit,
    onDelete: (Device) -> Unit,
    onRepair: (Device) -> Unit,
) {
    val p = LocalPalette.current
    var filter by remember { mutableStateOf<Kind?>(null) }
    val shown = devices.filter { filter == null || it.kind == filter }
    val activeCount = devices.count { d -> states[d.id]?.let { it.on == true || (d.kind == Kind.TV && it.online) } == true }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Control", color = p.text, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 22.dp, height = 3.dp).clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Magenta))))
                                Text("  by VORDER", color = if (p.dark) Neon.Cyan else Color(0xFF008CA3), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                            }
                        }
                        GlassIconButton(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, onToggleTheme, size = 46.dp, iconSize = 22.dp, description = "Cambiar tema")
                        Spacer(Modifier.size(10.dp))
                        GlassIconButton(Icons.Rounded.Add, onOpenAdd, size = 46.dp, iconSize = 24.dp, tint = Neon.Cyan, glow = Neon.Cyan, description = "Agregar")
                    }
                    Text(
                        if (devices.isEmpty()) "Sumá tu primer dispositivo" else "${devices.size} dispositivos · $activeCount activos",
                        color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)
                    )
                    if (devices.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                            item { SoftChip("Todo", filter == null, { filter = null }) }
                            items(Kind.values().toList()) { k -> SoftChip(k.label, filter == k, { filter = if (filter == k) null else k }) }
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                item(span = { GridItemSpan(2) }) { EmptyState(onOpenAdd) }
            }

            items(shown, key = { it.id }) { d ->
                DeviceCard(
                    d, states[d.id],
                    onClick = { if (d.kind == Kind.TV) onOpenTv(d) else onToggle(d) },
                    onPrimary = { if (d.kind == Kind.TV) onTvPower(d) else onToggle(d) },
                    onTune = { onTune(d) },
                    onEdit = { onEdit(d) }, onDelete = { onDelete(d) }, onRepair = { onRepair(d) },
                )
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp)) {
            NeonButton("Buscar dispositivos", onOpenAdd, icon = Icons.Rounded.Search)
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(top = 30.dp).glass(RoundedCornerShape(30.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Bolt, null, tint = Neon.Cyan, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(12.dp))
        Text("Todo en un solo lugar", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Conectá el celular al mismo Wi-Fi que tus TVs, luces y relés. Buscamos todo automáticamente.",
            color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        NeonButton("Buscar en mi red", onAdd, icon = Icons.Rounded.Search)
    }
}

@Composable
private fun DeviceCard(
    d: Device,
    st: DeviceState?,
    onClick: () -> Unit,
    onPrimary: () -> Unit,
    onTune: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRepair: () -> Unit,
) {
    val p = LocalPalette.current
    val accent = kindColor(d.kind)
    val isTv = d.kind == Kind.TV
    val active = if (isTv) st?.online == true else st?.on == true
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)

    val status = when {
        st == null -> "Conectando…"
        isTv -> if (st.online) "En línea" else "Apagada / sin conexión"
        !st.online -> "Sin conexión"
        st.on == true -> if (st.brightness != null) "Encendida · ${st.brightness}%" else "Encendida"
        else -> "Apagada"
    }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(shape, glow = if (active) accent else null)
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(
                        if (active) Brush.linearGradient(listOf(accent.copy(alpha = 0.95f), Neon.Magenta.copy(alpha = 0.75f)))
                        else Brush.linearGradient(listOf(p.glassStrong, p.glass))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(kindIcon(d.kind), null, tint = if (active) Color(0xFF0A0F2C) else p.text, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
                if (isTv) {
                    GlassIconButton(Icons.Rounded.PowerSettingsNew, onPrimary, size = 40.dp, iconSize = 20.dp, tint = if (active) Neon.Red else p.sub)
                } else {
                    Switch(
                        checked = st?.on == true, onCheckedChange = { onPrimary() }, enabled = st?.online != false,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = accent,
                            uncheckedThumbColor = p.sub, uncheckedTrackColor = p.glass, uncheckedBorderColor = p.edgeLo,
                            checkedBorderColor = Color.Transparent
                        )
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(d.name, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (st?.online == true) (if (active) accent else Neon.Lime) else p.sub.copy(alpha = 0.5f)))
                Text("  $status", color = p.sub, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (d.kind == Kind.LIGHT && d.brand != Brand.SHELLY) {
                    Icon(Icons.Rounded.Tune, "Ajustes", tint = p.sub,
                        modifier = Modifier.size(26.dp).clip(CircleShape).combinedClickable(onClick = onTune).padding(2.dp))
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() })
            if (d.brand == Brand.GOOGLE_TV) DropdownMenuItem(text = { Text("Vincular de nuevo") }, onClick = { menu = false; onRepair() })
            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
        }
    }
}
