package com.axalocklibs.singletone

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.axalocklibs.`interface`.IAPIAxaLockCallback
import com.axalocklibs.`interface`.IAPIResponse
import com.axalocklibs.service.AxaLockService
import com.axalocklibs.service.AxaLockService.LocalBinder
import com.axalocklibs.webservice.ApiRequest
import com.google.gson.Gson
import com.kolonishare.booking.model.ekey.ModelAxaEKeyResponse
import com.pixplicity.easyprefs.library.Prefs
import java.text.DateFormat
import java.util.*

class AxaSingleToneClass : IAPIResponse {

    private var mService: AxaLockService? = null
    private var mDevice: BluetoothDevice? = null
    private var mBtAdapter: BluetoothAdapter? = null
    private val mWriteQueue = LinkedList<ByteArray>()
    private var mOTPasskeyNr = 0
    private lateinit var mOTPKeyparts: Array<String>
    private var mEKeyAscii: String? = null
    private lateinit var mEkeyBinary: ByteArray
    private var mWaitingWriteChar = false
    private val mPassKeyInt = 0
    private var mState = ERL_PROFILE_DISCONNECTED
    private var isUnLockCommand = "1"
    private var mActivity: Activity? = null
    private val TAG_AXA_UPDATE_EKEY = "TAG_AXA_UPDATE_EKEY"
    private var connectToClickMacId = ""
    private var passBookingObjectId = ""
    private var isDisconnectByClick = "1" // 1 = false 2 = true 3 = nothing
    private var webServiceURL = "" // 1 = false 2 = true 3 = nothing
    private var appVersion = ""
    private var authHeader = ""
    private var lastHandleConnectPosition = -1
    private var currnetHandleConnectPosition = 0
    private lateinit var axaLockInterface: IAPIAxaLockCallback

