package com.vorder.control.drivers

import android.util.Base64
import com.vorder.control.data.DeviceState
import com.vorder.control.data.RemoteKey
import com.vorder.control.net.Net
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private const val PAIR_WAIT_MS = 45_000L

// ─────────────────────────── LG webOS ───────────────────────────
class LgDriver(
    private val host: String,
    private var clientKey: String?,
    private val mac: String?,
    private val save: (String, String) -> Unit,
) : Driver {
    private var ws: WebSocket? = null
    @Volatile private var ready = false
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var counter = 0
    private val mutex = Mutex()
    private var pointer: WebSocket? = null
    private var muted = false
    private var playing = true

    private fun registerMessage(): String {
        val perms = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "READ_APP_STATUS",
            "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "READ_RUNNING_APPS",
            "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "CONTROL_MOUSE_AND_KEYBOARD"
        )
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", JSONObject().put("manifestVersion", 1).put("permissions", org.json.JSONArray(perms)))
        clientKey?.let { payload.put("client-key", it) }
        return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload).toString()
    }

    private fun listener(done: CompletableDeferred<Boolean>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(registerMessage()) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return
            val id = o.optString("id")
            if (o.optString("type") == "registered") {
                o.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotEmpty() }?.let {
                    clientKey = it; save("clientKey", it)
                }
                ready = true
                done.complete(true)
            } else if (o.optString("type") == "error" && id == "register_0") {
                done.complete(false)
            }
            pending.remove(id)?.complete(o)
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { drop(webSocket); done.complete(false) }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { drop(webSocket); done.complete(false) }
    }

    private fun drop(w: WebSocket) {
        if (ws === w) { ws = null; ready = false }
        pending.values.forEach { it.cancel() }; pending.clear()
    }

    private suspend fun ensure() = mutex.withLock {
        if (ws != null && ready) return@withLock
        ws?.cancel(); ws = null; ready = false
        for (url in listOf("ws://$host:3000", "wss://$host:3001")) {
            val done = CompletableDeferred<Boolean>()
            ws = Net.client.newWebSocket(Request.Builder().url(url).build(), listener(done))
            val ok = try { withTimeout(PAIR_WAIT_MS) { done.await() } } catch (e: TimeoutCancellationException) {
                ws?.cancel(); ws = null
                throw IOException("Aceptá la conexión en la pantalla de la TV y probá de nuevo")
            }
            if (ok) return@withLock
        }
        throw IOException("No se pudo conectar con la TV LG (¿está encendida?)")
    }

    private suspend fun request(uri: String, payload: JSONObject? = null): JSONObject {
        ensure()
        val id = "req_${++counter}"
        val d = CompletableDeferred<JSONObject>()
        pending[id] = d
        val msg = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        if (payload != null) msg.put("payload", payload)
        ws?.send(msg.toString()) ?: throw IOException("Sin conexión con la TV")
        return try { withTimeout(5000) { d.await() } } catch (e: Exception) { pending.remove(id); throw IOException("La TV no respondió") }
    }

    private suspend fun button(name: String) {
        if (pointer == null) {
            val path = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
                .optJSONObject("payload")?.optString("socketPath").orEmpty()
            if (path.isEmpty()) throw IOException("La TV no abrió el control remoto")
            val opened = CompletableDeferred<Boolean>()
            pointer = Net.client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(true) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { pointer = null; opened.complete(false) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { pointer = null }
            })
            if (!withTimeout(4000) { opened.await() }) throw IOException("No se pudo abrir el control remoto")
        }
        pointer?.send("type:button\nname:$name\n\n")
    }

    override suspend fun getState() = DeviceState(online = Net.portOpen(host, 3000) || Net.portOpen(host, 3001))

    override suspend fun setPower(on: Boolean) {
        if (on) {
            val m = mac ?: throw IOException("Para encender la TV hace falta su MAC (editá el dispositivo)")
            Net.wakeOnLan(m, host)
        } else request("ssap://system/turnOff")
    }

    override suspend fun key(k: RemoteKey) {
        when (k) {
            RemoteKey.POWER -> setPower(false)
            RemoteKey.UP -> button("UP")
            RemoteKey.DOWN -> button("DOWN")
            RemoteKey.LEFT -> button("LEFT")
            RemoteKey.RIGHT -> button("RIGHT")
            RemoteKey.OK -> button("ENTER")
            RemoteKey.BACK -> button("BACK")
            RemoteKey.HOME -> button("HOME")
            RemoteKey.MENU -> button("MENU")
            RemoteKey.VOL_UP -> { request("ssap://audio/volumeUp") }
            RemoteKey.VOL_DOWN -> { request("ssap://audio/volumeDown") }
            RemoteKey.MUTE -> { muted = !muted; request("ssap://audio/setMute", JSONObject().put("mute", muted)) }
            RemoteKey.CH_UP -> { request("ssap://tv/channelUp") }
            RemoteKey.CH_DOWN -> { request("ssap://tv/channelDown") }
            RemoteKey.PLAY_PAUSE -> { playing = !playing; request(if (playing) "ssap://media.controls/play" else "ssap://media.controls/pause") }
        }
    }

    override fun close() { ws?.cancel(); pointer?.cancel(); ws = null; pointer = null; ready = false }
}

