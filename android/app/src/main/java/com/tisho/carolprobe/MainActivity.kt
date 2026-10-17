package com.tisho.carolprobe

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Remote-controlled probe for the Carol bike board.
 * adb shell am broadcast -a carol.SEND --es hex 4401      (any bytes, hex)
 * adb shell am broadcast -a carol.START | carol.STOP | carol.RES --ei level 90
 * Everything received is logged under tag CarolProbe.
 */
class MainActivity : Activity() {
    private val tag = "CarolProbe"
    private val permAction = "com.tisho.carolprobe.USB_PERMISSION"
    private var port: UsbSerialPort? = null
    @Volatile private var running = false
    private lateinit var view: TextView
    private val buf = ArrayList<Byte>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                permAction -> openPort()
                "carol.SEND" -> send(hex(i.getStringExtra("hex") ?: ""))
                "carol.START" -> Thread { startSequence() }.start()
                "carol.STOP" -> send(byteArrayOf(0x49, 0x00))
                "carol.RES" -> send(byteArrayOf(0x64, i.getIntExtra("level", 0).toByte()))
                "carol.LINES" -> port?.let {
                    it.dtr = i.getBooleanExtra("dtr", false)
                    it.rts = i.getBooleanExtra("rts", false)
                    log("dtr=${it.dtr} rts=${it.rts}")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = TextView(this).apply { textSize = 28f; typeface = Typeface.MONOSPACE; setPadding(40, 40, 40, 40) }
        setContentView(view)
        val f = IntentFilter().apply {
            listOf(permAction, "carol.SEND", "carol.START", "carol.STOP", "carol.RES", "carol.LINES").forEach(::addAction)
        }
        registerReceiver(receiver, f)
        openPort()
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        running = false
        port?.runCatching { close() }
        super.onDestroy()
    }

    private fun openPort() {
        if (port != null) return
        val usb = getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
        if (driver == null) { show("no FTDI device"); return }
        if (!usb.hasPermission(driver.device)) {
            show("requesting USB permission")
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(this, 0, Intent(permAction), 0))
            return
        }
        val conn = usb.openDevice(driver.device) ?: run { show("openDevice failed"); return }
        val p = driver.ports[0]
        p.open(conn)
        p.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        p.dtr = false
        p.rts = false
        port = p
        running = true
        log("port open: ${driver.device.deviceName}")
        Thread { readLoop(p) }.start()
    }

    private fun readLoop(p: UsbSerialPort) {
        val tmp = ByteArray(256)
        while (running) {
            val n = try { p.read(tmp, 200) } catch (e: Exception) { log("read error: $e"); break }
            if (n > 0) {
                Log.d(tag, "rx ${tmp.copyOf(n).toHex()}")
                synchronized(buf) { for (k in 0 until n) buf.add(tmp[k]); parse() }
            }
        }
    }

    private fun parse() {
        while (buf.isNotEmpty()) {
            val head = buf[0].toInt() and 0xFF
            val len = when (head) { 0x39 -> 9; 0x7B -> 12; else -> 0 }
            if (len == 0) { buf.removeAt(0); continue }
            if (buf.size < len) return
            val pkt = ByteArray(len) { buf[it] }
            if (head == 0x39) {
                var x = 0
                for (k in 0 until 8) x = x xor (pkt[k].toInt() and 0xFF)
                if (x != (pkt[8].toInt() and 0xFF)) { buf.removeAt(0); continue }
                val u = { a: Int -> ((pkt[a].toInt() and 0xFF) shl 8) or (pkt[a + 1].toInt() and 0xFF) }
                val rpm = u(2).let { if (it == 0) 0 else (60000f / it / 4f).toInt() }
                val hr1 = u(4).let { if (it == 0) 0 else 60000 / it }
                val hr2 = u(6).let { if (it == 0) 0 else 60000 / it }
                val res = pkt[1].toInt() and 0xFF
                val line = "rpm=$rpm res=$res hr1=$hr1 hr2=$hr2"
                Log.i(tag, "data $line [${pkt.toHex()}]")
                show(line)
            } else {
                log("ping [${pkt.toHex()}] fw=${((pkt[9].toInt() and 0xFF) shl 8) or (pkt[10].toInt() and 0xFF)} state=${pkt[11]}")
            }
            repeat(len) { buf.removeAt(0) }
        }
    }

    private fun startSequence() {
        send(byteArrayOf(0x44, 0x01)); Thread.sleep(50)
        send(byteArrayOf(0x44, 0x01)); Thread.sleep(50)
        send(byteArrayOf(0x49, 0x01))
    }

    private fun send(b: ByteArray) {
        val p = port ?: run { log("send ${b.toHex()}: port not open"); return }
        try { p.write(b, 500); log("tx ${b.toHex()}") } catch (e: Exception) { log("tx error: $e") }
    }

    private fun log(s: String) { Log.i(tag, s); show(s) }
    private fun show(s: String) = runOnUiThread { view.text = s }
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.toHex() = joinToString(" ") { "%02x".format(it) }
}
