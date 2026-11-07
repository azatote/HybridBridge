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
import android.os.SystemClock
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
    private lateinit var bitchatEndpoint: BitchatBleEndpoint
    private lateinit var bitchatStatus: TextView
    private lateinit var bitchatPeers: TextView
    private lateinit var bitchatHistory: TextView
    private lateinit var bitchatInput: EditText
    private lateinit var bitchatSend: Button
    private lateinit var publicBridge: PublicBridge
    private lateinit var bridgeStatus: TextView
    private lateinit var bridgeToggle: CheckBox
    private val bridgeTick = object : Runnable {
        override fun run() {
            if (::publicBridge.isInitialized && publicBridge.enabled) {
                publicBridge.drain()
                updateBridgeStatus()
                handler.postDelayed(this, 1_000)
            }
        }
    }
    private val bitchatMessages = linkedMapOf<String, Pair<BitchatPublicText, String>>()
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
        root.addView(TextView(this).apply { text = "HybridBridge 0.6"; textSize = 22f })
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
        bridgeStatus = TextView(this).apply { text = "Pont : desactive"; textSize = 16f }
        bridgeToggle = CheckBox(this).apply {
            text = "Relayer /hb du canal choisi vers Bitchat public"
            setOnCheckedChangeListener { _, checked -> if (checked) confirmBridge() else stopBridge() }
        }
        stateLayout.addView(bridgeToggle)
        stateLayout.addView(bridgeStatus)
        stateLayout.addView(Button(this).apply {
            text = "Licences"
            setOnClickListener {
                val text = assets.open("licenses/GPL-3.0.txt").bufferedReader().use { it.readText() } +
                    "\n\nGoogle Protocol Buffers\n" + assets.open("licenses/Protobuf-License.txt").bufferedReader().use { it.readText() } +
                    "\n\nBouncy Castle\n" + android.text.Html.fromHtml(
                        assets.open("licenses/BouncyCastle-LICENSE.html").bufferedReader().use { it.readText() },
                        android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                AlertDialog.Builder(this@MainActivity).setTitle("Licences").setMessage(text).setPositiveButton("Fermer", null).show()
            }
        })
        gattClient = MeshtasticGattClient(this, { message, ready ->
            connectionStatus.text = message
            sendButton.isEnabled = ready
            messageStatus.text = message
        }, { node -> updateNode(node) }, { message -> updateMessage(message) })
        val bitchatLayout = createBitchatInterface()
        val bridgePreferences = getSharedPreferences("bridge-dedup", MODE_PRIVATE)
        publicBridge = PublicBridge({ text -> bitchatEndpoint.send(text) }, SystemClock::elapsedRealtime,
            bridgePreferences.getStringSet("seen", emptySet()).orEmpty().toSet()) { seen ->
            check(bridgePreferences.edit().putStringSet("seen", seen.toSet()).commit()) { "Persistence refusee" }
        }
        val screens = listOf(layout, messageLayout, bitchatLayout, stateLayout)
        val labels = listOf("Appareils", "Messages", "Bitchat", "Etat")
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
        val details = "HybridBridge 0.6 - erreur de demarrage\nAndroid API ${Build.VERSION.SDK_INT}\n" +
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

    private fun confirmBridge() {
        val node = currentNode
        val channel = node?.channels?.getOrNull(channelSelector.selectedItemPosition)
        if (node == null || channel == null || !sendButton.isEnabled) {
            bridgeToggle.isChecked = false
            bridgeStatus.text = "Synchroniser le Heltec et choisir le canal dans Messages"
            return
        }
        AlertDialog.Builder(this).setTitle("Autoriser le relais public du canal ${channel.index} ?")
            .setMessage("Seuls les textes commencant par /hb seront publies sur Bitchat BLE, avec leur identifiant d'origine. Utiliser un canal de test dedie avec l'accord des auteurs. Aucune communication privee ne doit y etre transferee.")
            .setNegativeButton("Annuler") { _, _ -> bridgeToggle.isChecked = false }
            .setOnCancelListener { bridgeToggle.isChecked = false }
            .setPositiveButton("Autoriser") { _, _ ->
                val active = currentNode
                if (active == null || active.number != node.number || !sendButton.isEnabled || active.channels.none { it.index == channel.index }) {
                    bridgeToggle.isChecked = false
                    bridgeStatus.text = "Connexion ou canal change ; reautoriser le pont"
                } else {
                    publicBridge.enable(channel.index, node.number)
                    handler.removeCallbacks(bridgeTick)
                    handler.post(bridgeTick)
                    updateBridgeStatus()
                }
            }.show()
    }

    private fun stopBridge() {
        handler.removeCallbacks(bridgeTick)
        if (::publicBridge.isInitialized) publicBridge.disable()
        if (::bridgeToggle.isInitialized && bridgeToggle.isChecked) bridgeToggle.isChecked = false
        if (::bridgeStatus.isInitialized) updateBridgeStatus()
    }

    private fun updateBridgeStatus() {
        if (!::publicBridge.isInitialized) { bridgeStatus.text = "Pont : desactive"; return }
        bridgeStatus.text = "Pont ${if (publicBridge.enabled) "active" else "desactive"}\n" +
            "File : ${publicBridge.pendingCount} ; remis BLE : ${publicBridge.forwarded} ; rejetes : ${publicBridge.rejected}\n${publicBridge.lastEvent}"
    }

    private fun createBitchatInterface(): LinearLayout {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bitchatStatus = TextView(this).apply { text = "Bitchat arrete"; textSize = 16f }
        bitchatPeers = TextView(this).apply {
            text = "Aucune annonce signee"
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bitchatHistory = TextView(this).apply { text = "Aucun message public"; textSize = 16f }
        bitchatInput = EditText(this).apply {
            hint = "Message public Bitchat"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
        }
        bitchatSend = Button(this).apply {
            text = "Envoyer public"
            isEnabled = false
            setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_send, 0, 0, 0)
            setOnClickListener {
                val text = bitchatInput.text.toString()
                AlertDialog.Builder(this@MainActivity).setTitle("Diffuser ce message public ?")
                    .setMessage("Les clients Bitchat proches pourront recevoir et relayer ce texte.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Envoyer") { _, _ ->
                        bitchatEndpoint.send(text).onSuccess { bitchatInput.text.clear() }
                            .onFailure { bitchatStatus.text = it.message ?: "Envoi BLE impossible" }
                    }.show()
            }
        }
        bitchatEndpoint = BitchatBleEndpoint(this, { message, ready ->
            bitchatStatus.text = message; bitchatSend.isEnabled = ready
        }, { peers ->
            bitchatPeers.text = if (peers.isEmpty()) "Aucune annonce signee" else peers.joinToString("\n") { "${it.name} (${it.id.take(8)})" }
        }, { message, state ->
            bitchatMessages[message.id] = message to state
            if (bitchatMessages.size > 200) bitchatMessages.remove(bitchatMessages.keys.first())
            bitchatHistory.text = bitchatMessages.values.joinToString("\n\n") { "${it.first.name}\n${it.first.text}\n${it.second}" }
        })
        layout.addView(bitchatStatus)
        layout.addView(Button(this).apply { text = "Activer Bitchat BLE"; setOnClickListener { startBitchat() } })
        layout.addView(Button(this).apply { text = "Arreter Bitchat BLE"; setOnClickListener { stopBridge(); bitchatEndpoint.stop() } })
        layout.addView(bitchatPeers)
        layout.addView(ScrollView(this).apply { addView(bitchatHistory) }, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(bitchatInput)
        layout.addView(bitchatSend)
        return layout
    }

    private fun startBitchat() {
        val required = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 101); return }
        bitchatEndpoint.start()
    }

    private fun updateNode(node: MeshNode?) {
        if (node == null || currentNode?.number != node.number || currentNode?.channels != node.channels) stopBridge()
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
        if (::publicBridge.isInitialized) {
            publicBridge.receive(message)
            updateBridgeStatus()
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
        } else if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) startBitchat()
            else bitchatStatus.text = "Permissions Bitchat BLE refusees"
        }
    }
    override fun onStop() {
        stopBridge()
        if (::bitchatEndpoint.isInitialized) bitchatEndpoint.stop()
        super.onStop()
    }
    override fun onDestroy() {
        stopBridge()
        stopScan()
        if (::bitchatEndpoint.isInitialized) bitchatEndpoint.stop()
        if (::gattClient.isInitialized) gattClient.close()
        super.onDestroy()
    }
}
