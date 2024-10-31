package com.ssr.safitsafety.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.ssr.safitsafety.MainActivity
import com.ssr.safitsafety.MainActivity.Companion.ECG_UUID
import com.ssr.safitsafety.MainActivity.Companion.HEART_RATE_PROFILE
import com.ssr.safitsafety.MainActivity.Companion.HEART_RATE_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRMAD10_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRMAD30_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRMAD60_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRV_UUID
import com.ssr.safitsafety.data.DataStoreManager
import com.ssr.safitsafety.data.HearRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID


private const val TAG = "BluetoothLeService"

class ForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val CHANNEL_ID = "BLEBroadcastChannel"
    private val NOTIFICATION_ID = 1
    var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    companion object {
        val hearRate = MutableLiveData<HearRate>()
    }

    private fun initializeBluetoothAdapter(): Boolean {
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            createNotification("Emergency detection service running, please do not kill app. "),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        if (!initializeBluetoothAdapter()) {
            stopForegroundService()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            DataStoreManager.getMacAddress(this@ForegroundService).collect { macAddress ->
                if (!connect(macAddress)) {
                    createNotification("Failed to connect to device")
                    stopForegroundService()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Value Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Channel for Value Service"
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safit Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    private fun startBle() {
        serviceScope.launch {
            while (true) {
                val randomValues = List(10) { kotlin.random.Random.nextFloat() * 100 }
                hearRate.postValue(
                    HearRate(
                        randomValues[1].toInt(),
                        randomValues[2].toInt(),
                        randomValues[3],
                        randomValues[4],
                        randomValues[5],
                        randomValues[6]
                    )
                )
                delay(1000) // Broadcast every second
            }
        }
    }

    private fun connect(address: String?): Boolean {
        if (address.isNullOrEmpty()) return false

        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                return true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Device not found with provided address.  Unable to connect.")
                return false
            }
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return false
        }
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTING) {
                generateNotification("Collecting to Safit safety device")
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                generateNotification("Successfully connected to device")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (ActivityCompat.checkSelfPermission(
                            this@ForegroundService,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    gatt?.discoverServices()
                } else {
                    stopForegroundService()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                generateNotification("Disconnected from device")
                stopForegroundService()
            }
        }

        private val gattQueue = GattOperationQueue()

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Discovered services")

                val heartService = gatt?.getService(UUID.fromString(HEART_RATE_PROFILE))
                Log.d(TAG, "Heart service found: ${heartService != null}")

                // List of all characteristic UUIDs we're looking for
                val targetUuids = listOf(
                    HEART_RATE_UUID to "Heart Rate",
                    HRV_UUID to "HRV",
                    HRMAD10_UUID to "HRMAD10",
                    HRMAD30_UUID to "HRMAD30",
                    HRMAD60_UUID to "HRMAD60",
                    ECG_UUID to "ECG"
                )

                // Queue each characteristic operation
                targetUuids.forEach { (uuid, name) ->
                    val characteristic = heartService?.getCharacteristic(UUID.fromString(uuid))
                    if (characteristic == null) {
                        Log.e(TAG, "$name characteristic not found!")
                    } else {
                        Log.d(TAG, "$name characteristic found, setting up notification...")
                        if (ActivityCompat.checkSelfPermission(
                                this@ForegroundService,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED) {
                            val success = gatt.setCharacteristicNotification(characteristic, true)
                            if (success) {
                                gattQueue.enqueue(
                                    GattOperationQueue.GattOperation(
                                        characteristic = characteristic,
                                        gatt = gatt,
                                        name = name
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write successful")
                gattQueue.onOperationComplete()
            } else {
                Log.e(TAG, "Descriptor write failed with status: $status")
                // You might want to handle the error case differently
                gattQueue.onOperationComplete() // Skip to next operation even if this one failed
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic != null) {
                Log.d(TAG, "Characteristic changed: ${characteristic.uuid}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            Log.d(TAG, "Old callback - Characteristic changed: ${characteristic.uuid}")

            // All values are sent as floats from Arduino
            val floatValue = ByteBuffer.wrap(value)
                .order(ByteOrder.LITTLE_ENDIAN)
                .float

            // Create a copy of the current heart rate value
            val currentHeartRate = hearRate.value ?: HearRate(0, 0, 0f, 0f, 0f, 0f)

            // Update only the relevant field based on the characteristic UUID
            when (characteristic.uuid.toString()) {
                HEART_RATE_UUID -> {
                    hearRate.postValue(currentHeartRate.copy(heartRate = floatValue.toInt()))
                    Log.i(TAG, "Heart Rate: $floatValue bpm")
                }
                HRV_UUID -> {
                    hearRate.postValue(currentHeartRate.copy(hrv = floatValue))
                    Log.i(TAG, "HRV: $floatValue")
                }
                HRMAD10_UUID -> {
                    hearRate.postValue(currentHeartRate.copy(hrmad10 = floatValue))
                    Log.i(TAG, "HRMAD10: $floatValue")
                }
                HRMAD30_UUID -> {
                    hearRate.postValue(currentHeartRate.copy(hrmad30 = floatValue))
                    Log.i(TAG, "HRMAD30: $floatValue")
                }
                HRMAD60_UUID -> {
                    hearRate.postValue(currentHeartRate.copy(hrmad60 = floatValue))
                    Log.i(TAG, "HRMAD60: $floatValue")
                }
                ECG_UUID -> {
                    hearRate.postValue(currentHeartRate.copy(ecgValue = floatValue.toInt()))
                    Log.i(TAG, "ECG Value: $floatValue")
                }
            }
        }
    }

    private fun generateNotification(message: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun clearSavedMacAddress(context: Context) {
        serviceScope.launch {
            DataStoreManager.clearMacAddress(context)
        }
    }

    fun stopForegroundService() {
        clearSavedMacAddress(this@ForegroundService)
        createNotification("Terminating background service, reconnect to device to start service again")
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        createNotification("Terminating background service, reconnect to device to start service again")
        super.onDestroy()
        serviceScope.cancel()
    }
}