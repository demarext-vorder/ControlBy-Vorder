package com.vorder.control.drivers

import com.vorder.control.data.DeviceState
import com.vorder.control.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

// ─────────────────────────── Tasmota ───────────────────────────
class TasmotaDriver(private val host: String) : Driver {
    private suspend fun cmd(c: String): JSONObject? =
        Net.get("http://$host/cm?cmnd=${enc(c)}")?.let { runCatching { JSONObject(it) }.getOrNull() }

    override suspend fun getState(): DeviceState {
        val sts = cmd("Status 11")?.optJSONObject("StatusSTS") ?: return DeviceState(online = false)
        val power = sts.optString("POWER", sts.optString("POWER1", ""))
        return DeviceState(
            online = true,
            on = power.equals("ON", true),
            brightness = if (sts.has("Dimmer")) sts.optInt("Dimmer") else null
        )
    }

    override suspend fun setPower(on: Boolean) { cmd(if (on) "Power On" else "Power Off") ?: throw IOException("Sin respuesta") }
    override suspend fun setBrightness(pct: Int) { cmd("Dimmer $pct") ?: throw IOException("Sin respuesta") }
    override suspend fun setColor(rgb: Int) {
        val hex = String.format("%06X", rgb and 0xFFFFFF)
        cmd("Color #$hex") ?: throw IOException("Sin respuesta")
    }
}

// ─────────────────────────── Shelly ───────────────────────────
class ShellyDriver(private val host: String, private val gen: Int) : Driver {
    override suspend fun getState(): DeviceState {
        val body = if (gen >= 2) Net.get("http://$host/rpc/Switch.GetStatus?id=0") else Net.get("http://$host/relay/0")
        val o = body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return DeviceState(online = false)
        val on = if (gen >= 2) o.optBoolean("output") else o.optBoolean("ison")
        return DeviceState(online = true, on = on)
    }

    override suspend fun setPower(on: Boolean) {
        val v = if (on) "true" else "false"
        val r = if (gen >= 2) Net.get("http://$host/rpc/Switch.Set?id=0&on=$v")
        else Net.get("http://$host/relay/0?turn=${if (on) "on" else "off"}")
        r ?: throw IOException("Sin respuesta")
    }
}

// ─────────────────────────── Sonoff DIY (modo LAN) ───────────────────────────
class SonoffDriver(private val host: String) : Driver {
    private fun url(path: String) = "http://$host:8081/zeroconf/$path"
    private val body = """{"deviceid":"","data":{}}"""

    override suspend fun getState(): DeviceState {
        val r = Net.send("POST", url("info"), body) ?: return DeviceState(online = false)
        val o = runCatching { JSONObject(r) }.getOrNull() ?: return DeviceState(online = false)
        val data: JSONObject? = o.optJSONObject("data") ?: o.optString("data").takeIf { it.isNotEmpty() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        return DeviceState(online = true, on = data?.optString("switch") == "on")
    }

    override suspend fun setPower(on: Boolean) {
        val b = """{"deviceid":"","data":{"switch":"${if (on) "on" else "off"}"}}"""
        Net.send("POST", url("switch"), b) ?: throw IOException("Sin respuesta")
    }
}

// ─────────────────────────── WiZ (UDP 38899) ───────────────────────────
class WizDriver(private val host: String) : Driver {
    private suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 1500
                val msg = JSONObject().put("method", method).put("params", params).toString().toByteArray()
                s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(host), 38899))
                val buf = ByteArray(2048)
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                JSONObject(String(p.data, 0, p.length))
            }
        } catch (e: Exception) { null }
    }

    override suspend fun getState(): DeviceState {
        val res = call("getPilot")?.optJSONObject("result") ?: return DeviceState(online = false)
        return DeviceState(online = true, on = res.optBoolean("state"), brightness = if (res.has("dimming")) res.optInt("dimming") else null)
    }

    override suspend fun setPower(on: Boolean) {
        call("setState", JSONObject().put("state", on)) ?: throw IOException("Sin respuesta")
    }

    override suspend fun setBrightness(pct: Int) {
        call("setPilot", JSONObject().put("state", true).put("dimming", pct.coerceIn(10, 100))) ?: throw IOException("Sin respuesta")
    }

    override suspend fun setColor(rgb: Int) {
        val p = JSONObject().put("state", true)
            .put("r", (rgb shr 16) and 0xFF).put("g", (rgb shr 8) and 0xFF).put("b", rgb and 0xFF)
        call("setPilot", p) ?: throw IOException("Sin respuesta")
    }
}

