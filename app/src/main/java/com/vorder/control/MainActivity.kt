package com.vorder.control

import android.graphics.Color as AColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vorder.control.data.Device
import com.vorder.control.data.Kind
import com.vorder.control.ui.AddSheet
import com.vorder.control.ui.AuroraBackground
import com.vorder.control.ui.ControlTheme
import com.vorder.control.ui.DeleteDialog
import com.vorder.control.ui.EditDialog
import com.vorder.control.ui.HomeScreen
import com.vorder.control.ui.LightSheet
import com.vorder.control.ui.ManualAddDialog
import com.vorder.control.ui.PairDialog
import com.vorder.control.ui.RemoteScreen
import com.vorder.control.vm.MainViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark by vm.dark.collectAsState()
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT),
                )
            }
            ControlTheme(dark) { AuroraBackground { App(vm) } }
        }
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val devices by vm.devices.collectAsState()
    val states by vm.states.collectAsState()
    val dark by vm.dark.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val found by vm.found.collectAsState()
    val pair by vm.pair.collectAsState()
    val ctx = LocalContext.current

    var remoteId by remember { mutableStateOf<String?>(null) }
    var lightId by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Device?>(null) }
    var deleting by remember { mutableStateOf<Device?>(null) }

    // Mensajes de error / avisos
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }

    // Actualiza el estado de los dispositivos mientras la app está visible
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.refreshAll(); delay(8000) }
        }
    }

    val remote = devices.firstOrNull { it.id == remoteId }
    val light = devices.firstOrNull { it.id == lightId }

    if (remote != null) {
        BackHandler { remoteId = null }
        RemoteScreen(
            device = remote, state = states[remote.id],
            onBack = { remoteId = null },
            onKey = { vm.sendKey(remote, it) },
            onRepair = if (remote.brand == com.vorder.control.data.Brand.GOOGLE_TV) ({ vm.startAtvPairing(remote) }) else null,
        )
    } else {
        HomeScreen(
            devices = devices, states = states, dark = dark,
            onToggleTheme = vm::toggleTheme,
            onOpenAdd = { showAdd = true; vm.startScan() },
            onOpenTv = { d -> if (vm.needsPairing(d)) vm.startAtvPairing(d) else remoteId = d.id },
            onToggle = vm::toggle, onTvPower = vm::tvPower,
            onTune = { lightId = it.id },
            onEdit = { editing = it }, onDelete = { deleting = it },
            onRepair = { vm.startAtvPairing(it) },
        )
    }

    if (light != null) {
        LightSheet(
            device = light, state = states[light.id],
            onDismiss = { lightId = null },
            onToggle = { vm.toggle(light) },
            onBrightness = { vm.setBrightness(light, it) },
            onColor = { vm.setColor(light, it) },
        )
    }

    if (showAdd) {
        AddSheet(
            scanning = scanning, found = found,
            onScan = vm::startScan,
            onAdd = vm::addFound,
            onManual = { showManual = true },
            onDismiss = { showAdd = false; vm.stopScan() },
        )
    }

    if (showManual) {
        ManualAddDialog(
            onDismiss = { showManual = false },
            onAdd = { b, n, h, m -> showManual = false; showAdd = false; vm.stopScan(); vm.addManual(b, n, h, m) },
        )
    }

    editing?.let { d ->
        EditDialog(d, onDismiss = { editing = null }, onSave = { n, h, m -> vm.edit(d.id, n, h, m); editing = null })
    }
    deleting?.let { d ->
        DeleteDialog(d, onDismiss = { deleting = null }, onConfirm = {
            if (remoteId == d.id) remoteId = null
            vm.remove(d.id); deleting = null
        })
    }
    pair?.let { ui ->
        PairDialog(
            ui, onDismiss = vm::dismissPair,
            onAtvCode = vm::finishAtvPairing, onAtvRetry = vm::startAtvPairing, onHueContinue = vm::tryHuePair,
        )
    }
}
