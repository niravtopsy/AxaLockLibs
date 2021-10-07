package com.axalocklibs.service

import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.*

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
class AxaLockService : Service() {
    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothDeviceAddress: String? = null
    private var mBluetoothGatt: BluetoothGatt? = null
    private var mConnectionState = STATE_DISCONNECTED

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val intentAction: String
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED
                mConnectionState = STATE_CONNECTED
                broadcastUpdate(intentAction)
                Log.i(TAG, "Connected to GATT server.")
                // Attempts to discover services after successful connection.
                Log.i(
                    TAG, "Attempting to start service discovery:" +
                            mBluetoothGatt!!.discoverServices()
                )
                gatt.readRemoteRssi()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED
                mConnectionState = STATE_DISCONNECTED
                Log.i(TAG, "Disconnected from GATT server.")
                broadcastUpdate(intentAction)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "mBluetoothGatt = $mBluetoothGatt")
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }

        /**
         * indicates a write was queued up by the stack and you can now write more
         */
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onCharacteristicWrite callback success")
                broadcastUpdate(ACTION_ON_WRITE_CHAR, characteristic)
            }
        }
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun broadcastUpdate(
        action: String,
        characteristic: BluetoothGattCharacteristic
    ) {
        val intent = Intent(action)

        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        if (TX_CHAR_UUID == characteristic.uuid) {

            // Log.d(TAG, String.format("Received TX: %d",characteristic.getValue() ));
            intent.putExtra(EXTRA_DATA, characteristic.value)
        } else {
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    inner class LocalBinder : Binder() {
        val service: AxaLockService
            get() = this@AxaLockService
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close()
        return super.onUnbind(intent)
    }

    private val mBinder: IBinder = LocalBinder()

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    fun initialize(): Boolean {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }
        mBluetoothAdapter = mBluetoothManager!!.adapter
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun connect(address: String?): Boolean {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address == mBluetoothDeviceAddress && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.")
            return if (mBluetoothGatt!!.connect()) {
                mConnectionState = STATE_CONNECTING
                true
            } else {
                false
            }
        }
        val device = mBluetoothAdapter!!.getRemoteDevice(address)
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.")
            return false
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
//        mBluetoothGatt = device.connectGatt(this, false, mGattCallback,
//                BluetoothDevice.DEVICE_TYPE_LE);
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback)

//        mBluetoothGatt = device.connectGatt(this, true, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.")
        mBluetoothDeviceAddress = address
        mConnectionState = STATE_CONNECTING
//                device.createBond();
        return true
    }

    val isConnected: Boolean
        get() = mConnectionState == STATE_CONNECTED

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * `BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)`
     * callback.
     */
    fun disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt!!.disconnect()
        // mBluetoothGatt.close();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    fun close() {
        if (mBluetoothGatt == null) {
            return
        }
        Log.w(TAG, "mBluetoothGatt closed")
        mBluetoothDeviceAddress = null
        mBluetoothGatt!!.close()
        mBluetoothGatt = null
    }

    /**
     * Request a read on a given `BluetoothGattCharacteristic`. The read result is reported
     * asynchronously through the `BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)`
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return
        }
        mBluetoothGatt!!.readCharacteristic(characteristic)
    }

    /**
     * Enable TXNotification
     *
     * @return
     */
    fun enableTXNotification() {
        try {
            val RxService = mBluetoothGatt!!.getService(ERL_SERVICE_UUID)
            if (RxService == null) {
                showMessage("Rx service not found!")
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_ERL)
                return
            }
            val TxChar = RxService.getCharacteristic(TX_CHAR_UUID)
            if (TxChar == null) {
                showMessage("Tx charateristic not found!")
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_ERL)
                return
            }
            mBluetoothGatt!!.setCharacteristicNotification(TxChar, true)
            val descriptor = TxChar.getDescriptor(CCCD)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            mBluetoothGatt!!.writeDescriptor(descriptor)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //      TxChar.getValue();
        //      Log.d(TAG, "write TXchar - status=" + status);
    }

    fun writeRXCharacteristic(value: ByteArray?, writeType: Int) {
        try {
            val RxService = mBluetoothGatt!!.getService(ERL_SERVICE_UUID)
            if (RxService == null) {
                showMessage("Rx service not found!")
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_ERL)
                return
            }
            val RxChar = RxService.getCharacteristic(RX_CHAR_UUID)
            if (RxChar == null) {
                showMessage("Rx charateristic not found!")
                broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_ERL)
                return
            }
            RxChar.value = value
            RxChar.writeType = writeType // wfr
            val status = mBluetoothGatt!!.writeCharacteristic(RxChar)
            Log.d(TAG, "write RXchar - status=$status")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun writeRXCharacteristicCommand(value: ByteArray?) {
        // writing with NO RESPONSE is known as a Write Command.
        writeRXCharacteristic(value, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    fun writeRXCharacteristicRequest(value: ByteArray?) {
        // writing with WRITE_TYPE_DEFAULT means it expects a response - this is known as Write Request.
        // And no, this is not the 'default' setting for a characteristic. You must set it
        // or it defaults to WRITE_TYPE_NO_REPONSE. Makes sense ?!?
        writeRXCharacteristic(value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }

    private fun showMessage(msg: String) {
        Log.e(TAG, msg)
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after `BluetoothGatt#discoverServices()` completes successfully.
     *
     * @return A `List` of supported services.
     */
    val supportedGattServices: List<BluetoothGattService>?
        get() = if (mBluetoothGatt == null) null else mBluetoothGatt!!.services

    fun getbattery() {
        val batteryService = mBluetoothGatt!!.getService(TX_POWER_UUID)
        if (batteryService == null) {
            Log.d(TAG, "Battery service not found!")
            return
        }
        val batteryLevel = batteryService.getCharacteristic(TX_POWER_LEVEL_UUID)
        if (batteryLevel == null) {
            Log.d(TAG, "Battery level not found!")
            return
        }
        mBluetoothGatt!!.readCharacteristic(batteryLevel)
        Log.v(TAG, "batteryLevel = " + mBluetoothGatt!!.readCharacteristic(batteryLevel))
    }

    fun readData() {
        val RxService = mBluetoothGatt!!.getService(ERL_SERVICE_UUID)
        if (RxService == null) {
            showMessage("Rx service not found!")
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_ERL)
            return
        }
        val TxChar = RxService.getCharacteristic(TX_CHAR_UUID)
        if (TxChar == null) {
            showMessage("Tx charateristic not found!")
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_ERL)
            return
        }
        readCharacteristic(TxChar)
    }

    companion object {
        private const val TAG = "lbs_tag_service"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
        const val ACTION_GATT_CONNECTED = "com.MobiComm.Axa_eRL_Demo.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.MobiComm.Axa_eRL_Demo.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.MobiComm.Axa_eRL_Demo.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.MobiComm.Axa_eRL_Demo.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.MobiComm.Axa_eRL_Demo.EXTRA_DATA"
        const val DEVICE_DOES_NOT_SUPPORT_ERL =
            "com.MobiComm.Axa_eRL_Demo.DEVICE_DOES_NOT_SUPPORT_ERL"
        const val ACTION_BOND_STATE_CHANGED = "com.MobiComm.Axa_eRL_Demo.ACTION_BOND_STATE_CHANGED"

        //wfr
        const val ACTION_ON_WRITE_CHAR = "com.MobiComm.Axa_eRL_Demo.ACTION_ON_WRITE_CHAR"
        val TX_POWER_UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
        val TX_POWER_LEVEL_UUID = UUID.fromString("00002a07-0000-1000-8000-00805f9b34fb")
        val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISON_UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val DIS_UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")

        // PRODUCTION UUID
        val ERL_SERVICE_UUID = UUID.fromString("00001523-e513-11e5-9260-0002a5d5c51b")
        val TX_CHAR_UUID = UUID.fromString("00001524-e513-11e5-9260-0002a5d5c51b")
        val RX_CHAR_UUID = UUID.fromString("00001525-e513-11e5-9260-0002a5d5c51b")
        val TIME_CHAR_UUID = UUID.fromString("00001526-e513-11e5-9260-0002a5d5c51b")
        val ERL_SERVICE_UUID_ARRAY =
            arrayOf(UUID.fromString("00001523-e513-11e5-9260-0002a5d5c51b"))
    }
}