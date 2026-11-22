package com.vorder.control.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Kind(val label: String) { TV("TV"), LIGHT("Luces"), RELAY("Enchufes") }

enum class Brand(val label: String, val defaultKind: Kind) {
    LG("LG webOS", Kind.TV),
    SAMSUNG("Samsung", Kind.TV),
    GOOGLE_TV("Google TV / Android TV", Kind.TV),
    WIZ("WiZ", Kind.LIGHT),
    YEELIGHT("Yeelight", Kind.LIGHT),
    HUE("Philips Hue", Kind.LIGHT),
    TASMOTA("Tasmota (luz o relé)", Kind.RELAY),
    SHELLY("Shelly (relé)", Kind.RELAY),
    SONOFF("Sonoff DIY (relé)", Kind.RELAY),
}

data class Device(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: Brand,
    val kind: Kind,
    val host: String,
    val extra: Map<String, String> = emptyMap(),
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("brand", brand.name); put("kind", kind.name); put("host", host)
        put("extra", JSONObject(extra))
    }

    companion object {
        fun fromJson(o: JSONObject): Device? = try {
            val ex = o.optJSONObject("extra")
            val map = mutableMapOf<String, String>()
            ex?.keys()?.forEach { map[it] = ex.getString(it) }
            Device(
                o.getString("id"), o.getString("name"),
                Brand.valueOf(o.getString("brand")), Kind.valueOf(o.getString("kind")),
                o.getString("host"), map
            )
        } catch (e: Exception) { null }
    }
}

/** Estado en vivo (no se guarda). */
data class DeviceState(
    val online: Boolean = false,
    val on: Boolean? = null,
    val brightness: Int? = null, // 0..100
)

enum class RemoteKey {
    POWER, UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU,
    VOL_UP, VOL_DOWN, MUTE, CH_UP, CH_DOWN, PLAY_PAUSE
}

class DeviceStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("control_by_vorder", Context.MODE_PRIVATE)

    fun load(): List<Device> {
        val raw = sp.getString("devices", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { Device.fromJson(arr.getJSONObject(it)) }
    }

    fun save(list: List<Device>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("devices", arr.toString()).apply()
    }

    fun updateExtra(id: String, k: String, v: String) {
        save(load().map { if (it.id == id) it.copy(extra = it.extra + (k to v)) else it })
    }

    var dark: Boolean
        get() = sp.getBoolean("dark", true)
        set(v) { sp.edit().putBoolean("dark", v).apply() }

    fun getString(k: String): String? = sp.getString(k, null)
    fun putString(k: String, v: String) { sp.edit().putString(k, v).apply() }
}
