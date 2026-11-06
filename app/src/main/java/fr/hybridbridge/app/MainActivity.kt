package fr.hybridbridge.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

/** BLE discovery milestone only: no GATT connection or Meshtastic messaging yet. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var results: TextView
    private val serviceUuid = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
    private val bluetooth by lazy { getSystemService(BluetoothManager::class.java).adapter }
    private var scanning = false
    private val devices = linkedMapOf<String, String>()
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: "Appareil sans nom"
            devices[result.device.address] = "$name  •  ${result.device.address}  •  ${result.rssi} dBm"
            runOnUiThread { results.text = devices.values.joinToString("\n\n") }
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            runOnUiThread { status.text = "Échec du scan BLE : $errorCode" }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 24) }
        layout.addView(TextView(this).apply { text = "HybridBridge 0.1 — découverte Meshtastic"; textSize = 22f })
        status = TextView(this).apply { text = "Prêt à rechercher un Heltec"; textSize = 16f }
        results = TextView(this).apply { text = "Aucun appareil découvert"; textSize = 15f }
        layout.addView(status)
        layout.addView(Button(this).apply { text = "Rechercher les Heltec"; setOnClickListener { requestAndScan() } })
        layout.addView(Button(this).apply { text = "Arrêter la recherche"; setOnClickListener { stopScan() } })
        layout.addView(results)
        setContentView(layout)
    }
    private fun requestAndScan() {
        val required = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 100); return }
        if (bluetooth == null || !bluetooth.isEnabled) {
            Toast.makeText(this, "Active le Bluetooth", Toast.LENGTH_LONG).show(); return
        }
        stopScan()
        devices.clear(); results.text = "Recherche en cours…"
        val scanner = bluetooth.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        scanning = true
        status.text = "Recherche BLE Meshtastic active"
    }
    private fun stopScan() {
        if (scanning && checkSelfPermission(if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            bluetooth?.bluetoothLeScanner?.stopScan(callback)
        }
        scanning = false
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) requestAndScan()
    }
    override fun onDestroy() { stopScan(); super.onDestroy() }
}
