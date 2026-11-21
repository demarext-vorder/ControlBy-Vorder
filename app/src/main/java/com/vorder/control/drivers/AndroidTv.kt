package com.vorder.control.drivers

import android.content.Context
import android.util.Base64
import com.vorder.control.data.DeviceState
import com.vorder.control.data.RemoteKey
import com.vorder.control.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/*
 * Protocolo "Android TV Remote v2" (puertos 6467 para vincular y 6466 para controlar).
 * Se usa TLS con un certificado de cliente propio; la TV lo recuerda tras vincular con un PIN.
 */

// ───────── Protobuf mínimo ─────────
object Pb {
    fun varint(v0: Long): ByteArray {
        var v = v0
        val o = ByteArrayOutputStream()
        while (true) {
            if (v and 0x7FL.inv() == 0L) { o.write(v.toInt()); break }
            o.write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7
        }
        return o.toByteArray()
    }
    fun int(field: Int, v: Long) = varint((field shl 3).toLong()) + varint(v)
    fun bytes(field: Int, b: ByteArray) = varint(((field shl 3) or 2).toLong()) + varint(b.size.toLong()) + b
    fun str(field: Int, s: String) = bytes(field, s.toByteArray())

    class Msg(val fields: List<Pair<Int, Any>>) {
        fun bytes(f: Int): ByteArray? = fields.firstOrNull { it.first == f && it.second is ByteArray }?.second as? ByteArray
        fun long(f: Int): Long? = fields.firstOrNull { it.first == f && it.second is Long }?.second as? Long
        fun has(f: Int) = fields.any { it.first == f }
    }

    fun parse(b: ByteArray): Msg {
        val out = mutableListOf<Pair<Int, Any>>()
        var i = 0
        fun readVar(): Long {
            var shift = 0; var r = 0L
            while (i < b.size) {
                val x = b[i++].toInt() and 0xFF
                r = r or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) break
                shift += 7
            }
            return r
        }
        while (i < b.size) {
            val key = readVar()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> out += field to readVar()
                2 -> { val n = readVar().toInt(); out += field to b.copyOfRange(i, i + n); i += n }
                1 -> i += 8
                5 -> i += 4
                else -> return Msg(out)
            }
        }
        return Msg(out)
    }

    fun readFrame(inp: InputStream): ByteArray? {
        var shift = 0; var len = 0L
        while (true) {
            val x = inp.read()
            if (x < 0) return null
            len = len or ((x and 0x7F).toLong() shl shift)
            if (x and 0x80 == 0) break
            shift += 7
        }
        val buf = ByteArray(len.toInt())
        var off = 0
        while (off < buf.size) {
            val n = inp.read(buf, off, buf.size - off)
            if (n < 0) return null
            off += n
        }
        return buf
    }
}

// ───────── Identidad (certificado de cliente) ─────────
class AtvIdentity(ctx: Context) {
    private val sp = ctx.getSharedPreferences("atv_identity", Context.MODE_PRIVATE)
    val key: PrivateKey
    val cert: X509Certificate

    init {
        val k = sp.getString("key", null)
        val c = sp.getString("cert", null)
        if (k != null && c != null) {
            key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.decode(k, Base64.NO_WRAP)))
            cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.decode(c, Base64.NO_WRAP))) as X509Certificate
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val name = X500Name("CN=control-by-vorder")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 3650L * 86_400_000L), name, kp.public
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
            key = kp.private
            cert = JcaX509CertificateConverter().getCertificate(holder)
            sp.edit().putString("key", Base64.encodeToString(key.encoded, Base64.NO_WRAP))
                .putString("cert", Base64.encodeToString(cert.encoded, Base64.NO_WRAP)).apply()
        }
    }

    fun sslContext(): SSLContext {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        ks.setKeyEntry("client", key, CharArray(0), arrayOf(cert))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, arrayOf<TrustManager>(Net.trustAll), SecureRandom())
        }
    }

    fun openSocket(host: String, port: Int): SSLSocket {
        val s = sslContext().socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, port), 4000)
        s.soTimeout = 15_000
        s.startHandshake()
        return s
    }
}

