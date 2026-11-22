@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.vorder.control.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.vorder.control.data.Brand
import com.vorder.control.data.Device
import com.vorder.control.data.DeviceState
import com.vorder.control.data.Kind
import com.vorder.control.net.Found
import com.vorder.control.vm.PairUi

// ───────────────────────── Luces ─────────────────────────
private val SWATCHES = listOf(
    0xFFFFB347.toInt() to "Cálida", 0xFFFFF1E0.toInt() to "Neutra", 0xFFCFEAFF.toInt() to "Fría",
    0xFF00E5FF.toInt() to "Cian", 0xFF3D7BFF.toInt() to "Azul", 0xFF8B5CFF.toInt() to "Violeta",
    0xFFFF3DCB.toInt() to "Magenta", 0xFFFF4D6D.toInt() to "Rojo", 0xFFFFC145.toInt() to "Ámbar",
    0xFFB6FF3B.toInt() to "Lima", 0xFF22E58B.toInt() to "Verde",
)

@Composable
fun LightSheet(
    device: Device,
    state: DeviceState?,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onBrightness: (Int) -> Unit,
    onColor: (Int) -> Unit,
) {
    val p = LocalPalette.current
    var level by remember(device.id) { mutableFloatStateOf((state?.brightness ?: 100).toFloat()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.dialog,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(device.brand.label, color = p.sub, fontSize = 13.sp)
                }
                Switch(
                    checked = state?.on == true, onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Neon.Amber)
                )
            }
            Spacer(Modifier.height(22.dp))
            Text("Brillo · ${level.toInt()}%", color = p.sub, fontSize = 13.sp)
            Slider(
                value = level, onValueChange = { level = it },
                onValueChangeFinished = { onBrightness(level.toInt().coerceIn(1, 100)) },
                valueRange = 1f..100f,
                colors = SliderDefaults.colors(thumbColor = Neon.Cyan, activeTrackColor = Neon.Cyan, inactiveTrackColor = p.glassStrong)
            )
            Spacer(Modifier.height(14.dp))
            Text("Color", color = p.sub, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SWATCHES.forEach { (c, label) ->
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(c))
                            .border(2.dp, p.edgeHi, CircleShape)
                            .clickable { onColor(c) },
                    )
                }
            }
            Text("Los colores dependen de que la lámpara sea RGB.", color = p.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

// ───────────────────────── Buscar / agregar ─────────────────────────
@Composable
fun AddSheet(
    scanning: Boolean,
    info: String?,
    found: List<Found>,
    onScan: () -> Unit,
    onAdd: (Found) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.dialog,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Buscar dispositivos", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("El celular y los aparatos tienen que estar en el mismo Wi-Fi. Las TVs deben estar encendidas.", color = p.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Neon.Cyan, strokeWidth = 2.5.dp)
                    Text("  Escaneando la red…", color = p.text, fontSize = 15.sp)
                } else {
                    NeonButton(if (found.isEmpty()) "Escanear red" else "Escanear de nuevo", onScan, icon = Icons.Rounded.Search)
                }
            }
            if (info != null) Text(info, color = p.sub, fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(16.dp))

            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                found.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(kindColor(f.kind).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                            Icon(kindIcon(f.kind), null, tint = kindColor(f.kind), modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(f.name, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                            Text("${f.brand.label} · ${f.host}", color = p.sub, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(onClick = { onAdd(f) }) { Text("Agregar", color = Neon.Cyan, fontWeight = FontWeight.Bold) }
                    }
                }
                if (!scanning && found.isEmpty()) {
                    Text("Todavía no hay resultados. Si algo no aparece (por ejemplo, Hue o dispositivos con la red aislada), agregalo a mano.", color = p.sub, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onManual) { Text("Agregar manualmente (con IP)", color = Neon.Cyan) }
        }
    }
}

private fun hintFor(b: Brand) = when (b) {
    Brand.LG -> "La TV mostrará un aviso para aceptar la conexión la primera vez."
    Brand.SAMSUNG -> "La TV mostrará un aviso para permitir el dispositivo la primera vez."
    Brand.GOOGLE_TV -> "Vas a ingresar un código de 6 caracteres que aparece en la TV."
    Brand.WIZ -> "En la app WiZ: Ajustes → Privacidad → activar 'Comunicación local'."
    Brand.YEELIGHT -> "En la app Yeelight activá 'Control LAN' en la lámpara."
    Brand.HUE -> "Vas a tener que presionar el botón del puente Hue."
    Brand.TASMOTA -> "Dispositivo con firmware Tasmota (sin contraseña web)."
    Brand.SHELLY -> "Shelly en la red local, sin contraseña de acceso."
    Brand.SONOFF -> "Sonoff en 'Modo DIY' con la API LAN activa (puerto 8081)."
}

