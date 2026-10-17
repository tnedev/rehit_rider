package com.tisho.ride.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.tisho.ride.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

@Serializable
data class PhoneLink(val token: String, val profileId: Long, val device: String, val createdAt: Long)

@Serializable
data class PairRequest(val code: String, val device: String)

@Serializable
data class PairResponse(val token: String, val rider: String)

@Serializable
data class SyncRide(
    val startedAt: Long, val durationSec: Int, val workoutName: String, val notes: String,
    val kcal: Double, val activeKcal: Double, val avgWatts: Double, val avgHr: Int,
    /** Per-second samples as [t, watts, rpm, hr]. */
    val samples: List<List<Int>>,
)

@Serializable
data class SyncResponse(val rider: String, val rides: List<SyncRide>)

/**
 * Local-network sync for the companion phone app. A rider links a phone with a short-lived
 * 6-digit code; the phone then gets a token that can only ever read that rider's rides.
 */
class SyncServer(private val context: Context, private val repo: Repository) {
    private val tag = "RideSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("phone_links", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null

    private data class PendingCode(val profileId: Long, val expires: Long)
    private val pending = HashMap<String, PendingCode>()

    private val _links = MutableStateFlow(loadLinks())
    val links: StateFlow<List<PhoneLink>> = _links.asStateFlow()

    fun start() {
        if (server != null) return
        scope.launch {
            try {
                val s = ServerSocket(PORT)
                server = s
                register(s.localPort)
                while (!s.isClosed) {
                    val c = s.accept()
                    scope.launch { c.use { handle(it) } }
                }
            } catch (e: Exception) {
                Log.w(tag, "sync server stopped", e)
                server = null
            }
        }
    }

    /** New pairing code for [profileId], valid for 10 minutes. */
    fun newCode(profileId: Long): String {
        val code = "%06d".format(random.nextInt(1_000_000))
        synchronized(pending) {
            pending.entries.removeIf { it.value.expires < System.currentTimeMillis() || it.value.profileId == profileId }
            pending[code] = PendingCode(profileId, System.currentTimeMillis() + 10 * 60_000)
        }
        return code
    }

    fun unlink(token: String) = saveLinks(_links.value.filterNot { it.token == token })
    fun unlinkProfile(profileId: Long) = saveLinks(_links.value.filterNot { it.profileId == profileId })

    private fun register(port: Int) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = "REHIT Rider ${Build.MODEL}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(tag, "advertised as ${i.serviceName}") }
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) { Log.w(tag, "nsd failed $e") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        nsd = manager
        registration = listener
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val input = BufferedInputStream(socket.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
        }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length in 1..65_536) ByteArray(length).also { b -> var r = 0; while (r < length) { val n = input.read(b, r, length - r); if (n < 0) break; r += n } }.decodeToString() else ""
        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0) ?: ""
        val path = parts.getOrNull(1) ?: ""
        val out = socket.getOutputStream()
        try {
            when {
                method == "GET" && path == "/hello" -> respond(out, 200, """{"app":"rehit-rider","version":1}""")
                method == "POST" && path == "/pair" -> pair(out, body)
                method == "GET" && path.startsWith("/rides") -> rides(out, headers["authorization"], path)
                else -> respond(out, 404, """{"error":"not found"}""")
            }
        } catch (e: Exception) {
            Log.w(tag, "request failed", e)
            respond(out, 400, """{"error":"bad request"}""")
        }
    }

    private fun pair(out: OutputStream, body: String) {
        val req = repo.json.decodeFromString(PairRequest.serializer(), body)
        val match = synchronized(pending) {
            pending.remove(req.code)?.takeIf { it.expires >= System.currentTimeMillis() }
        } ?: return respond(out, 403, """{"error":"wrong or expired code"}""")
        val profile = runBlocking { repo.db.profiles().get(match.profileId) } ?: return respond(out, 403, """{"error":"rider not found"}""")
        val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        saveLinks(_links.value + PhoneLink(token, profile.id, req.device.take(60), System.currentTimeMillis()))
        respond(out, 200, repo.json.encodeToString(PairResponse.serializer(), PairResponse(token, profile.name)))
    }

    private fun rides(out: OutputStream, auth: String?, path: String) {
        val token = auth?.removePrefix("Bearer ")?.trim()
        val link = _links.value.firstOrNull { it.token == token } ?: return respond(out, 401, """{"error":"not linked"}""")
        val since = Regex("since=(\\d+)").find(path)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val response = runBlocking {
            val profile = repo.db.profiles().get(link.profileId)
            val rides = repo.db.rides().forProfileNow(link.profileId).filter { it.startedAt > since && it.durationSec > 0 }.take(50)
            SyncResponse(
                profile?.name ?: "",
                rides.map { r ->
                    SyncRide(r.startedAt, r.durationSec, r.workoutName, r.notes, r.kcal, r.activeKcal, r.avgWatts, r.avgHr,
                        repo.samples(r.id).map { listOf(it.t, it.watts.toInt(), it.rpm, it.hr) })
                },
            )
        }
        respond(out, 200, repo.json.encodeToString(SyncResponse.serializer(), response))
    }

    private fun respond(out: OutputStream, code: Int, json: String) {
        val body = json.toByteArray()
        val status = when (code) { 200 -> "OK"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Bad Request" }
        out.write("HTTP/1.1 $code $status\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(b.toChar())
        }
    }

    private fun loadLinks(): List<PhoneLink> = runCatching {
        repo.json.decodeFromString(ListSerializer(PhoneLink.serializer()), prefs.getString("links", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun saveLinks(list: List<PhoneLink>) {
        prefs.edit().putString("links", repo.json.encodeToString(ListSerializer(PhoneLink.serializer()), list)).apply()
        _links.value = list
    }

    companion object {
        const val PORT = 47820
        const val SERVICE_TYPE = "_rehitrider._tcp."
    }
}
