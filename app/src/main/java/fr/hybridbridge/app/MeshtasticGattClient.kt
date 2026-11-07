package fr.hybridbridge.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.security.SecureRandom
import org.meshtastic.proto.ChannelProtos

class MeshtasticGattClient(
    context: Context,
    private val onState: (String, Boolean) -> Unit,
    private val onNode: (MeshNode?) -> Unit = {},
    private val onMessage: (MeshText) -> Unit = {}
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var activeGatt: BluetoothGatt? = null
    private var packetCounter: Long? = null
    private val random = SecureRandom()
    private var mtu = 23
    private var servicesRequested = false
    private var requestId = 0
    private var nodeNumber: Int? = null
    private var nodeName = ""
    private var firmware = ""
    private var ready = false
    private var lastMessageQueuedAt = -15_000L
    private val channels = linkedMapOf<Int, MeshChannel>()
    private val seenMessages = linkedSetOf<String>()
    private val operations = java.util.ArrayDeque<Operation>()
    private var currentOperation: Operation? = null
    private data class Operation(val readUuid: UUID? = null, val bytes: ByteArray? = null, val message: MeshText? = null)
    private val timeout = Runnable { fail("Delai de connexion ou de lecture depasse") }
    private val operationTimeout = Runnable { fail("Operation GATT sans reponse") }
    private val poll = Runnable {
        if (activeGatt != null && currentOperation == null && operations.isEmpty() && requestId != 0) {
            operations.add(Operation(readUuid = FROM_RADIO_UUID))
            nextOperation()
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            dispatch(gatt) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Erreur GATT $status ; verifier Bluetooth et appairage")
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onState("Liaison BLE etablie ; verification du service Meshtastic", false)
                    try {
                        if (!gatt.requestMtu(512)) discover(gatt)
                    } catch (error: SecurityException) {
                        fail("Permission Bluetooth manquante")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("Heltec deconnecte")
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, negotiatedMtu: Int, status: Int) {
            dispatch(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) mtu = negotiatedMtu
                if (!servicesRequested) discover(gatt)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            dispatch(gatt) {
                if (characteristic.uuid != TO_RADIO_UUID || currentOperation?.bytes == null) return@dispatch
                val message = currentOperation?.message
                if (message != null) {
                    onMessage(message.copy(state = if (status == BluetoothGatt.GATT_SUCCESS) "Transmis au Heltec" else "Echec GATT $status"))
                }
                finishOperation()
                if (status != BluetoothGatt.GATT_SUCCESS) fail("Ecriture GATT echouee : $status")
                else nextOperation()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            dispatch(gatt) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Decouverte des services echouee : $status")
                    return@dispatch
                }
                val service = gatt.getService(SERVICE_UUID)
                val toRadio = service?.getCharacteristic(TO_RADIO_UUID)
                val fromRadio = service?.getCharacteristic(FROM_RADIO_UUID)
                val fromNum = service?.getCharacteristic(FROM_NUM_UUID)
                if (toRadio == null || fromRadio == null || fromNum == null ||
                    toRadio.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                    fromRadio.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0 ||
                    fromNum.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
                    fail("Service ou caracteristiques Meshtastic attendus absents")
                    return@dispatch
                }
                onState("Service Meshtastic trouve ; lecture FromNum (appairage Android si demande)", false)
                read(gatt, fromNum)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleRead(gatt, characteristic.uuid, value.copyOf(), status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < 33) {
                handleRead(gatt, characteristic.uuid, characteristic.value?.copyOf() ?: byteArrayOf(), status)
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        close()
        onState("Connexion BLE en cours ; accepter l'appairage Android si demande", false)
        try {
            activeGatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (activeGatt == null) {
                fail("Impossible de creer la connexion GATT")
            } else {
                handler.postDelayed(timeout, 90_000)
            }
        } catch (error: SecurityException) {
            fail("Permission Bluetooth manquante")
        } catch (error: IllegalArgumentException) {
            fail("Appareil Bluetooth invalide")
        }
    }

    fun disconnect() {
        close()
        onState("Deconnecte", false)
    }

    fun close() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(operationTimeout)
        handler.removeCallbacks(poll)
        currentOperation?.message?.let { onMessage(it.copy(state = "Transmission interrompue")) }
        operations.forEach { operation -> operation.message?.let { onMessage(it.copy(state = "Transmission annulee")) } }
        currentOperation = null
        operations.clear()
        ready = false
        requestId = 0
        nodeNumber = null
        nodeName = ""
        firmware = ""
        channels.clear()
        seenMessages.clear()
        mtu = 23
        servicesRequested = false
        onNode(null)
        val previous = activeGatt
        activeGatt = null
        packetCounter = null
        if (previous != null) {
            try {
                previous.disconnect()
            } catch (error: SecurityException) {
                onState("Permission Bluetooth retiree", false)
            } finally {
                try {
                    previous.close()
                } catch (error: SecurityException) {
                    onState("Permission Bluetooth retiree", false)
                }
            }
        }
    }

    private fun dispatch(gatt: BluetoothGatt, action: () -> Unit) {
        handler.post { if (activeGatt === gatt) action() }
    }

    private fun discover(gatt: BluetoothGatt) {
        servicesRequested = true
        if (!gatt.discoverServices()) fail("Decouverte des services impossible")
    }

    fun sendChannelMessage(channelIndex: Int, text: String): Result<Unit> = runCatching {
        check(ready && nodeNumber != null) { "Noeud non synchronise" }
        check(channels.containsKey(channelIndex)) { "Canal non actif" }
        check(operations.size < 16) { "File d'envoi pleine" }
        var packetId = random.nextInt()
        while (packetId == 0) packetId = random.nextInt()
        val bytes = MeshtasticProtocol.channelText(nodeNumber!!, channelIndex, packetId, text)
        require(bytes.size <= minOf(mtu - 3, MeshtasticProtocol.MAX_GATT_BYTES)) { "Message trop long pour la liaison BLE negociee" }
        val now = SystemClock.elapsedRealtime()
        check(now - lastMessageQueuedAt >= 15_000) { "Attendre 15 secondes entre deux envois de test" }
        val message = MeshText(packetId, nodeNumber!!, channelIndex, text, true, "En attente")
        lastMessageQueuedAt = now
        remember(message)
        onMessage(message)
        operations.add(Operation(bytes = bytes, message = message))
        nextOperation()
    }

    private fun beginSynchronization() {
        do { requestId = random.nextInt() } while (requestId == 0)
        onState("GATT verifie ; synchronisation du noeud Meshtastic", false)
        operations.add(Operation(bytes = MeshtasticProtocol.configurationRequest(requestId)))
        nextOperation()
    }

    @Suppress("DEPRECATION")
    private fun nextOperation() {
        val gatt = activeGatt ?: return
        if (currentOperation != null) return
        handler.removeCallbacks(poll)
        val operation = operations.pollFirst()
        if (operation == null) {
            handler.postDelayed(poll, 1_000)
            return
        }
        currentOperation = operation
        val started = try {
            if (operation.bytes != null) {
                val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(TO_RADIO_UUID)
                if (characteristic == null || operation.bytes.size > minOf(mtu - 3, 512)) false
                else if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeCharacteristic(characteristic, operation.bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                } else {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    characteristic.value = operation.bytes
                    gatt.writeCharacteristic(characteristic)
                }
            } else {
                val characteristic = if (operation.readUuid == BATTERY_LEVEL_UUID) {
                    gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_UUID)
                } else gatt.getService(SERVICE_UUID)?.getCharacteristic(operation.readUuid)
                characteristic != null && gatt.readCharacteristic(characteristic)
            }
        } catch (error: SecurityException) {
            false
        }
        if (!started) {
            if (operation.readUuid == BATTERY_LEVEL_UUID) { finishOperation(); nextOperation() }
            else fail("Operation GATT impossible ; verifier permissions et liaison")
        } else handler.postDelayed(operationTimeout, 15_000)
    }

    private fun finishOperation() {
        handler.removeCallbacks(operationTimeout)
        currentOperation = null
    }

    private fun remember(message: MeshText): Boolean {
        val added = seenMessages.add("${message.sender}:${message.id}")
        if (seenMessages.size > 512) seenMessages.remove(seenMessages.first())
        return added
    }

    private fun handleFromRadio(value: ByteArray) {
        if (value.isEmpty()) return
        val incoming = try { MeshtasticProtocol.decode(value) } catch (error: Exception) {
            fail("Paquet Meshtastic invalide")
            return
        }
        if (incoming.hasMyInfo()) nodeNumber = incoming.myInfo.myNodeNum
        if (incoming.hasMetadata()) firmware = incoming.metadata.firmwareVersion
        if (incoming.hasNodeInfo() && incoming.nodeInfo.num == nodeNumber && incoming.nodeInfo.hasUser()) {
            nodeName = incoming.nodeInfo.user.longName.take(80)
        }
        if (incoming.hasChannel()) {
            val channel = incoming.channel
            if (channel.index in 0..7 && (channel.role == ChannelProtos.Channel.Role.PRIMARY ||
                channel.role == ChannelProtos.Channel.Role.SECONDARY)) {
                channels[channel.index] = MeshChannel(channel.index, channel.settings.name.take(32).ifBlank { "Canal ${channel.index}" })
            }
        }
        if (incoming.hasConfigCompleteId() && incoming.configCompleteId == requestId && !ready) {
            val number = nodeNumber
            if (number == null || number == 0 || number == -1 || !firmware.startsWith("2.7.") || channels.isEmpty()) {
                fail("Configuration incomplete ou firmware hors cible 2.7.x")
                return
            }
            ready = true
            handler.removeCallbacks(timeout)
            onNode(MeshNode(number, nodeName.ifBlank { MeshtasticProtocol.nodeLabel(number) }, firmware, channels.values.toList()))
            onState("Meshtastic synchronise ; firmware $firmware", true)
            val battery = activeGatt?.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_UUID)
            if (battery != null && battery.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                operations.add(Operation(readUuid = BATTERY_LEVEL_UUID))
            }
        }
        if (ready && incoming.hasPacket()) {
            val message = MeshtasticProtocol.receivedText(incoming.packet)
            if (message != null && channels.containsKey(message.channel) && remember(message)) onMessage(message)
        }
        operations.add(Operation(readUuid = FROM_RADIO_UUID))
    }

    private fun read(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            if (!gatt.readCharacteristic(characteristic)) fail("Lecture GATT impossible")
        } catch (error: SecurityException) {
            fail("Permission Bluetooth manquante")
        }
    }

    private fun handleRead(gatt: BluetoothGatt, uuid: UUID, value: ByteArray, status: Int) {
        dispatch(gatt) {
            if (uuid == FROM_NUM_UUID) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    val reason = if (status == 5 || status == 15) {
                        "Appairage requis ; verifier la demande Android puis reconnecter"
                    } else "Lecture FromNum echouee : $status"
                    fail(reason)
                    return@dispatch
                }
                if (value.size != 4) {
                    fail("FromNum invalide : ${value.size} octets au lieu de 4")
                    return@dispatch
                }
                packetCounter = decodeFromNum(value)
                beginSynchronization()
            } else if (uuid == FROM_RADIO_UUID) {
                finishOperation()
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Lecture FromRadio echouee : $status")
                    return@dispatch
                }
                handleFromRadio(value)
                nextOperation()
            } else if (uuid == BATTERY_LEVEL_UUID) {
                finishOperation()
                val batteryLevel = value.firstOrNull()?.toInt()?.and(0xff)
                if (status == BluetoothGatt.GATT_SUCCESS && value.size == 1 && batteryLevel != null && batteryLevel in 0..100) {
                    nodeNumber?.let { onNode(MeshNode(it, nodeName.ifBlank { MeshtasticProtocol.nodeLabel(it) }, firmware, channels.values.toList(), batteryLevel)) }
                }
                nextOperation()
            }
        }
    }

    private fun fail(reason: String) {
        close()
        onState(reason, false)
    }

    companion object {
        internal fun decodeFromNum(value: ByteArray): Long {
            require(value.size == 4) { "FromNum doit contenir 4 octets" }
            return ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        }

        val SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        private val TO_RADIO_UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        private val FROM_RADIO_UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        private val FROM_NUM_UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
        private val BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        private val BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    }
}
