package com.vorder.control.net

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Los dispositivos de la casa usan certificados autofirmados, así que este cliente
 * acepta cualquier certificado. SOLO se usa para hablar con IPs de la red local.
 */
object Net {
    val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun trustAllSslContext(): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }

    val client: OkHttpClient by lazy {
        val ctx = trustAllSslContext()
        OkHttpClient.Builder()
            .sslSocketFactory(ctx.socketFactory, trustAll)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        } catch (e: Exception) { null }
    }

    suspend fun send(method: String, url: String, body: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).method(method, body.toRequestBody(json)).build()
            client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        } catch (e: Exception) { null }
    }

    suspend fun portOpen(host: String, port: Int, timeoutMs: Int = 700): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
        } catch (e: Exception) { false }
    }

    /** IP local usando el sistema de conexiones de Android (más confiable), con respaldo. */
    fun localIp(ctx: Context): String? {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val nets = listOfNotNull(cm.activeNetwork) + cm.allNetworks.toList()
            for (n in nets) {
                val lp = cm.getLinkProperties(n) ?: continue
                lp.linkAddresses.firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress }
                    ?.let { return it.address.hostAddress }
            }
        } catch (_: Exception) { }
        return localIp()
    }

    /** IPv4 local del Wi-Fi (ej. 192.168.0.23) o null. */
    fun localIp(): String? {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
        val cands = ifaces.filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") }
        for (i in cands) for (a in i.inetAddresses) {
            if (a.address.size == 4 && a.isSiteLocalAddress) return a.hostAddress
        }
        return null
    }

    /** Intenta leer la MAC desde la tabla ARP (puede fallar según la versión de Android). */
    fun arpMac(host: String): String? = try {
        File("/proc/net/arp").readLines().drop(1).map { it.trim().split(Regex("\\s+")) }
            .firstOrNull { it.size >= 4 && it[0] == host && it[3] != "00:00:00:00:00:00" }?.get(3)
    } catch (e: Exception) { null }

    /** Wake-on-LAN para encender TVs apagadas. */
    suspend fun wakeOnLan(mac: String, host: String?) = withContext(Dispatchers.IO) {
        val bytes = mac.split(":", "-").map { it.toInt(16).toByte() }.toByteArray()
        require(bytes.size == 6) { "MAC inválida" }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(bytes, 0, packet, 6 + i * 6, 6)
        val targets = mutableSetOf("255.255.255.255")
        host?.substringBeforeLast('.', "")?.takeIf { it.isNotEmpty() }?.let { targets += "$it.255" }
        DatagramSocket().use { s ->
            s.broadcast = true
            targets.forEach { t ->
                repeat(3) { s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), 9)) }
            }
        }
    }
}