// ─────────────────────────── Samsung Tizen ───────────────────────────
class SamsungDriver(
    private val host: String,
    private var token: String?,
    private val mac: String?,
    private val save: (String, String) -> Unit,
) : Driver {
    private var ws: WebSocket? = null
    @Volatile private var ready = false
    private val mutex = Mutex()
    private val appName = Base64.encodeToString("Control by VORDER".toByteArray(), Base64.NO_WRAP)

    private fun listener(done: CompletableDeferred<Boolean>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (o.optString("event")) {
                "ms.channel.connect" -> {
                    o.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { token = it; save("token", it) }
                    ready = true; done.complete(true)
                }
                "ms.channel.timeout", "ms.error" -> done.complete(false)
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { drop(webSocket); done.complete(false) }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { drop(webSocket); done.complete(false) }
    }

    private fun drop(w: WebSocket) { if (ws === w) { ws = null; ready = false } }

    private suspend fun ensure() = mutex.withLock {
        if (ws != null && ready) return@withLock
        ws?.cancel(); ws = null; ready = false
        val tk = token?.let { "&token=$it" }.orEmpty()
        val urls = listOf(
            "wss://$host:8002/api/v2/channels/samsung.remote.control?name=$appName$tk",
            "ws://$host:8001/api/v2/channels/samsung.remote.control?name=$appName$tk",
        )
        for (url in urls) {
            val done = CompletableDeferred<Boolean>()
            ws = Net.client.newWebSocket(Request.Builder().url(url).build(), listener(done))
            val ok = try { withTimeout(PAIR_WAIT_MS) { done.await() } } catch (e: TimeoutCancellationException) {
                ws?.cancel(); ws = null
                throw IOException("Aceptá la conexión en la pantalla de la TV y probá de nuevo")
            }
            if (ok) return@withLock
        }
        throw IOException("No se pudo conectar con la TV Samsung (¿está encendida?)")
    }

    private suspend fun sendKey(code: String) {
        ensure()
        val msg = JSONObject().put("method", "ms.remote.control").put(
            "params", JSONObject().put("Cmd", "Click").put("DataOfCmd", code).put("Option", "false").put("TypeOfRemote", "SendRemoteKey")
        )
        ws?.send(msg.toString()) ?: throw IOException("Sin conexión con la TV")
    }

    override suspend fun getState() = DeviceState(online = Net.portOpen(host, 8002) || Net.portOpen(host, 8001))

    override suspend fun setPower(on: Boolean) {
        if (on) {
            val m = mac ?: throw IOException("Para encender la TV hace falta su MAC (editá el dispositivo)")
            Net.wakeOnLan(m, host)
        } else sendKey("KEY_POWER")
    }

    override suspend fun key(k: RemoteKey) = sendKey(
        when (k) {
            RemoteKey.POWER -> "KEY_POWER"
            RemoteKey.UP -> "KEY_UP"
            RemoteKey.DOWN -> "KEY_DOWN"
            RemoteKey.LEFT -> "KEY_LEFT"
            RemoteKey.RIGHT -> "KEY_RIGHT"
            RemoteKey.OK -> "KEY_ENTER"
            RemoteKey.BACK -> "KEY_RETURN"
            RemoteKey.HOME -> "KEY_HOME"
            RemoteKey.MENU -> "KEY_MENU"
            RemoteKey.VOL_UP -> "KEY_VOLUP"
            RemoteKey.VOL_DOWN -> "KEY_VOLDOWN"
            RemoteKey.MUTE -> "KEY_MUTE"
            RemoteKey.CH_UP -> "KEY_CHUP"
            RemoteKey.CH_DOWN -> "KEY_CHDOWN"
            RemoteKey.PLAY_PAUSE -> "KEY_PLAY"
        }
    )

    override fun close() { ws?.cancel(); ws = null; ready = false }
}
