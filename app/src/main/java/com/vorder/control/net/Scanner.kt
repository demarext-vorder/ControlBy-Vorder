package com.vorder.control.net

import com.vorder.control.data.Brand
import com.vorder.control.data.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

data class Found(
    val brand: Brand,
    val kind: Kind,
    val name: String,
    val host: String,
    val extra: Map<String, String> = emptyMap(),
    val isHueBridge: Boolean = false,
) {
    val key get() = "${brand.name}@$host"
}

object Scanner {
    private val PORTS = listOf(8001, 3000, 6466, 80, 8081, 55443)

    fun scan(): Flow<Found> = channelFlow {
        val ip = Net.localIp() ?: return@channelFlow
        val base = ip.substringBeforeLast('.')
        val gate = Semaphore(96)

        // 1) WiZ: broadcast UDP
        launch(Dispatchers.IO) {
            try {
                DatagramSocket().use { s ->
                    s.broadcast = true
                    s.soTimeout = 700
                    val msg = """{"method":"getPilot","params":{}}""".toByteArray()
                    for (t in listOf("255.255.255.255", "$base.255")) {
                        s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(t), 38899))
                    }
                    val end = System.currentTimeMillis() + 3500
                    val seen = mutableSetOf<String>()
                    while (System.currentTimeMillis() < end) {
                        try {
                            val p = DatagramPacket(ByteArray(2048), 2048)
                            s.receive(p)
                            val host = p.address.hostAddress ?: continue
                            if (host == ip || !seen.add(host)) continue
                            val mac = runCatching { JSONObject(String(p.data, 0, p.length)).getJSONObject("result").optString("mac") }.getOrDefault("")
                            send(Found(Brand.WIZ, Kind.LIGHT, "Luz WiZ ${host.substringAfterLast('.')}", host, mapOf("mac" to mac)))
                        } catch (e: SocketTimeoutException) { /* seguir esperando */ }
                    }
                }
            } catch (_: Exception) { }
        }

        // 2) Escaneo de puertos de la subred /24
        for (i in 1..254) {
            val host = "$base.$i"
            if (host == ip) continue
            launch {
                val open = gate.withPermit { PORTS.filter { Net.portOpen(host, it, 450) }.toSet() }
                if (open.isNotEmpty()) identify(host, open)?.forEach { send(it) }
            }
        }
    }

    private suspend fun identify(host: String, open: Set<Int>): List<Found>? = coroutineScope {
        val out = mutableListOf<Found>()
        val mac = Net.arpMac(host)
        val mx = if (mac != null) mapOf("mac" to mac) else emptyMap()

        if (8001 in open) {
            val info = Net.get("http://$host:8001/api/v2/")?.let { runCatching { JSONObject(it) }.getOrNull() }
            val dev = info?.optJSONObject("device")
            if (dev != null) {
                val n = dev.optString("name").replace(Regex("^\\[[^\\]]*\\]\\s*"), "").ifBlank { "TV Samsung" }
                out += Found(Brand.SAMSUNG, Kind.TV, n, host, mx)
            }
        }
        if (3000 in open && 8001 !in open) out += Found(Brand.LG, Kind.TV, "TV LG ${host.substringAfterLast('.')}", host, mx)
        if (6466 in open) out += Found(Brand.GOOGLE_TV, Kind.TV, "Google TV ${host.substringAfterLast('.')}", host, mx)
        if (55443 in open) out += Found(Brand.YEELIGHT, Kind.LIGHT, "Yeelight ${host.substringAfterLast('.')}", host)

        if (8081 in open) {
            val r = Net.send("POST", "http://$host:8081/zeroconf/info", """{"deviceid":"","data":{}}""")
            if (r != null && r.contains("\"error\"")) out += Found(Brand.SONOFF, Kind.RELAY, "Sonoff ${host.substringAfterLast('.')}", host)
        }

        if (80 in open) {
            val shelly = Net.get("http://$host/shelly")?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (shelly != null && (shelly.has("type") || shelly.has("model") || shelly.has("app"))) {
                val gen = shelly.optInt("gen", 1)
                val name = shelly.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: shelly.optString("id").takeIf { it.isNotBlank() } ?: shelly.optString("type", "Shelly")
                out += Found(Brand.SHELLY, Kind.RELAY, name, host, mx + ("gen" to gen.toString()))
            } else {
                val tas = Net.get("http://$host/cm?cmnd=Status%200")?.let { runCatching { JSONObject(it) }.getOrNull() }
                val st = tas?.optJSONObject("Status")
                if (st != null) {
                    val sts = tas.optJSONObject("StatusSTS")
                    val isLight = sts?.has("Dimmer") == true || sts?.has("Color") == true
                    val fn = st.optJSONArray("FriendlyName")?.optString(0)?.takeIf { it.isNotBlank() } ?: "Tasmota ${host.substringAfterLast('.')}"
                    out += Found(Brand.TASMOTA, if (isLight) Kind.LIGHT else Kind.RELAY, fn, host)
                } else {
                    val hue = Net.get("http://$host/api/config")?.let { runCatching { JSONObject(it) }.getOrNull() }
                    if (hue != null && hue.has("bridgeid")) {
                        out += Found(Brand.HUE, Kind.LIGHT, hue.optString("name", "Puente Hue"), host, isHueBridge = true)
                    }
                }
            }
        }
        out
    }
}
