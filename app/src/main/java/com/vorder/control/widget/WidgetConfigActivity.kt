package com.vorder.control.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vorder.control.data.DeviceStore
import com.vorder.control.ui.AuroraBackground
import com.vorder.control.ui.ControlTheme
import com.vorder.control.ui.LocalPalette
import com.vorder.control.ui.glass
import com.vorder.control.ui.kindColor
import com.vorder.control.ui.kindIcon

/** Se abre al colocar el widget: el usuario elige qué dispositivo controla. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val store = DeviceStore(this)
        val devices = store.load()

        setContent {
            ControlTheme(store.dark) {
                AuroraBackground {
                    val p = LocalPalette.current
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
                        Text("¿Qué querés controlar?", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Elegí el dispositivo para este widget", color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                        if (devices.isEmpty()) {
                            Text("Todavía no agregaste dispositivos. Abrí Control by VORDER, agregalos y volvé a colocar el widget.", color = p.sub)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(devices, key = { it.id }) { d ->
                                Row(
                                    Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                                        .clickable {
                                            WidgetPrefs.set(this@WidgetConfigActivity, widgetId, d.id)
                                            ControlWidget.update(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), widgetId)
                                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                            finish()
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.size(42.dp).clip(CircleShape).background(kindColor(d.kind).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                        Icon(kindIcon(d.kind), null, tint = kindColor(d.kind), modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.padding(start = 14.dp)) {
                                        Text(d.name, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                        Text("${d.brand.label} · ${d.host}", color = p.sub, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
