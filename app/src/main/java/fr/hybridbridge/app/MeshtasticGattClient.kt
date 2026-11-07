package fr.hybridbridge.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class MeshtasticGattClient(context: Context, private val onState: (String, Boolean) -> Unit) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var activeGatt: BluetoothGatt? = null
    private var packetCounter: Long? = null
    private val timeout = Runnable { fail("Delai de connexion ou de lecture depasse") }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            dispatch(gatt) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fail("Erreur GATT $status ; verifier Bluetooth et appairage")
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onState("Liaison BLE etablie ; verification du service Meshtastic", false)
                    try {
                        if (!gatt.discoverServices()) fail("Decouverte des services impossible")
                    } catch (error: SecurityException) {
                        fail("Permission Bluetooth manquante")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("Heltec deconnecte")
                }
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
                handler.removeCallbacks(timeout)
                onState("GATT Meshtastic verifie ; FromNum = $packetCounter", true)
                val battery = gatt.getService(BATTERY_SERVICE_UUID)?.getCharacteristic(BATTERY_LEVEL_UUID)
                if (battery != null && battery.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                    try {
                        if (!gatt.readCharacteristic(battery)) handler.removeCallbacks(timeout)
                    } catch (error: SecurityException) {
                        fail("Permission Bluetooth manquante")
                    }
                } else {
                    handler.removeCallbacks(timeout)
                }
            } else if (uuid == BATTERY_LEVEL_UUID) {
                handler.removeCallbacks(timeout)
                val batteryLevel = value.firstOrNull()?.toInt()?.and(0xff)
                val batteryStatus = if (status == BluetoothGatt.GATT_SUCCESS && value.size == 1 &&
                    batteryLevel != null && batteryLevel in 0..100) "Batterie : $batteryLevel %" else "Batterie indisponible"
                onState("GATT Meshtastic verifie ; FromNum = $packetCounter\n$batteryStatus", true)
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