// ───────── Vinculación con PIN (puerto 6467) ─────────
class AtvPairingSession internal constructor(
    private val socket: SSLSocket,
    private val identity: AtvIdentity,
) {
    private fun frame(payload: ByteArray) = Pb.varint(payload.size.toLong()) + payload
    private fun outer(vararg parts: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(Pb.int(1, 2)); o.write(Pb.int(2, 200))
        parts.forEach { o.write(it) }
        return frame(o.toByteArray())
    }
    private fun send(b: ByteArray) { socket.getOutputStream().write(b); socket.getOutputStream().flush() }
    private fun read(expectField: Int): Pb.Msg {
        val raw = Pb.readFrame(socket.getInputStream()) ?: throw IOException("La TV cerró la conexión")
        val m = Pb.parse(raw)
        if ((m.long(2) ?: 200L) != 200L) throw IOException("La TV rechazó la vinculación")
        if (!m.has(expectField)) throw IOException("Respuesta inesperada de la TV")
        return m
    }

    internal fun begin() {
        send(outer(Pb.bytes(10, Pb.str(1, "com.vorder.control") + Pb.str(2, "Control by VORDER"))))
        read(11)
        val enc = Pb.int(1, 3) + Pb.int(2, 6) // HEXADECIMAL, 6 símbolos
        send(outer(Pb.bytes(20, Pb.bytes(1, enc) + Pb.int(3, 1))))
        read(20)
        send(outer(Pb.bytes(30, Pb.bytes(1, enc) + Pb.int(2, 1))))
        read(31) // ahora la TV muestra el código
    }

    private fun bigBytes(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
    }

    /** code = 6 caracteres hexadecimales que muestra la TV. */
    suspend fun finish(code: String): Unit = withContext(Dispatchers.IO) {
        try {
            val c = code.trim().uppercase()
            require(c.length == 6 && c.all { it in "0123456789ABCDEF" }) { "El código tiene 6 caracteres (0-9, A-F)" }
            val codeBytes = ByteArray(3) { c.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val clientKey = identity.cert.publicKey as RSAPublicKey
            val serverKey = socket.session.peerCertificates[0].publicKey as RSAPublicKey
            val md = MessageDigest.getInstance("SHA-256")
            md.update(bigBytes(clientKey.modulus)); md.update(bigBytes(clientKey.publicExponent))
            md.update(bigBytes(serverKey.modulus)); md.update(bigBytes(serverKey.publicExponent))
            md.update(codeBytes.copyOfRange(1, 3))
            val digest = md.digest()
            if (digest[0] != codeBytes[0]) throw IOException("Código incorrecto")
            send(outer(Pb.bytes(40, Pb.bytes(1, digest))))
            read(41)
        } finally {
            runCatching { socket.close() }
        }
    }

    fun cancel() { runCatching { socket.close() } }

    companion object {
        suspend fun start(host: String, identity: AtvIdentity): AtvPairingSession = withContext(Dispatchers.IO) {
            val s = try { identity.openSocket(host, 6467) } catch (e: Exception) { throw IOException("No se pudo conectar con la TV (puerto 6467)") }
            val session = AtvPairingSession(s, identity)
            try { session.begin() } catch (e: Exception) { session.cancel(); throw e }
            session
        }
    }
}

// ───────── Control remoto (puerto 6466) ─────────
class AndroidTvDriver(private val host: String, private val identity: AtvIdentity) : Driver {
    private var socket: SSLSocket? = null
    private val mutex = Mutex()
    private val writeLock = Any()
    @Volatile private var ready = false

    private fun frame(payload: ByteArray) = Pb.varint(payload.size.toLong()) + payload

    private fun write(payload: ByteArray) {
        val s = socket ?: throw IOException("Sin conexión")
        synchronized(writeLock) { s.getOutputStream().write(frame(payload)); s.getOutputStream().flush() }
    }

    private fun readerLoop(s: SSLSocket) {
        try {
            s.soTimeout = 0
            val inp = s.getInputStream()
            while (true) {
                val raw = Pb.readFrame(inp) ?: break
                val m = Pb.parse(raw)
                when {
                    m.has(1) -> { // remote_configure del servidor → respondemos el nuestro
                        val dev = Pb.str(1, "Control") + Pb.str(2, "VORDER") + Pb.int(3, 1) + Pb.str(4, "1") +
                            Pb.str(5, "com.vorder.control") + Pb.str(6, "1.0")
                        write(Pb.bytes(1, Pb.int(1, 622) + Pb.bytes(2, dev)))
                    }
                    m.has(2) -> { write(Pb.bytes(2, Pb.int(1, 622))); ready = true }
                    m.has(8) -> { // ping
                        val v = m.bytes(8)?.let { Pb.parse(it).long(1) } ?: 0L
                        write(Pb.bytes(9, Pb.int(1, v)))
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            ready = false
            if (socket === s) socket = null
            runCatching { s.close() }
        }
    }

    private suspend fun ensure() = mutex.withLock {
        if (socket != null && ready) return@withLock
        withContext(Dispatchers.IO) {
            runCatching { socket?.close() }
            ready = false
            val s = try { identity.openSocket(host, 6466) } catch (e: Exception) {
                throw IOException("No se pudo conectar. ¿La TV está encendida y vinculada?")
            }
            socket = s
            Thread { readerLoop(s) }.apply { isDaemon = true; start() }
            val t0 = System.currentTimeMillis()
            while (!ready && System.currentTimeMillis() - t0 < 6000) Thread.sleep(50)
            if (!ready) throw IOException("La TV no completó el saludo. Probá vincularla de nuevo")
        }
    }

    private fun code(k: RemoteKey) = when (k) {
        RemoteKey.POWER -> 26
        RemoteKey.UP -> 19
        RemoteKey.DOWN -> 20
        RemoteKey.LEFT -> 21
        RemoteKey.RIGHT -> 22
        RemoteKey.OK -> 23
        RemoteKey.BACK -> 4
        RemoteKey.HOME -> 3
        RemoteKey.MENU -> 82
        RemoteKey.VOL_UP -> 24
        RemoteKey.VOL_DOWN -> 25
        RemoteKey.MUTE -> 164
        RemoteKey.CH_UP -> 166
        RemoteKey.CH_DOWN -> 167
        RemoteKey.PLAY_PAUSE -> 85
    }

    override suspend fun key(k: RemoteKey) {
        ensure()
        withContext(Dispatchers.IO) { write(Pb.bytes(10, Pb.int(1, code(k).toLong()) + Pb.int(2, 3))) } // 3 = pulsación corta
    }

    override suspend fun getState() = DeviceState(online = Net.portOpen(host, 6466))

    /** En Google TV el botón de encendido es un "toggle": prende o apaga según el estado actual. */
    override suspend fun setPower(on: Boolean) = key(RemoteKey.POWER)

    override fun close() { runCatching { socket?.close() }; socket = null; ready = false }
}
