package com.tisho.ride.bike

/**
 * Wire protocol of the bike control board (FTDI FT232, 115200 8N1, DTR/RTS deasserted).
 *
 * Host -> bike: ping `44 01`, start `49 01`, stop `49 00`, resistance `64 nn` (innate 0..255).
 * Bike -> host: ping reply `7B serial[8] fw[2] state`, data `39 res period[2] hr1[2] hr2[2] xor`.
 */
object Protocol {
    val PING = byteArrayOf(0x44, 0x01)
    val START = byteArrayOf(0x49, 0x01)
    val STOP = byteArrayOf(0x49, 0x00)
    fun resistance(innate: Int) = byteArrayOf(0x64, innate.coerceIn(0, 255).toByte())

    const val BAUD = 115200
    const val FTDI_VENDOR = 1027
    const val FTDI_PRODUCT = 24577
}

sealed interface Packet {
    /** Raw values: crank and heart periods in ms, 0 when absent. */
    data class Data(val innate: Int, val crankPeriodMs: Int, val hr1PeriodMs: Int, val hr2PeriodMs: Int) : Packet {
        val rpm: Int get() = if (crankPeriodMs == 0) 0 else ((60000 / crankPeriodMs) / 4.0).toInt()
    }
    data class Ping(val serial: Long, val firmware: Int, val state: Int) : Packet
}

class PacketParser {
    private val buf = ArrayList<Byte>(64)

    fun feed(bytes: ByteArray, n: Int = bytes.size): List<Packet> {
        for (i in 0 until n) buf.add(bytes[i])
        val out = ArrayList<Packet>()
        while (buf.isNotEmpty()) {
            val head = buf[0].u
            val len = when (head) { 0x39 -> 9; 0x7B -> 12; else -> 0 }
            if (len == 0) { buf.removeAt(0); continue }
            if (buf.size < len) break
            if (head == 0x39) {
                var x = 0
                for (k in 0 until 8) x = x xor buf[k].u
                if (x != buf[8].u) { buf.removeAt(0); continue }
                out += Packet.Data(buf[1].u, u16(2), u16(4), u16(6))
            } else {
                var serial = 0L
                for (k in 1..8) serial = (serial shl 8) or buf[k].u.toLong()
                out += Packet.Ping(serial, u16(9), buf[11].u)
            }
            repeat(len) { buf.removeAt(0) }
        }
        return out
    }

    private fun u16(i: Int) = (buf[i].u shl 8) or buf[i + 1].u
    private val Byte.u get() = toInt() and 0xFF
}

/** Drops single-sample cadence spikes the same way the original firmware client did. */
class RpmSpikeFilter {
    private val history = ArrayDeque<Int>()
    private var last = 0
    private var lastDiff = 0

    fun filter(raw: Int): Int {
        val ref = if (history.size >= 6) history.first() else null
        val diff = if (ref != null) raw - ref else 0
        val value = if (ref != null && diff > 40 && lastDiff < 0) last else raw
        history.addLast(raw)
        if (history.size > 6) history.removeFirst()
        lastDiff = diff
        last = value
        return value
    }
}

/**
 * Hand-grip pulse arrives as beat-to-beat periods and is noisy whenever the grip shifts.
 * Keep a short median and reject physiologically impossible or wildly off readings.
 */
class GripHeartRateFilter(private val timeoutMs: Long = 5000) {
    private val recent = ArrayDeque<Int>()
    private var lastAcceptedAt = 0L

    fun update(periodMs: Int, now: Long): Int {
        if (periodMs > 0) {
            val bpm = 60000 / periodMs
            if (bpm in 40..210) {
                val med = median()
                if (med == 0 || recent.size < 3 || kotlin.math.abs(bpm - med) <= med * 0.25) {
                    recent.addLast(bpm)
                    if (recent.size > 5) recent.removeFirst()
                    lastAcceptedAt = now
                }
            }
        }
        if (now - lastAcceptedAt > timeoutMs) recent.clear()
        return median()
    }

    private fun median(): Int {
        if (recent.isEmpty()) return 0
        val s = recent.sorted()
        return s[s.size / 2]
    }
}
