package com.vorder.control.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vorder.control.data.Brand
import com.vorder.control.data.Device
import com.vorder.control.data.DeviceState
import com.vorder.control.data.DeviceStore
import com.vorder.control.data.Kind
import com.vorder.control.data.RemoteKey
import com.vorder.control.drivers.AndroidTvDriver
import com.vorder.control.drivers.AtvIdentity
import com.vorder.control.drivers.AtvPairingSession
import com.vorder.control.drivers.Driver
import com.vorder.control.drivers.Drivers
import com.vorder.control.drivers.HueDriver
import com.vorder.control.net.Found
import com.vorder.control.net.Net
import com.vorder.control.net.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed class PairUi {
    data class Atv(val device: Device, val session: AtvPairingSession?, val error: String?, val starting: Boolean = false) : PairUi()
    data class Hue(val host: String, val name: String, val error: String?, val busy: Boolean = false) : PairUi()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DeviceStore(app)
    private val identity = lazy { AtvIdentity(app) }
    private val drivers = mutableMapOf<String, Driver>()

    private val _devices = MutableStateFlow(store.load())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _states = MutableStateFlow<Map<String, DeviceState>>(emptyMap())
    val states: StateFlow<Map<String, DeviceState>> = _states.asStateFlow()

    private val _dark = MutableStateFlow(store.dark)
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()

    private val _pair = MutableStateFlow<PairUi?>(null)
    val pair: StateFlow<PairUi?> = _pair.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Genera el certificado de cliente (Google TV) en segundo plano, una sola vez.
        viewModelScope.launch(Dispatchers.IO) { runCatching { identity.value } }
    }

    private fun toast(s: String) { _messages.tryEmit(s) }
    private fun err(e: Throwable) = e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar la acción"

    private fun driver(d: Device): Driver = drivers.getOrPut(d.id) {
        Drivers.create(d, identity) { k, v -> updateExtra(d.id, k, v) }
    }

    private fun persist() = store.save(_devices.value)

    private fun updateExtra(id: String, k: String, v: String) {
        _devices.update { list -> list.map { if (it.id == id) it.copy(extra = it.extra + (k to v)) else it } }
        persist()
    }

    // ───── estado ─────
    fun refresh(d: Device) {
        viewModelScope.launch {
            val st = try { withTimeout(4000) { driver(d).getState() } } catch (e: Exception) { DeviceState(online = false) }
            _states.update { it + (d.id to st) }
        }
    }

    fun refreshAll() = _devices.value.forEach { refresh(it) }

    private fun setLocal(id: String, f: (DeviceState) -> DeviceState) {
        _states.update { it + (id to f(it[id] ?: DeviceState(online = true))) }
    }

    // ───── acciones ─────
    fun toggle(d: Device) {
        val target = !(_states.value[d.id]?.on ?: false)
        setLocal(d.id) { it.copy(on = target, online = true) }
        viewModelScope.launch {
            try { driver(d).setPower(target) } catch (e: Exception) { toast(err(e)); refresh(d) }
        }
    }

    fun tvPower(d: Device) {
        viewModelScope.launch {
            try {
                val online = _states.value[d.id]?.online == true
                if (online) driver(d).key(RemoteKey.POWER) else driver(d).setPower(true)
                if (!online) toast("Enviando señal de encendido…")
            } catch (e: Exception) { toast(err(e)) }
        }
    }

    fun setBrightness(d: Device, pct: Int) {
        setLocal(d.id) { it.copy(brightness = pct, on = true, online = true) }
        viewModelScope.launch { try { driver(d).setBrightness(pct) } catch (e: Exception) { toast(err(e)) } }
    }

    fun setColor(d: Device, rgb: Int) {
        setLocal(d.id) { it.copy(on = true, online = true) }
        viewModelScope.launch {
            try {
                if (_states.value[d.id]?.on != true) driver(d).setPower(true)
                driver(d).setColor(rgb)
            } catch (e: Exception) { toast(err(e)) }
        }
    }

    fun sendKey(d: Device, k: RemoteKey) {
        viewModelScope.launch {
            try {
                if (k == RemoteKey.POWER) { tvPower(d); return@launch }
                driver(d).key(k)
            } catch (e: Exception) { toast(err(e)) }
        }
    }

    /** Abre el flujo de vinculación si la TV (Google TV) todavía no está vinculada. */
    fun needsPairing(d: Device) = d.brand == Brand.GOOGLE_TV && d.extra["paired"] != "1"

    // ───── dispositivos ─────
    fun addDevice(dev: Device) {
        _devices.update { it + dev }
        persist()
        refresh(dev)
        if (needsPairing(dev)) startAtvPairing(dev)
    }

    fun addManual(brand: Brand, name: String, host: String, mac: String) {
        viewModelScope.launch {
            var kind = brand.defaultKind
            val extra = mutableMapOf<String, String>()
            if (mac.isNotBlank()) extra["mac"] = mac.trim()
            when (brand) {
                Brand.TASMOTA -> {
                    val sts = Net.get("http://$host/cm?cmnd=Status%2011")
                    if (sts != null && (sts.contains("\"Dimmer\"") || sts.contains("\"Color\""))) kind = Kind.LIGHT
                }
                Brand.SHELLY -> {
                    val gen = Net.get("http://$host/shelly")?.let { runCatching { JSONObject(it).optInt("gen", 1) }.getOrNull() } ?: 1
                    extra["gen"] = gen.toString()
                }
                Brand.HUE -> { startHuePairing(host, name.ifBlank { "Puente Hue" }); return@launch }
                else -> {}
            }
            if (mac.isBlank() && kind == Kind.TV) Net.arpMac(host)?.let { extra["mac"] = it }
            addDevice(Device(name = name.ifBlank { brand.label }, brand = brand, kind = kind, host = host.trim(), extra = extra))
        }
    }

    fun edit(id: String, name: String, host: String, mac: String) {
        drivers.remove(id)?.close()
        _devices.update { list ->
            list.map {
                if (it.id != id) it else it.copy(
                    name = name.ifBlank { it.name }, host = host.trim().ifBlank { it.host },
                    extra = if (mac.isBlank()) it.extra - "mac" else it.extra + ("mac" to mac.trim())
                )
            }
        }
        persist()
        _devices.value.firstOrNull { it.id == id }?.let { refresh(it) }
    }

    fun remove(id: String) {
        drivers.remove(id)?.close()
        _devices.update { list -> list.filterNot { it.id == id } }
        _states.update { it - id }
        persist()
    }

    // ───── escaneo ─────
    fun startScan() {
        scanJob?.cancel()
        _found.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            try {
                Scanner.scan().collect { f ->
                    val known = _devices.value.any { it.host == f.host && it.brand == f.brand }
                    if (!known && _found.value.none { it.key == f.key }) _found.update { it + f }
                }
            } catch (e: Exception) { toast("No se pudo escanear la red") }
            finally { _scanning.value = false }
        }
    }

    fun stopScan() { scanJob?.cancel(); _scanning.value = false }

    fun addFound(f: Found) {
        _found.update { list -> list.filterNot { it.key == f.key } }
        if (f.isHueBridge) { startHuePairing(f.host, f.name); return }
        addDevice(Device(name = f.name, brand = f.brand, kind = f.kind, host = f.host, extra = f.extra))
    }

    // ───── vinculaciones ─────
    fun startAtvPairing(d: Device) {
        (_pair.value as? PairUi.Atv)?.session?.cancel()
        _pair.value = PairUi.Atv(d, null, null, starting = true)
        viewModelScope.launch {
            try {
                val s = AtvPairingSession.start(d.host, identity.value)
                _pair.value = PairUi.Atv(d, s, null)
            } catch (e: Exception) { _pair.value = PairUi.Atv(d, null, err(e)) }
        }
    }

    fun finishAtvPairing(code: String) {
        val ui = _pair.value as? PairUi.Atv ?: return
        val s = ui.session ?: return
        viewModelScope.launch {
            try {
                s.finish(code)
                updateExtra(ui.device.id, "paired", "1")
                _pair.value = null
                toast("TV vinculada ✓")
                refresh(ui.device)
            } catch (e: Exception) { _pair.value = PairUi.Atv(ui.device, null, err(e)) }
        }
    }

    fun startHuePairing(host: String, name: String) { _pair.value = PairUi.Hue(host, name, null) }

    fun tryHuePair() {
        val ui = _pair.value as? PairUi.Hue ?: return
        viewModelScope.launch {
            _pair.value = ui.copy(busy = true, error = null)
            HueDriver.pair(ui.host).onSuccess { user ->
                if (user == null) {
                    _pair.value = ui.copy(busy = false, error = "Presioná el botón del puente y tocá Continuar otra vez")
                } else {
                    val lights = HueDriver.listLights(ui.host, user)
                    lights.forEach { (id, n) ->
                        addDevice(Device(name = n, brand = Brand.HUE, kind = Kind.LIGHT, host = ui.host, extra = mapOf("user" to user, "lightId" to id)))
                    }
                    _pair.value = null
                    toast("${lights.size} luces Hue agregadas")
                }
            }.onFailure { _pair.value = ui.copy(busy = false, error = err(it)) }
        }
    }

    fun dismissPair() {
        (_pair.value as? PairUi.Atv)?.session?.cancel()
        _pair.value = null
    }

    fun toggleTheme() { _dark.update { !it }; store.dark = _dark.value }

    override fun onCleared() { drivers.values.forEach { it.close() } }
}
