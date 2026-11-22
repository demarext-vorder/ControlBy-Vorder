package com.vorder.control.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.vorder.control.MainActivity
import com.vorder.control.R
import com.vorder.control.data.Device
import com.vorder.control.data.DeviceStore
import com.vorder.control.data.Kind
import com.vorder.control.data.RemoteKey
import com.vorder.control.drivers.AtvIdentity
import com.vorder.control.drivers.Driver
import com.vorder.control.drivers.Drivers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Botón del widget: comando + ícono (+ si usa el fondo con degradado). */
private data class Slot(val cmd: String, val icon: Int, val accent: Boolean = false)

object WidgetPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun get(c: Context, widgetId: Int): String? = sp(c).getString("w_$widgetId", null)
    fun set(c: Context, widgetId: Int, deviceId: String) { sp(c).edit().putString("w_$widgetId", deviceId).apply() }
    fun remove(c: Context, widgetId: Int) { sp(c).edit().remove("w_$widgetId").apply() }
}

class ControlWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CMD) { super.onReceive(context, intent); return }
        val deviceId = intent.getStringExtra("device") ?: return
        val cmd = intent.getStringExtra("cmd") ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(9000) { run(app, deviceId, cmd) }
            } catch (e: Exception) {
                val msg = e.message?.takeIf { it.isNotBlank() && e !is kotlinx.coroutines.TimeoutCancellationException } ?: "El dispositivo no respondió"
                Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CMD = "com.vorder.control.WIDGET_CMD"
        private val drivers = ConcurrentHashMap<String, Driver>()

        private fun slotsFor(d: Device): List<Slot> = when (d.kind) {
            Kind.TV -> listOf(
                Slot("POWER", R.drawable.ic_w_power, true), Slot("VOL_DOWN", R.drawable.ic_w_vol_down), Slot("VOL_UP", R.drawable.ic_w_vol_up),
                Slot("MUTE", R.drawable.ic_w_mute), Slot("CH_DOWN", R.drawable.ic_w_ch_down), Slot("CH_UP", R.drawable.ic_w_ch_up),
            )
            Kind.LIGHT -> listOf(
                Slot("ON", R.drawable.ic_w_bulb, true), Slot("OFF", R.drawable.ic_w_power), Slot("BRI_DOWN", R.drawable.ic_w_minus),
                Slot("BRI_UP", R.drawable.ic_w_plus),
            )
            Kind.RELAY -> listOf(Slot("ON", R.drawable.ic_w_bulb, true), Slot("OFF", R.drawable.ic_w_power))
        }

        private val BTN_IDS = intArrayOf(R.id.b1, R.id.b2, R.id.b3, R.id.b4, R.id.b5, R.id.b6)

        fun update(ctx: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_remote)
            val device = WidgetPrefs.get(ctx, widgetId)?.let { id -> DeviceStore(ctx).load().firstOrNull { it.id == id } }
            val openApp = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

            if (device == null) {
                views.setTextViewText(R.id.title, "Tocá para abrir Control by VORDER")
                BTN_IDS.forEach { views.setViewVisibility(it, View.GONE) }
                views.setOnClickPendingIntent(R.id.root, openApp)
            } else {
                views.setTextViewText(R.id.title, device.name)
                views.setOnClickPendingIntent(R.id.title, openApp)
                val slots = slotsFor(device)
                BTN_IDS.forEachIndexed { i, viewId ->
                    val slot = slots.getOrNull(i)
                    if (slot == null) {
                        views.setViewVisibility(viewId, View.GONE)
                    } else {
                        views.setViewVisibility(viewId, View.VISIBLE)
                        views.setImageViewResource(viewId, slot.icon)
                        views.setInt(viewId, "setBackgroundResource", if (slot.accent) R.drawable.widget_btn_accent else R.drawable.widget_btn)
                        val intent = Intent(ctx, ControlWidget::class.java).apply {
                            action = ACTION_CMD
                            data = Uri.parse("vorder://widget/$widgetId/$i")
                            putExtra("device", device.id)
                            putExtra("cmd", slot.cmd)
                        }
                        views.setOnClickPendingIntent(
                            viewId,
                            PendingIntent.getBroadcast(ctx, widgetId * 10 + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        )
                    }
                }
                views.setViewVisibility(R.id.row2, if (slots.size > 3) View.VISIBLE else View.GONE)
            }
            manager.updateAppWidget(widgetId, views)
        }

        private suspend fun run(ctx: Context, deviceId: String, cmd: String) {
            val store = DeviceStore(ctx)
            val d = store.load().firstOrNull { it.id == deviceId } ?: throw IOException("Ese dispositivo ya no existe en la app")
            val drv = drivers.getOrPut(d.id) {
                Drivers.create(d, lazy { AtvIdentity(ctx) }) { k, v -> store.updateExtra(d.id, k, v) }
            }
            when (cmd) {
                "POWER" -> if (drv.getState().online) drv.key(RemoteKey.POWER) else drv.setPower(true)
                "VOL_UP" -> drv.key(RemoteKey.VOL_UP)
                "VOL_DOWN" -> drv.key(RemoteKey.VOL_DOWN)
                "MUTE" -> drv.key(RemoteKey.MUTE)
                "CH_UP" -> drv.key(RemoteKey.CH_UP)
                "CH_DOWN" -> drv.key(RemoteKey.CH_DOWN)
                "ON" -> drv.setPower(true)
                "OFF" -> drv.setPower(false)
                "BRI_UP", "BRI_DOWN" -> {
                    val cur = drv.getState().brightness ?: 50
                    drv.setBrightness((cur + if (cmd == "BRI_UP") 20 else -20).coerceIn(5, 100))
                }
            }
        }
    }
}
