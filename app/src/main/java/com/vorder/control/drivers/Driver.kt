package com.vorder.control.drivers

import android.graphics.Color
import com.vorder.control.data.Brand
import com.vorder.control.data.Device
import com.vorder.control.data.DeviceState
import com.vorder.control.data.RemoteKey

interface Driver {
    suspend fun getState(): DeviceState
    suspend fun setPower(on: Boolean)
    suspend fun setBrightness(pct: Int): Unit = throw UnsupportedOperationException("Este dispositivo no regula brillo")
    suspend fun setColor(rgb: Int): Unit = throw UnsupportedOperationException("Este dispositivo no cambia de color")
    suspend fun key(k: RemoteKey): Unit = throw UnsupportedOperationException("Este dispositivo no es un control remoto")
    fun close() {}
}

object Drivers {
    /** saveExtra guarda datos de vinculación (token, client-key, etc.) en el dispositivo. */
    fun create(d: Device, identity: Lazy<AtvIdentity>, saveExtra: (String, String) -> Unit): Driver = when (d.brand) {
        Brand.LG -> LgDriver(d.host, d.extra["clientKey"], d.extra["mac"], saveExtra)
        Brand.SAMSUNG -> SamsungDriver(d.host, d.extra["token"], d.extra["mac"], saveExtra)
        Brand.GOOGLE_TV -> AndroidTvDriver(d.host, identity.value)
        Brand.WIZ -> WizDriver(d.host)
        Brand.YEELIGHT -> YeelightDriver(d.host)
        Brand.HUE -> HueDriver(d.host, d.extra["user"] ?: "", d.extra["lightId"] ?: "1")
        Brand.TASMOTA -> TasmotaDriver(d.host)
        Brand.SHELLY -> ShellyDriver(d.host, d.extra["gen"]?.toIntOrNull() ?: 1)
        Brand.SONOFF -> SonoffDriver(d.host)
    }
}

/** Convierte un color ARGB a (h 0..360, s 0..1, v 0..1). */
fun rgbToHsv(rgb: Int): FloatArray {
    val hsv = FloatArray(3)
    Color.colorToHSV(rgb, hsv)
    return hsv
}
