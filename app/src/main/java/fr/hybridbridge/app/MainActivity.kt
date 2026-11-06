package fr.hybridbridge.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

/** BLE discovery milestone only: no GATT connection or Meshtastic messaging yet. */
class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var showAllDevices: CheckBox
    private val serviceUuid = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
    private val bluetooth by lazy { getSystemService(BluetoothManager::class.java).adapter }
    private var scanning = false
    private var diagnosticScan = false
    private val handler = Handler(Looper.getMainLooper())
    private val scanTimeout = Runnable { stopScan() }
    private val devices = linkedMapOf<String, String>()
    private val seenAddresses = mutableSetOf<String>()
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            runOnUiThread {
                if (!scanning) return@runOnUiThread
                val address = result.device.address
                seenAddresses.add(address)
                val advertisesMeshtastic = result.scanRecord?.serviceUuids?.contains(ParcelUuid(serviceUuid)) == true
                if (advertisesMeshtastic || diagnosticScan) {
                    val name = result.scanRecord?.deviceName ?: result.device.name ?: "Appareil sans nom"
                    val service = if (advertisesMeshtastic) "Service Meshtastic annonce" else "BLE non identifie"
                    devices[address] = "$name  •  $address  •  ${result.rssi} dBm\n$service"
                    results.text = devices.values.joinToString("\n\n")
                }
                status.text = "Recherche active : ${seenAddresses.size} appareils BLE, ${devices.size} affiches"
            }
        }
        override fun onBatchScanResults(scanResults: MutableList<ScanResult>) {
            scanResults.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            handler.removeCallbacks(scanTimeout)
            runOnUiThread { status.text = "Échec du scan BLE : $errorCode" }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 24) }
        layout.addView(TextView(this).apply { text = "HybridBridge 0.2 — découverte Meshtastic"; textSize = 22f })
        status = TextView(this).apply { text = "Prêt à rechercher un Heltec"; textSize = 16f }
        results = TextView(this).apply { text = "Aucun appareil découvert"; textSize = 15f }
        layout.addView(status)
        showAllDevices = CheckBox(this).apply { text = "Tous les appareils BLE (diagnostic)" }
        layout.addView(showAllDevices)
        layout.addView(Button(this).apply { text = "Rechercher les Heltec"; setOnClickListener { requestAndScan() } })
        layout.addView(Button(this).apply { text = "Arrêter la recherche"; setOnClickListener { stopScan() } })
        layout.addView(ScrollView(this).apply { addView(results) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
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
        devices.clear(); seenAddresses.clear(); results.text = "Recherche en cours…"
        diagnosticScan = showAllDevices.isChecked
        val scanner = bluetooth.bluetoothLeScanner
        if (scanner == null) { status.text = "Scanner BLE indisponible"; return }
        scanning = true
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            status.text = "Recherche BLE active pendant 30 secondes"
            handler.postDelayed(scanTimeout, 30_000)
        } catch (error: SecurityException) {
            scanning = false
            status.text = "Permission Bluetooth manquante"
        } catch (error: IllegalStateException) {
            scanning = false
            status.text = "Bluetooth indisponible"
        }
    }
    private fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        if (scanning && checkSelfPermission(if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            bluetooth?.bluetoothLeScanner?.stopScan(callback)
        }
        scanning = false
        if (::status.isInitialized) {
            status.text = "Recherche arretee : ${seenAddresses.size} appareils BLE, ${devices.size} affiches"
            if (devices.isEmpty()) results.text = "Aucun appareil correspondant detecte"
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) requestAndScan()
            else status.text = "Permissions refusees : verifier les autorisations de HybridBridge"
        }
    }
    override fun onDestroy() { stopScan(); super.onDestroy() }
}
