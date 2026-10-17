package com.tisho.ride.hr

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class HrDevice(val address: String, val name: String, val rssi: Int)

enum class HrStatus { IDLE, SCANNING, CONNECTING, CONNECTED, UNAVAILABLE }

/** Standard Bluetooth LE Heart Rate Service (0x180D / 0x2A37) client. */
@SuppressLint("MissingPermission")
class HeartRateMonitor(private val context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var wantedAddress: String? = null

    private val _status = MutableStateFlow(if (adapter == null) HrStatus.UNAVAILABLE else HrStatus.IDLE)
    val status: StateFlow<HrStatus> = _status.asStateFlow()
    private val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm.asStateFlow()
    private val _found = MutableStateFlow<List<HrDevice>>(emptyList())
    val found: StateFlow<List<HrDevice>> = _found.asStateFlow()
    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = HrDevice(result.device.address, result.device.name ?: result.scanRecord?.deviceName ?: "Heart rate sensor", result.rssi)
            _found.value = (_found.value.filterNot { it.address == d.address } + d).sortedByDescending { it.rssi }
            if (d.address == wantedAddress && gatt == null) { stopScan(); open(result.device) }
        }
    }

    fun scan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (adapter?.isEnabled != true) { _status.value = HrStatus.UNAVAILABLE; return }
        _found.value = emptyList()
        _status.value = HrStatus.SCANNING
        runCatching {
            scanner.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback,
            )
        }
        main.postDelayed({ if (_status.value == HrStatus.SCANNING) { stopScan(); _status.value = HrStatus.IDLE } }, 15000)
    }

    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    /** Connect to a remembered sensor; reconnects automatically if it drops. */
    fun connect(address: String) {
        wantedAddress = address
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        open(device)
    }

    fun disconnect() {
        wantedAddress = null
        gatt?.close()
        gatt = null
        _bpm.value = 0
        _connectedName.value = null
        if (_status.value != HrStatus.UNAVAILABLE) _status.value = HrStatus.IDLE
    }

    private fun open(device: BluetoothDevice) {
        gatt?.close()
        _status.value = HrStatus.CONNECTING
        gatt = device.connectGatt(context, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _bpm.value = 0
                _status.value = if (wantedAddress != null) HrStatus.CONNECTING else HrStatus.IDLE
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: return
            g.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(CCCD) ?: return
            @Suppress("DEPRECATION")
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            g.writeDescriptor(desc)
            _status.value = HrStatus.CONNECTED
            _connectedName.value = g.device.name ?: "Heart rate sensor"
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            parse(ch.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            parse(value)
        }
    }

    private fun parse(v: ByteArray?) {
        if (v == null || v.size < 2) return
        val flags = v[0].toInt()
        val bpm = if (flags and 0x01 != 0 && v.size >= 3) (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8)
        else v[1].toInt() and 0xFF
        if (bpm in 25..240) _bpm.value = bpm
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
