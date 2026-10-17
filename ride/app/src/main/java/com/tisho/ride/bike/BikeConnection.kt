package com.tisho.ride.bike

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class BikeStatus { DISCONNECTED, NEEDS_PERMISSION, CONNECTING, READY, SIMULATED, ERROR }

data class BikeTelemetry(
    val rpm: Int = 0,
    val innate: Int = 0,
    val watts: Double = 0.0,
    val gripHr: Int = 0,
    val firmware: Int = 0,
    val at: Long = 0,
)

/**
 * Owns the bike: USB discovery and permission, wake/start handshake, the read loop and paced
 * resistance writes. Falls back to a simulator when demo mode is on.
 */
class BikeConnection(private val context: Context, private val power: PowerModel) {
    private val tag = "RideBike"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionAction = "com.tisho.ride.USB_PERMISSION"

    private val _status = MutableStateFlow(BikeStatus.DISCONNECTED)
    val status: StateFlow<BikeStatus> = _status.asStateFlow()
    private val _telemetry = MutableStateFlow(BikeTelemetry())
    val telemetry: StateFlow<BikeTelemetry> = _telemetry.asStateFlow()

    private var port: UsbSerialPort? = null
    private var session: Job? = null
    private var simulator: Job? = null
    @Volatile private var lastDataAt = 0L

    // Resistance pacing: the brake motor needs 2 s between moves (4 s after returning to 0).
    @Volatile private var wantedInnate = 0
    @Volatile private var sentInnate = -1
    @Volatile private var nextMoveAt = 0L

    var simulate = false
        set(value) {
            field = value
            if (value) startSimulator() else { simulator?.cancel(); simulator = null; _status.value = BikeStatus.DISCONNECTED; connect() }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                permissionAction, UsbManager.ACTION_USB_DEVICE_ATTACHED -> connect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> disconnect()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(permissionAction)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        scope.launch { pacer() }
        scope.launch { watchdog() }
    }

    /** Desired resistance on the innate scale; written to the board as soon as the motor allows. */
    fun setResistance(innate: Int) {
        wantedInnate = innate.coerceIn(0, PowerModel.MAX_INNATE)
    }

    val resistance: Int get() = wantedInnate

    fun connect() {
        if (simulate || port != null) return
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
            .firstOrNull { it.device.vendorId == Protocol.FTDI_VENDOR }
        if (driver == null) { _status.value = BikeStatus.DISCONNECTED; return }
        if (!usb.hasPermission(driver.device)) {
            _status.value = BikeStatus.NEEDS_PERMISSION
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(permissionAction).setPackage(context.packageName)
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(context, 0, intent, flags))
            return
        }
        val conn = usb.openDevice(driver.device) ?: run { _status.value = BikeStatus.ERROR; return }
        try {
            val p = driver.ports[0]
            p.open(conn)
            p.setParameters(Protocol.BAUD, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            // DTR/RTS reset the MCU into its bootloader; keep them released.
            p.dtr = false
            p.rts = false
            port = p
        } catch (e: Exception) {
            Log.w(tag, "open failed", e)
            _status.value = BikeStatus.ERROR
            return
        }
        _status.value = BikeStatus.CONNECTING
        session = scope.launch { runSession() }
    }

    fun disconnect() {
        session?.cancel()
        session = null
        port?.let { p ->
            runCatching { p.write(Protocol.resistance(0), 200) }
            runCatching { p.write(Protocol.STOP, 200) }
            runCatching { p.close() }
        }
        port = null
        sentInnate = -1
        if (!simulate) _status.value = BikeStatus.DISCONNECTED
    }

    private suspend fun runSession() {
        val p = port ?: return
        val parser = PacketParser()
        val rpmFilter = RpmSpikeFilter()
        val hrFilter = GripHeartRateFilter()
        scope.launch { wake() }
        val buf = ByteArray(256)
        while (scope.isActive && port === p) {
            val n = try { p.read(buf, 200) } catch (e: Exception) { Log.w(tag, "read", e); break }
            if (n <= 0) continue
            val now = System.currentTimeMillis()
            for (pkt in parser.feed(buf, n)) when (pkt) {
                is Packet.Ping -> _telemetry.update { it.copy(firmware = pkt.firmware) }
                is Packet.Data -> {
                    lastDataAt = now
                    if (_status.value != BikeStatus.READY) _status.value = BikeStatus.READY
                    val rpm = rpmFilter.filter(pkt.rpm)
                    _telemetry.update {
                        it.copy(rpm = rpm, innate = pkt.innate, watts = power.watts(rpm, pkt.innate),
                            gripHr = hrFilter.update(pkt.hr1PeriodMs, now), at = now)
                    }
                }
            }
        }
        withPortLost()
    }

    private fun withPortLost() {
        if (port != null) { runCatching { port?.close() }; port = null }
        if (!simulate) _status.value = BikeStatus.DISCONNECTED
    }

    /** Same handshake as the original client: ping twice, then start until data flows. */
    private suspend fun wake() {
        repeat(10) { round ->
            write(Protocol.PING); delay(50)
            write(Protocol.PING); delay(50)
            repeat(3) {
                write(Protocol.START)
                delay(500)
                if (System.currentTimeMillis() - lastDataAt < 1000) {
                    sentInnate = -1
                    nextMoveAt = System.currentTimeMillis() + 4000
                    return
                }
            }
            Log.i(tag, "start attempt ${round + 1} got no data")
            write(Protocol.STOP)
            delay(2000)
        }
        _status.value = BikeStatus.ERROR
    }

    private suspend fun pacer() {
        while (scope.isActive) {
            delay(100)
            val want = wantedInnate
            val now = System.currentTimeMillis()
            if (want == sentInnate || now < nextMoveAt) continue
            if (simulate) {
                sentInnate = want
                continue
            }
            if (_status.value != BikeStatus.READY) continue
            if (write(Protocol.resistance(want))) {
                sentInnate = want
                nextMoveAt = now + if (want == 0) 4000 else 2000
            }
        }
    }

    /** Restart the stream if data stops (board power blip or a stray stop). */
    private suspend fun watchdog() {
        while (scope.isActive) {
            delay(3000)
            if (simulate) continue
            if (port == null) { connect(); continue }
            if (_status.value == BikeStatus.READY && System.currentTimeMillis() - lastDataAt > 4000) {
                _status.value = BikeStatus.CONNECTING
                scope.launch { wake() }
            }
        }
    }

    private fun write(bytes: ByteArray): Boolean {
        val p = port ?: return false
        return try { p.write(bytes, 500); true } catch (e: Exception) { Log.w(tag, "write", e); false }
    }

    private fun startSimulator() {
        disconnect()
        _status.value = BikeStatus.SIMULATED
        simulator?.cancel()
        simulator = scope.launch {
            var t = 0.0
            var cadence = 0.0
            while (isActive) {
                delay(330)
                t += 0.33
                val load = sentInnate.coerceAtLeast(0)
                val target = max(0.0, 82 - load * 0.12 + 6 * sin(t / 7)) + Random.nextDouble(-2.0, 2.0)
                cadence += (target - cadence) * 0.25
                val rpm = cadence.toInt()
                val hr = (95 + load * 0.35 + rpm * 0.25 + 4 * sin(t / 11)).toInt()
                _telemetry.value = BikeTelemetry(rpm, load, power.watts(rpm, load), hr, 268, System.currentTimeMillis())
            }
        }
    }
}
