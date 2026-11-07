package fr.hybridbridge.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var showAllDevices: CheckBox
    private lateinit var connectionStatus: TextView
    private lateinit var deviceSelector: Spinner
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private lateinit var gattClient: MeshtasticGattClient
    private lateinit var nodeStatus: TextView
    private lateinit var messageStatus: TextView
    private lateinit var messageHistory: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var channelSelector: Spinner
    private lateinit var channelAdapter: ArrayAdapter<String>
    private var currentNode: MeshNode? = null
    private val messages = linkedMapOf<String, MeshText>()
    private val selectableDevices = arrayListOf<BluetoothDevice>()
    private val serviceUuid = MeshtasticGattClient.SERVICE_UUID
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
                    if (!devices.containsKey(address)) {
                        selectableDevices.add(result.device)
                        deviceAdapter.add("$name ($address)")
                    }
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
        try {
            createInterface()
        } catch (error: RuntimeException) {
            showStartupDiagnostic(error)
        }
    }

    private fun createInterface() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 12) }
        root.addView(TextView(this).apply { text = "HybridBridge 0.4.1"; textSize = 22f })
        val navigation = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val content = FrameLayout(this)
        root.addView(navigation)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { text = "Prêt à rechercher un Heltec"; textSize = 16f }
        results = TextView(this).apply { text = "Aucun appareil découvert"; textSize = 15f }
        layout.addView(status)
        showAllDevices = CheckBox(this).apply { text = "Tous les appareils BLE (diagnostic)" }
        layout.addView(showAllDevices)
        layout.addView(Button(this).apply { text = "Rechercher les Heltec"; setOnClickListener { requestAndScan() } })
        layout.addView(Button(this).apply { text = "Arrêter la recherche"; setOnClickListener { stopScan() } })
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayListOf<String>()).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        deviceSelector = Spinner(this).apply {
            adapter = deviceAdapter
            contentDescription = "Appareil Bluetooth a connecter"
        }
        layout.addView(deviceSelector)
        connectionStatus = TextView(this).apply { text = "Deconnecte"; textSize = 16f }
        layout.addView(Button(this).apply { text = "Connecter"; setOnClickListener { connectSelectedDevice() } })
        layout.addView(Button(this).apply { text = "Deconnecter"; setOnClickListener { gattClient.disconnect() } })
        layout.addView(connectionStatus)
        layout.addView(ScrollView(this).apply { addView(results) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val messageLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        channelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayListOf<String>()).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        channelSelector = Spinner(this).apply { adapter = channelAdapter; contentDescription = "Canal Meshtastic" }
        messageLayout.addView(channelSelector)
        messageStatus = TextView(this).apply { text = "Non synchronise" }
        messageLayout.addView(messageStatus)
        messageHistory = TextView(this).apply { text = "Aucun message"; textSize = 16f }
        messageLayout.addView(ScrollView(this).apply { addView(messageHistory) }, LinearLayout.LayoutParams(-1, 0, 1f))
        messageInput = EditText(this).apply {
            hint = "Message de canal"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
        }
        messageLayout.addView(messageInput)
        sendButton = Button(this).apply {
            text = "Envoyer"
            isEnabled = false
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_send, 0, 0, 0)
            setOnClickListener { confirmSend() }
        }
        messageLayout.addView(sendButton)
        val stateLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nodeStatus = TextView(this).apply { text = "Aucun noeud synchronise"; textSize = 16f }
        stateLayout.addView(nodeStatus)
        stateLayout.addView(TextView(this).apply { text = "Bitchat : non integre\nPont : desactive"; textSize = 16f })
        stateLayout.addView(Button(this).apply {
            text = "Licences"
            setOnClickListener {
                val text = assets.open("licenses/GPL-3.0.txt").bufferedReader().use { it.readText() } +
                    "\n\nGoogle Protocol Buffers\n" + assets.open("licenses/Protobuf-License.txt").bufferedReader().use { it.readText() }
                AlertDialog.Builder(this@MainActivity).setTitle("Licences").setMessage(text).setPositiveButton("Fermer", null).show()
            }
        })
        gattClient = MeshtasticGattClient(this, { message, ready ->
            connectionStatus.text = message
            sendButton.isEnabled = ready
            messageStatus.text = message
        }, { node -> updateNode(node) }, { message -> updateMessage(message) })
        val screens = listOf(layout, messageLayout, stateLayout)
        val labels = listOf("Appareils", "Messages", "Etat")
        val navigationIds = labels.map { android.view.View.generateViewId() }
        screens.forEachIndexed { index, screen ->
            screen.visibility = if (index == 0) android.view.View.VISIBLE else android.view.View.GONE
            content.addView(screen, FrameLayout.LayoutParams(-1, -1))
            navigation.addView(RadioButton(this).apply {
                id = navigationIds[index]
                text = labels[index]
                textSize = 14f
            }, RadioGroup.LayoutParams(0, -2, 1f))
        }
        navigation.check(navigationIds.first())
        navigation.setOnCheckedChangeListener { _, checkedId ->
            val selected = navigationIds.indexOf(checkedId)
            screens.forEachIndexed { index, screen ->
                screen.visibility = if (index == selected) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        setContentView(root)
    }

    private fun showStartupDiagnostic(error: RuntimeException) {
        val details = "HybridBridge 0.4.1 - erreur de demarrage\nAndroid API ${Build.VERSION.SDK_INT}\n" +
            error.javaClass.name + "\n" + error.stackTrace.take(8).joinToString("\n") {
                "${it.className}.${it.methodName}:${it.lineNumber}"
            }
        val fallback = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        fallback.addView(TextView(this).apply { text = details; textSize = 14f }, LinearLayout.LayoutParams(-1, 0, 1f))
        fallback.addView(Button(this).apply {
            text = "Partager le diagnostic"
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, details)
                }, "Diagnostic HybridBridge"))
            }
        })
        setContentView(fallback)
    }

    private fun updateNode(node: MeshNode?) {
        val selectedIndex = currentNode?.channels?.getOrNull(channelSelector.selectedItemPosition)?.index
        val changedChannels = currentNode?.channels != node?.channels
        currentNode = node
        if (changedChannels) {
            channelAdapter.clear()
            node?.channels?.forEach { channel -> channelAdapter.add("${channel.index} : ${channel.name}") }
            val position = node?.channels?.indexOfFirst { it.index == selectedIndex } ?: -1
            if (position >= 0) channelSelector.setSelection(position)
        }
        nodeStatus.text = if (node == null) "Aucun noeud synchronise" else
            "${node.name}\n${MeshtasticProtocol.nodeLabel(node.number)}\nFirmware : ${node.firmware}\nBatterie : ${node.battery?.let { "$it %" } ?: "indisponible"}\nCanaux actifs : ${node.channels.size}"
        if (node == null) sendButton.isEnabled = false
    }

    private fun updateMessage(message: MeshText) {
        messages["${message.sender}:${message.id}"] = message
        if (messages.size > 300) messages.remove(messages.keys.first())
        messageHistory.text = messages.values.joinToString("\n\n") {
            val sender = if (it.outgoing) "Moi" else MeshtasticProtocol.nodeLabel(it.sender)
            "$sender • canal ${it.channel}\n${it.text}\n${it.state}"
        }
    }

    private fun confirmSend() {
        val channel = currentNode?.channels?.getOrNull(channelSelector.selectedItemPosition) ?: return
        val text = messageInput.text.toString()
        AlertDialog.Builder(this)
            .setTitle("Envoyer sur ${channel.name} ?")
            .setMessage("Ce message sera diffuse sur le canal Meshtastic ${channel.index}.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer") { _, _ ->
                gattClient.sendChannelMessage(channel.index, text)
                    .onSuccess { messageInput.text.clear() }
                    .onFailure { messageStatus.text = it.message ?: "Envoi impossible" }
            }.show()
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
        devices.clear(); seenAddresses.clear(); selectableDevices.clear(); deviceAdapter.clear()
        results.text = "Recherche en cours…"
        diagnosticScan = showAllDevices.isChecked
        val scanner = bluetooth.bluetoothLeScanner
        if (scanner == null) { status.text = "Scanner BLE indisponible"; return }
        scanning = true
        try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            status.text = "Recherche BLE active pendant 90 secondes"
            handler.postDelayed(scanTimeout, 90_000)
        } catch (error: SecurityException) {
            scanning = false
            status.text = "Permission Bluetooth manquante"
        } catch (error: IllegalStateException) {
            scanning = false
            status.text = "Bluetooth indisponible"
        }
    }
    private fun connectSelectedDevice() {
        val device = selectableDevices.getOrNull(deviceSelector.selectedItemPosition)
        if (device == null) {
            connectionStatus.text = "Selectionner un appareil detecte"
            return
        }
        stopScan()
        gattClient.connect(device)
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
    override fun onDestroy() {
        stopScan()
        if (::gattClient.isInitialized) gattClient.close()
        super.onDestroy()
    }
}
