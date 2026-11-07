package fr.hybridbridge.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import com.bitchat.android.util.AppConstants
import java.util.ArrayDeque

class BitchatBleEndpoint(
    context: Context,
    private val onState: (String, Boolean) -> Unit,
    private val onPeers: (List<BitchatPeer>) -> Unit,
    private val onText: (BitchatPublicText, String) -> Unit
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var server: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var protocol: BitchatPublicProtocol? = null
    private var epoch = 0
    private var advertised = false
    private var lastSendAt = -2_000L
    private data class Link(val device: BluetoothDevice, var mtu: Int = 23, var subscribed: Boolean = false)
    private data class Notification(val link: Link, val bytes: ByteArray, val text: BitchatPublicText? = null)
    private val links = linkedMapOf<String, Link>()
    private val notifications = ArrayDeque<Notification>()
    private var pending: Notification? = null
    private val notificationTimeout = Runnable { stop(); onState("Notification BLE sans reponse", false) }
    private val announceTick = Runnable { announce(); scheduleAnnouncement() }

    fun start() {
        if (server != null) return
        try {
            val manager = context.getSystemService(BluetoothManager::class.java)
            val adapter = manager.adapter
            check(adapter != null && adapter.isEnabled) { "Activer le Bluetooth" }
            advertiser = adapter.bluetoothLeAdvertiser
            check(advertiser != null) { "Annonce BLE non disponible" }
            val identity = BitchatIdentity.load(context)
            protocol = BitchatPublicProtocol(identity)
            val session = ++epoch
            server = manager.openGattServer(context, callbacks(session, identity.senderId))
            check(server != null) { "Serveur BLE indisponible" }
            val descriptor = BluetoothGattDescriptor(AppConstants.Mesh.Gatt.DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
            characteristic = BluetoothGattCharacteristic(AppConstants.Mesh.Gatt.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE).apply { addDescriptor(descriptor) }
            val service = BluetoothGattService(AppConstants.Mesh.Gatt.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(requireNotNull(characteristic))
            check(server!!.addService(service)) { "Service BLE refuse" }
            onState("Preparation Bitchat BLE public", false)
        } catch (error: Exception) {
            stop()
            onState("Bitchat indisponible : ${error.javaClass.simpleName}", false)
        }
    }

    fun stop() {
        epoch++
        handler.removeCallbacks(announceTick)
        handler.removeCallbacks(notificationTimeout)
        pending?.text?.let { onText(it, "Transmission interrompue") }
        notifications.forEach { item -> item.text?.let { onText(it, "Transmission annulee") } }
        pending = null
        notifications.clear()
        links.clear()
        val previous = server
        server = null
        protocol = null
        characteristic = null
        advertised = false
        try { advertiseCallback?.let { advertiser?.stopAdvertising(it) } } catch (error: SecurityException) { }
        try { previous?.close() } catch (error: SecurityException) { }
        advertiser = null
        advertiseCallback = null
        onPeers(emptyList())
        onState("Bitchat arrete", false)
    }

    fun send(text: String): Result<Unit> = runCatching {
        check(advertised && links.values.any { it.subscribed }) { "Aucun client BLE abonne" }
        check(SystemClock.elapsedRealtime() - lastSendAt >= 2_000) { "Attendre 2 secondes entre deux envois" }
        val outgoing = requireNotNull(protocol).outgoing(text)
        val recipients = links.values.filter { it.subscribed && outgoing.first.size <= it.mtu - 3 }
        check(recipients.isNotEmpty()) { "MTU BLE insuffisante ; attendre la connexion complete" }
        check(notifications.size + recipients.size <= 32) { "File BLE pleine" }
        lastSendAt = SystemClock.elapsedRealtime()
        onText(outgoing.second, "En attente BLE")
        recipients.forEach { notifications.add(Notification(it, outgoing.first, outgoing.second)) }
        notifyNext()
    }

    private fun dispatch(session: Int, action: () -> Unit) {
        handler.post {
            if (session == epoch && server != null) {
                try { action() } catch (error: SecurityException) { stop(); onState("Permission Bluetooth retiree", false) }
                catch (error: IllegalStateException) { stop(); onState("Bluetooth indisponible", false) }
            }
        }
    }

    private fun callbacks(session: Int, senderId: ByteArray) = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            dispatch(session) {
                if (status != BluetoothGatt.GATT_SUCCESS) { stop(); onState("Ajout du service BLE echoue : $status", false); return@dispatch }
                advertiseCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        dispatch(session) { advertised = true; state(); scheduleAnnouncement() }
                    }
                    override fun onStartFailure(errorCode: Int) {
                        dispatch(session) { stop(); onState("Annonce BLE echouee : $errorCode", false) }
                    }
                }
                advertiser?.startAdvertising(
                    AdvertiseSettings.Builder().setConnectable(true).setTimeout(0)
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).build(),
                    AdvertiseData.Builder().addServiceUuid(ParcelUuid(AppConstants.Mesh.Gatt.SERVICE_UUID)).build(),
                    AdvertiseData.Builder().addServiceData(ParcelUuid(AppConstants.Mesh.Gatt.SERVICE_UUID), senderId).build(),
                    requireNotNull(advertiseCallback)
                )
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            dispatch(session) {
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    if (links.size >= 8) server?.cancelConnection(device)
                    else links[device.address] = Link(device)
                } else {
                    val removed = links.remove(device.address)
                    notifications.removeAll { it.link === removed }
                    if (pending?.link === removed) pending?.text?.let { onText(it, "Transmission interrompue") }
                }
                state()
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            dispatch(session) { links[device.address]?.mtu = mtu; announce(device.address); notifyNext() }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            dispatch(session) { server?.sendResponse(device, requestId, if (offset == 0) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET, offset, byteArrayOf()) }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val bytes = value.copyOf()
            dispatch(session) {
                val valid = !preparedWrite && offset == 0 && bytes.size <= 512 && links.containsKey(device.address) &&
                    characteristic.uuid == AppConstants.Mesh.Gatt.CHARACTERISTIC_UUID
                if (responseNeeded) server?.sendResponse(device, requestId, if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                if (valid) {
                    val message = protocol?.receive(bytes)
                    onPeers(protocol?.peers().orEmpty())
                    message?.let { onText(it, "Recu ; signature verifiee") }
                    state()
                }
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            dispatch(session) {
                val value = if (links[device.address]?.subscribed == true) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                server?.sendResponse(device, requestId, if (offset == 0) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET, offset, value)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val bytes = value.copyOf()
            dispatch(session) {
                val link = links[device.address]
                val enabled = bytes.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val disabled = bytes.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
                val valid = link != null && !preparedWrite && offset == 0 && descriptor.uuid == AppConstants.Mesh.Gatt.DESCRIPTOR_UUID && (enabled || disabled)
                if (responseNeeded) server?.sendResponse(device, requestId, if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                if (valid) { link!!.subscribed = enabled; if (enabled) announce(device.address); state() }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            dispatch(session) {
                val item = pending ?: return@dispatch
                if (item.link.device.address != device.address) return@dispatch
                handler.removeCallbacks(notificationTimeout)
                pending = null
                val currentLink = links.values.any { it === item.link }
                item.text?.let { onText(it, if (!currentLink) "Transmission interrompue" else if (status == BluetoothGatt.GATT_SUCCESS) "Notifie au client BLE" else "Echec notification $status") }
                notifyNext()
            }
        }
    }

    private fun state() { onState("Bitchat BLE public : ${links.size} liaisons, ${protocol?.peers()?.size ?: 0} annonces signees", advertised && links.values.any { it.subscribed }) }

    private fun announce(address: String? = null) {
        val bytes = protocol?.announcement() ?: return
        links.values.filter { it.subscribed && (address == null || it.device.address == address) && bytes.size <= it.mtu - 3 }
            .forEach { if (notifications.size < 32) notifications.add(Notification(it, bytes)) }
        notifyNext()
    }

    private fun scheduleAnnouncement() {
        handler.removeCallbacks(announceTick)
        if (server != null) handler.postDelayed(announceTick, 30_000)
    }

    @Suppress("DEPRECATION")
    private fun notifyNext() {
        val activeServer = server ?: return
        val activeCharacteristic = characteristic ?: return
        if (pending != null) return
        while (notifications.isNotEmpty()) {
            val item = notifications.removeFirst()
            if (links.values.none { it === item.link } || !item.link.subscribed) continue
            pending = item
            val started = try {
                if (Build.VERSION.SDK_INT >= 33) activeServer.notifyCharacteristicChanged(item.link.device, activeCharacteristic, false, item.bytes) == BluetoothStatusCodes.SUCCESS
                else { activeCharacteristic.value = item.bytes; activeServer.notifyCharacteristicChanged(item.link.device, activeCharacteristic, false) }
            } catch (error: SecurityException) { false }
            if (started) { handler.postDelayed(notificationTimeout, 10_000); return }
            pending = null
            item.text?.let { onText(it, "Notification BLE refusee") }
        }
    }
}