@Composable
fun ManualAddDialog(onDismiss: () -> Unit, onAdd: (Brand, String, String, String) -> Unit) {
    val p = LocalPalette.current
    var brand by remember { mutableStateOf(Brand.LG) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.dialog,
        title = { Text("Agregar dispositivo", color = p.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Brand.values().forEach { b -> SoftChip(b.label.substringBefore(" ("), brand == b, { brand = b }) }
                }
                Text(hintFor(brand), color = p.sub, fontSize = 12.5.sp)
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Dirección IP (ej. 192.168.0.25)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                if (brand.defaultKind == Kind.TV) {
                    OutlinedTextField(mac, { mac = it }, label = { Text("MAC (opcional, para encender)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (host.isNotBlank()) onAdd(brand, name, host.trim(), mac) }, enabled = host.isNotBlank()) { Text("Agregar", color = Neon.Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = p.sub) } },
    )
}

@Composable
fun EditDialog(device: Device, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(device.name) }
    var host by remember { mutableStateOf(device.host) }
    var mac by remember { mutableStateOf(device.extra["mac"].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.dialog,
        title = { Text("Editar", color = p.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(host, { host = it }, label = { Text("Dirección IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (device.kind == Kind.TV) {
                    OutlinedTextField(mac, { mac = it }, label = { Text("MAC (para encender por red)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, host, mac) }) { Text("Guardar", color = Neon.Cyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = p.sub) } },
    )
}

@Composable
fun DeleteDialog(device: Device, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.dialog,
        title = { Text("¿Eliminar ${device.name}?", color = p.text) },
        text = { Text("Solo se quita de esta app; el dispositivo no se modifica.", color = p.sub) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Eliminar", color = Neon.Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = p.sub) } },
    )
}

// ───────────────────────── Vinculaciones ─────────────────────────
@Composable
fun PairDialog(
    ui: PairUi,
    onDismiss: () -> Unit,
    onAtvCode: (String) -> Unit,
    onAtvRetry: (Device) -> Unit,
    onHueContinue: () -> Unit,
) {
    val p = LocalPalette.current
    when (ui) {
        is PairUi.Atv -> {
            var code by remember(ui.session) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = p.dialog,
                title = { Text("Vincular ${ui.device.name}", color = p.text) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            ui.starting -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = Neon.Cyan, strokeWidth = 2.5.dp)
                                Text("  Contactando la TV…", color = p.sub)
                            }
                            ui.session != null -> {
                                Text("Mirá la pantalla de la TV: aparece un código de 6 caracteres. Escribilo acá.", color = p.sub)
                                OutlinedTextField(
                                    code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) },
                                    singleLine = true, label = { Text("Código") }, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        ui.error?.let { Text(it, color = Neon.Red, fontSize = 13.sp) }
                    }
                },
                confirmButton = {
                    when {
                        ui.session != null -> TextButton(onClick = { onAtvCode(code) }, enabled = code.length == 6) { Text("Vincular", color = Neon.Cyan) }
                        !ui.starting -> TextButton(onClick = { onAtvRetry(ui.device) }) { Text("Reintentar", color = Neon.Cyan) }
                        else -> {}
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = p.sub) } },
            )
        }
        is PairUi.Hue -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = p.dialog,
            title = { Text("Vincular puente Hue", color = p.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Presioná el botón redondo del puente Hue.\n2. Tocá Continuar (tenés 30 segundos).", color = p.sub)
                    if (ui.busy) CircularProgressIndicator(Modifier.size(22.dp), color = Neon.Cyan, strokeWidth = 2.5.dp)
                    ui.error?.let { Text(it, color = Neon.Red, fontSize = 13.sp) }
                }
            },
            confirmButton = { TextButton(onClick = onHueContinue, enabled = !ui.busy) { Text("Continuar", color = Neon.Cyan) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = p.sub) } },
        )
    }
}
