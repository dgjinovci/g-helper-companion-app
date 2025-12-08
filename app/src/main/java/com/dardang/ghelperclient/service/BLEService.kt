package com.dardang.ghelperclient.service

import android.Manifest
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
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dardang.ghelperclient.BuildConfig
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue


private const val TAG = "BleScanner"

class BLEService(context: Context) : IService(context) {

    /**
     * These UUIDs must match with server side (G-Helper windows app)
     */

    private val SERVICE_UUID = UUID.fromString("5A59D14B-D6B1-4CDE-B336-4EBC87960C4F")
    private val CHARACTERISTIC_INFO_UID = UUID.fromString("68D4DC98-E0EA-48B1-838C-4BD6E7A3067A")
    private val CHARACTERISTIC_MODES_UID = UUID.fromString("8F9D3A3A-486D-4B5F-A7A7-1304942B75FB")
    private val CHARACTERISTIC_SENSOR_UID = UUID.fromString("ADFB2D54-EEC2-4B9B-A77B-BA53ECBF0A87")
    private val CHARACTERISTIC_CMD_UID = UUID.fromString("84F4E7DA-4695-4DAA-8485-E4A5CCC91ABB")

    private val coroutineScope = MainScope() + CoroutineName("BLEService")

    var device: BluetoothDevice? = null
        private set

    private var gatt: BluetoothGatt? = null

    //private var sensorCharacteristic: BluetoothGattCharacteristic? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var infoCharacteristic: BluetoothGattCharacteristic? = null
    private var modeCharacteristic: BluetoothGattCharacteristic? = null
    private var sensorCharacteristic: BluetoothGattCharacteristic? = null


    private val adapter: BluetoothAdapter

    var isScanning: Boolean = false
        private set

    private val queue = ConcurrentLinkedQueue<Runnable>()
    private var isQueueProcessing = false


    init {
        /*requestPermissionLauncher =
            (context as ComponentActivity).registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    // there's no args for ble service
                    connect()
                } else {
                    connectionState = STATE_ERROR
                }
            }*/
        /*coroutineScope.launch {
            (context as ComponentActivity).lifecycle.repeatOnLifecycle(
                state = Lifecycle.State.CREATED
            ) {

            }
        }*/

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private suspend fun startScan(duration: Long = 20000) {
        log("startScan")
        isScanning = true
        connectionState = STATE_SCANNING
        adapter.bluetoothLeScanner.startScan(
            listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(SERVICE_UUID))
                    .build()
            ),
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback
        )
        delay(duration)
        stopScan()
        if (device == null) {
            connectionState = STATE_NONE
        }
    }


    @RequiresPermission(anyOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    override fun connect() {

        if (device == null) {
            coroutineScope.launch(Dispatchers.IO) {
                startScan()
            }
        } else {
            connectionState = STATE_CONNECTING
            gatt = device!!.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        stopScan()
        gatt?.disconnect()
        connectionState = STATE_NONE
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        log("stopScan")
        if (isScanning) {
            adapter.bluetoothLeScanner.stopScan(scanCallback)
        }
        isScanning = false
    }


    @SuppressLint("MissingPermission")
    override fun read(@Type type: Int) {
        val gatt = gatt ?: return

        when (type) {
            TYPE_INFO -> {
                queue.add {
                    gatt.readCharacteristic(infoCharacteristic)
                }
            }

            TYPE_MODES -> {
                queue.add {
                    gatt.readCharacteristic(modeCharacteristic)
                }
            }

            TYPE_SENSOR -> {
                queue.add {
                    gatt.readCharacteristic(sensorCharacteristic)
                }
            }

            TYPE_CMD -> {
            }
        }
        processQueue()
    }

    private fun processQueue() {
        if (!isQueueProcessing && queue.isNotEmpty()) {
            isQueueProcessing = true
            queue.poll()?.run()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun sendCmd(data: ByteArray) {

        val gatt = gatt ?: return
        val characteristic = cmdCharacteristic ?: return

        val type =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        queue.add {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    data,
                    type
                )
            } else {
                characteristic.value = data
                characteristic.writeType = type
                gatt.writeCharacteristic(characteristic)
            }
        }
    }


    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            log("onScanFailed(${errorCode})")
            connectionState = STATE_ERROR
        }

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            if (result == null) return

            val found = result.scanRecord?.serviceUuids?.find { it.uuid == SERVICE_UUID } != null

            if (found) {
                log("onScanResult: ${result.device?.name}")
                device = result.device
                //deviceName = device?.name ?: "<Unknown>"
                connectionState = STATE_SCAN_COMPLETED
                stopScan()
                connect()
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)
            log("onConnectionStateChange $status $newState")

            if (status == BluetoothGatt.GATT_SUCCESS
                && newState == BluetoothProfile.STATE_CONNECTED
            ) {
                connectionState = STATE_CONNECTING
                gatt.discoverServices()

            } else {
                this@BLEService.gatt = null
                this@BLEService.device = null
                this@BLEService.cmdCharacteristic = null

                connectionState = if (status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_DISCONNECTED
                ) {
                    STATE_NONE
                } else {
                    STATE_ERROR
                }
            }
        }

        override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            log("onPhyRead $txPhy $txPhy $status")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            log("onServicesDiscovered status:$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)

                Log.d(
                    TAG,
                    "onServicesDiscovered characteristics: ${service?.characteristics?.map { it.uuid }}}"
                )

                infoCharacteristic = service?.getCharacteristic(CHARACTERISTIC_INFO_UID)
                modeCharacteristic = service?.getCharacteristic(CHARACTERISTIC_MODES_UID)
                cmdCharacteristic = service?.getCharacteristic(CHARACTERISTIC_CMD_UID)
                sensorCharacteristic = service?.getCharacteristic(CHARACTERISTIC_SENSOR_UID)

                queue.clear()
                isQueueProcessing = false

                if (
                    infoCharacteristic != null
                    && modeCharacteristic != null
                    && cmdCharacteristic != null
                    && sensorCharacteristic != null
                ) {
                    // characteristics read/write can hang right after onServicesDiscovered
                    // add 1s delay before notifying caller for successful connection
                    coroutineScope.launch {
                        delay(1000)
                        connectionState = STATE_CONNECTED
                    }

                } else {
                    this@BLEService.gatt = null
                    this@BLEService.device = null
                    connectionState = STATE_ERROR
                }


            } else {
                this@BLEService.gatt = null
                this@BLEService.device = null
                connectionState = STATE_ERROR
            }
        }

        fun getTypeByUid(uuid: UUID): Int {
            return when (uuid) {
                CHARACTERISTIC_INFO_UID -> TYPE_INFO
                CHARACTERISTIC_MODES_UID -> TYPE_MODES
                CHARACTERISTIC_SENSOR_UID -> TYPE_SENSOR
                CHARACTERISTIC_CMD_UID -> TYPE_CMD
                else -> -1
            }
        }

        // Older devices  Android 12 and lower
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val type = getTypeByUid(characteristic.uuid)
                if (type > -1) {
                    onReadData(type, characteristic.value)
                }
            }

            isQueueProcessing = false
            processQueue()

            log("old read: $status")

        }

        // Newer devices Android 13+
        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                val type = getTypeByUid(characteristic.uuid)
                if (type > -1) {
                    onReadData(type, value)
                }
            }

            isQueueProcessing = false
            processQueue()

            log("read: $status")

        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            isQueueProcessing = false
            processQueue()

            log("write: $status")
        }

    }

    private fun log(t: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, t)
        }
    }


}