    fun serviceInit(activity: Activity?, axaLockInterface: IAPIAxaLockCallback) {

        this.axaLockInterface = axaLockInterface
        mBtAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBtAdapter == null) {
        }
        mActivity = activity
        val bindIntent = Intent(activity, AxaLockService::class.java)
        mActivity!!.bindService(bindIntent, mServiceConnection!!, Context.BIND_AUTO_CREATE)
        val filter = IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        mActivity!!.registerReceiver(mPairingRequestReceiver, filter)
        LocalBroadcastManager.getInstance(mActivity!!).registerReceiver(
            ERLStatusChangeReceiver!!, makeGattUpdateIntentFilter()
        )
    }

    fun handleConnectClick(
        connectBtn: Int, connectToClickMacId: String,
        passBookingObjectId: String, position: Int, webServiceURL: String
    ): Boolean {
        currnetHandleConnectPosition = position
        this.connectToClickMacId = connectToClickMacId
        this.passBookingObjectId = passBookingObjectId
        this.webServiceURL = webServiceURL
        scanLeDevice(true)
        mOTPasskeyNr = 0
        mWaitingWriteChar = false
        connectAxaToLock()
        return true
    }

    private fun handleBonding() {
        val bondState = mDevice!!.bondState
        if (bondState == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "device is BONDED")
            return
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            Log.d(TAG, "device is BONDING")
            return
        } else {
            Log.d(TAG, "device is NOT BONDED")
        }
    }

    private val mServiceConnection: ServiceConnection? = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, rawBinder: IBinder) {
            mService = (rawBinder as LocalBinder).service
            Log.d(TAG, "onServiceConnected mService= $mService")
            if (!mService!!.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth")
                //                mActivity.finish();
            }
        }

        override fun onServiceDisconnected(classname: ComponentName) {
            mService = null
        }
    }

    /* Handle events from bluetooth service. Must be added to intent filter */
    private val ERLStatusChangeReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val mLockState: Boolean
            val mIntent = intent

            //*********************//
            if (action == AxaLockService.ACTION_GATT_CONNECTED) {
                mActivity!!.runOnUiThread {
                    val currentDateTimeString = DateFormat.getTimeInstance().format(Date())
                    mState = ERL_PROFILE_CONNECTED
                    isUnLockCommand = "1"
                    axaLockInterface.onAxaConnected("1", "")
                }
            }

            //*********************//
            if (action == AxaLockService.ACTION_GATT_DISCONNECTED) {
                mActivity!!.runOnUiThread {
                    mState = ERL_PROFILE_DISCONNECTED
                    mService!!.close()
                    Log.e("testing_log_disconne_", mEKeyAscii + "")
                    mActivity!!.runOnUiThread({
                        if ((isDisconnectByClick == "2")) {
                            isDisconnectByClick = "1"
                            onUpdateAxaEKey(
                                2, connectToClickMacId,
                                passBookingObjectId, currnetHandleConnectPosition,
                                0, webServiceURL, appVersion, authHeader
                            )
                        } else if ((isDisconnectByClick == "1")) {
                            axaLockInterface.onAxaDisconnected("2", "")
                        } else if ((isDisconnectByClick == "3")) {
                        }
                    })
                }
            }

            //*********************//
            if (action == AxaLockService.ACTION_GATT_SERVICES_DISCOVERED) {
                mService!!.enableTXNotification()
                axaLockInterface.onAxaDiscovered("3", "")
                mActivity!!.runOnUiThread {
                    val handler = Handler()
                    handler.postDelayed(Runnable {
                        axaLockInterface.onAxaStartLockUnlock("51", "")
                        onSendEkey()
                    }, 1000)
                }
            }

            //*********************//
            if (action == AxaLockService.ACTION_DATA_AVAILABLE) {
                val txValue = intent.getByteArrayExtra(AxaLockService.EXTRA_DATA)
                // 0-Lock, 1-Unlock
//                Prefs.putInt(PreferenceVariables.AXA_LOCK_STATE, txValue!![0].toInt())
//                Prefs.putInt(PreferenceVariables.AXA_LOCK_NUMBER, currnetHandleConnectPosition)
                axaLockInterface.onAxaLockUnLockSuccessfully("4", txValue!![0].toString())
            }

            //*********************//
            if (action == AxaLockService.DEVICE_DOES_NOT_SUPPORT_ERL) {
                Log.e("testing_log_ns", " : DEVICE_DOES_NOT_SUPPORT_ERL ")
                mActivity!!.runOnUiThread {
                    axaLockInterface.onAxaERLNotFound("5", "")
                }
                try {
                    if (mService != null) {
                        isDisconnectByClick = "2"
                        mService!!.disconnect()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (AxaLockService.ACTION_ON_WRITE_CHAR == action) {
                Log.d("TAG", "Received broadcast ON_WRITE_CHAR")
                dequeCommand() // kickoff next send  if any buffered
                mWaitingWriteChar = false
                if (mWriteQueue.size > 0) {
                    Log.d("TAG", " Write Que > 0 ")
                } else {
                    Log.d("TAG", " Write Que = 0 ")
                }
                Log.e("testing_log_write", " : ON_WRITE_CHAR $action")
            } else if (AxaLockService.ACTION_BOND_STATE_CHANGED == action) {
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0)
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d("TAG", "Bond state changed: BONDED")
                } else if (bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d("TAG", "Bond state changed: BONDING")
                } else if (bondState == BluetoothDevice.BOND_NONE) {
                    Log.d("TAG", "Bond state changed: NOT BONDED")
                }
            }
        }
    }
    private val mPairingRequestReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_PAIRING_REQUEST) {
                try {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val pin = intent.getIntExtra(
                        "android.bluetooth.device.extra.PAIRING_KEY",
                        mPassKeyInt
                    )
                    //the pin in case you need to accept for an specific pin
                    //                  Log.d(TAG, "Start Auto Pairing. PIN = " + intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY",mPassKeyInt));
                    val pinBytes: ByteArray
                    pinBytes = ("" + pin).toByteArray(charset("UTF-8"))
                    device!!.setPin(pinBytes)
                    abortBroadcast()
                    //setPairing confirmation if neeeded
                    //                   device.setPairingConfirmation(true);
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurs when trying to auto pair")
                    e.printStackTrace()
                }
            }
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                try {
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurs when trying to auto pair")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * wfr queued writing for sending eKey, which is 110 bytes, 20 at a time (5-20 bytes, 1-10 byte)
     *
     * @param data
     */
    private fun queueData(data: ByteArray) {
        mWriteQueue.add(data)
        String.format("%02x", data[0])
        Log.e(
            TAG,
            "testing_log_que: " + data.size + " bytes: " + String.format(
                "%02x",
                data[0]
            ) + "," + String.format("%02x", data[1]) + "," + String.format(
                "%02x",
                data[2]
            ) + "," + String.format("%02x", data[3]) + "..."
        )
        if (!mWaitingWriteChar) dequeCommand()
    }

    private fun dequeCommand() {
        if (mWriteQueue.size > 0) {
            try {
                val item = mWriteQueue.removeAt(0)
                if (item == null) {
                    Log.d(TAG, "error item was null, not writing characteristic")
                    return
                }
                mWaitingWriteChar = true // indicate waiting for write done callback
                mService!!.writeRXCharacteristicCommand(item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun scanLeDevice(enable: Boolean) {
        try {
            val handler = Handler()
            if (mBtAdapter == null) {
                mBtAdapter = BluetoothAdapter.getDefaultAdapter()
            }
            val bluetoothLeScanner = mBtAdapter!!.bluetoothLeScanner
            try {
                if (enable) {
                    bluetoothLeScanner.startScan(mLeScanCallback)
                } else {
                    bluetoothLeScanner.stopScan(mLeScanCallback)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mLeScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
        }
    }

    override fun onSuccess(responce: String, TAG: String) {
        if (TAG == TAG_AXA_UPDATE_EKEY) {
            try {
                parseUpdateAxaEkey(responce)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onFailure(response: String, tag: String) {

    }

    fun connectAxaToLock() {
        mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(connectToClickMacId)
        handleBonding()
        Log.d("TAG", "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService)
        if (mService != null) {
            isDisconnectByClick = "1"
            axaLockInterface.onAxaConnecting("24" ,"")
            try {
                mService!!.connect(connectToClickMacId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Throws(Exception::class)
    private fun parseUpdateAxaEkey(responce: String) {
        axaLockInterface.onAxaEkeyUpdatedSuccessfully("23","")
        val gson = Gson()
        val responseModelLockInst = gson.fromJson(responce, ModelAxaEKeyResponse::class.java)
        mOTPasskeyNr = 0
        mEKeyAscii = responseModelLockInst.aSSETSDETAIL!!.axaEkey
        Prefs.putString("mEKeyAscii", mEKeyAscii)
        Prefs.putString("mOTPKeyparts", responseModelLockInst.aSSETSDETAIL!!.axaPasskey)
        //        mOTPKeyparts = responseModelLockInst.getASSETSDETAIL().getAxaPasskey().split("-");
        lastHandleConnectPosition = currnetHandleConnectPosition
        handleConnectClick(
            2, connectToClickMacId,
            passBookingObjectId, 20, webServiceURL
        )
    }

    fun onCheckLockIsLockedOrOpen() {
        if (mService != null) {
            mService!!.readData()
        }
    }

    fun lockAndUnlockAxaLock() {
        mOTPKeyparts = Prefs.getString("mOTPKeyparts", "").split("-").toTypedArray()
        mActivity!!.runOnUiThread {
            try {
                var bleData: ByteArray = byteArrayOf(0)
                if (mOTPasskeyNr < mOTPKeyparts.size) {
                    bleData = hexStringToByteArray(mOTPKeyparts[mOTPasskeyNr++])
                }
                mService!!.writeRXCharacteristicCommand(bleData)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onUpdateAxaEKey(
        connectBtn: Int, connectToClickMacId: String,
        passBookingObjectId: String, position: Int,
        axaLockUnLockCounter: Int,
        webServiceURL: String,
        appVersion: String,
        authHeader: String
    ) {
        this.passBookingObjectId = passBookingObjectId
        currnetHandleConnectPosition = position
        this.connectToClickMacId = connectToClickMacId
        this.webServiceURL = webServiceURL
        this.appVersion = appVersion
        this.authHeader = authHeader
        if (mService != null) {
            if (mService!!.isConnected) {
                if (axaLockUnLockCounter == 12) {
                    isDisconnectByClick = "2"
                    mService!!.disconnect()
                    //                    Constants.showFailureCustomToast(mActivity, "Please press again...");
                } else {
                    if (position == lastHandleConnectPosition) {
                        lockAndUnlockAxaLock()
                    } else {
                        isDisconnectByClick = "2"
                        mService!!.disconnect()
                        //                        Constants.showFailureCustomToast(mActivity, "Please press again...");
                    }
                }
            } else {
                this.passBookingObjectId = passBookingObjectId
                currnetHandleConnectPosition = position
                this.connectToClickMacId = connectToClickMacId
                axaLockInterface.onAxaStartEkeyUpdate("22" ,"")
                Log.e("testing_log_update_key_", "start updating key")
                val params = HashMap<String, Any>()
                params["object_id"] = passBookingObjectId
                params["passkey_type"] = "otp"
                ApiRequest.callPOSTAPI(
                    mActivity, webServiceURL,
                    params, TAG_AXA_UPDATE_EKEY, this, appVersion, authHeader, ""
                )
            }
        }
    }

    private fun sendEkey() {
        val parts = Prefs.getString("mEKeyAscii", "").split("-").toTypedArray()
        for (p in parts.indices) {
            // process each parts[p]
            mEkeyBinary = hexStringToByteArray(parts[p])
            queueData(mEkeyBinary)
            //            Log.e("testing_log_send_key_pr", "send key" + mEkeyBinary);
        }
    }

    fun removeBroadcast() {
        try {
            if (ERLStatusChangeReceiver != null) {
                LocalBroadcastManager.getInstance(mActivity!!)
                    .unregisterReceiver(ERLStatusChangeReceiver)
            }
            if (mServiceConnection != null) {
                mActivity!!.unbindService(mServiceConnection)
            }
            if (mService != null) {
                mService!!.stopSelf()
                mService = null
            }
        } catch (ignore: Exception) {
            Log.e(TAG, ignore.toString())
        }
    }

    fun onSendEkey() {
        mActivity!!.runOnUiThread {
            Log.e("testing_log_send_key", "send key")
            sendEkey()
        }
    }

    fun onDisconnectAxa() {
        try {
            if (mService != null) {
                isDisconnectByClick = "3"
                mService!!.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 2
        private const val REQUEST_ENABLE_LOCATION = 4
        private const val REQUEST_CODE_ASK_PERMISSIONS = 123
        const val TAG = "LOCK_EVENT_"
        private const val CONNECT_NONE = 1
        private const val CONNECT_LOCK = 2
        private const val ERL_PROFILE_CONNECTED = 20
        private const val ERL_PROFILE_DISCONNECTED = 21
        private fun makeGattUpdateIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(AxaLockService.ACTION_GATT_CONNECTED)
            intentFilter.addAction(AxaLockService.ACTION_GATT_DISCONNECTED)
            intentFilter.addAction(AxaLockService.ACTION_GATT_SERVICES_DISCOVERED)
            intentFilter.addAction(AxaLockService.ACTION_DATA_AVAILABLE)
            intentFilter.addAction(AxaLockService.DEVICE_DOES_NOT_SUPPORT_ERL)

            // allow these events to be sent to BroadcastReceiver
            intentFilter.addAction(AxaLockService.ACTION_ON_WRITE_CHAR) // this indicates a write has "completed", such that you can write more without error.
            intentFilter.addAction(AxaLockService.ACTION_BOND_STATE_CHANGED) // be notified of bond state change. Shouldn't need this.
            return intentFilter
        }

        fun hexStringToByteArray(s: String): ByteArray {
            val len = s.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                        + Character.digit(s[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }
    }
}