// ─────────────────────────── Yeelight (TCP 55443, "Control LAN" activado) ───────────────────────────
class YeelightDriver(private val host: String, private val port: Int = 55443) : Driver {
    private suspend fun call(method: String, vararg params: Any): JSONArray? = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 1500)
                s.soTimeout = 2000
                val msg = JSONObject().put("id", 1).put("method", method).put("params", JSONArray(params.toList())).toString() + "\r\n"
                s.getOutputStream().write(msg.toByteArray())
                s.getOutputStream().flush()
                val reader = s.getInputStream().bufferedReader()
                var result: JSONArray? = null
                while (result == null) {
                    val line = reader.readLine() ?: break
                    val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (o.optInt("id") == 1) result = o.optJSONArray("result") ?: JSONArray()
                }
                result
            }
        } catch (e: Exception) { null }
    }

    override suspend fun getState(): DeviceState {
        val r = call("get_prop", "power", "bright") ?: return DeviceState(online = false)
        return DeviceState(online = true, on = r.optString(0) == "on", brightness = r.optString(1).toIntOrNull())
    }

    override suspend fun setPower(on: Boolean) {
        call("set_power", if (on) "on" else "off", "smooth", 300) ?: throw IOException("Activá 'Control LAN' en la app Yeelight")
    }

    override suspend fun setBrightness(pct: Int) {
        call("set_bright", pct.coerceIn(1, 100), "smooth", 300) ?: throw IOException("Sin respuesta")
    }

    override suspend fun setColor(rgb: Int) {
        call("set_rgb", rgb and 0xFFFFFF, "smooth", 300) ?: throw IOException("Sin respuesta")
    }
}

// ─────────────────────────── Philips Hue (puente, API v1 local) ───────────────────────────
class HueDriver(private val host: String, private val user: String, private val lightId: String) : Driver {
    private val base get() = "http://$host/api/$user/lights/$lightId"

    override suspend fun getState(): DeviceState {
        val st = Net.get(base)?.let { runCatching { JSONObject(it) }.getOrNull() }?.optJSONObject("state")
            ?: return DeviceState(online = false)
        val reachable = st.optBoolean("reachable", true)
        val bri = if (st.has("bri")) (st.optInt("bri") * 100 / 254) else null
        return DeviceState(online = reachable, on = st.optBoolean("on"), brightness = bri)
    }

    private suspend fun put(body: JSONObject) {
        Net.send("PUT", "$base/state", body.toString()) ?: throw IOException("Sin respuesta del puente")
    }

    override suspend fun setPower(on: Boolean) = put(JSONObject().put("on", on))
    override suspend fun setBrightness(pct: Int) =
        put(JSONObject().put("on", true).put("bri", (pct.coerceIn(1, 100) * 254 / 100).coerceAtLeast(1)))

    override suspend fun setColor(rgb: Int) {
        val hsv = rgbToHsv(rgb)
        put(JSONObject().put("on", true).put("hue", (hsv[0] / 360f * 65535).toInt()).put("sat", (hsv[1] * 254).toInt()))
    }

    companion object {
        /** null = hay que presionar el botón del puente; String = usuario creado. */
        suspend fun pair(host: String): Result<String?> {
            val r = Net.send("POST", "http://$host/api", """{"devicetype":"control_by_vorder#android"}""")
                ?: return Result.failure(IOException("No se pudo contactar al puente"))
            val first = runCatching { JSONArray(r).getJSONObject(0) }.getOrNull()
                ?: return Result.failure(IOException("Respuesta inesperada"))
            first.optJSONObject("success")?.let { return Result.success(it.getString("username")) }
            return Result.success(null)
        }

        suspend fun listLights(host: String, user: String): List<Pair<String, String>> {
            val o = Net.get("http://$host/api/$user/lights")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyList()
            return o.keys().asSequence().map { it to o.getJSONObject(it).optString("name", "Luz $it") }.toList()
        }
    }
}
