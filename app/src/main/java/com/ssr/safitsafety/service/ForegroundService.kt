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
import java.util.UUID


private const val TAG = "BluetoothLeService"

@Suppress("DEPRECATION")
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
        var chars: MutableList<BluetoothGattCharacteristic> = mutableListOf()

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

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Discovered services")

                val heartService =
                    gatt?.getService(UUID.fromString(HEART_RATE_PROFILE))

                // Subscribe to notifications for each characteristic
                heartService?.characteristics?.forEach { characteristic ->
                    if (characteristic.uuid in listOf(
                            UUID.fromString(HEART_RATE_UUID),
                            UUID.fromString(HRV_UUID),
                            UUID.fromString(HRMAD10_UUID),
                            UUID.fromString(HRMAD30_UUID),
                            UUID.fromString(HRMAD60_UUID),
                            UUID.fromString(ECG_UUID)
                        )
                    ) {
                        if (ActivityCompat.checkSelfPermission(
                                this@ForegroundService,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        gatt.setCharacteristicNotification(characteristic, true)

                        val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } else {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        }
                    }
                }

            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            when (characteristic?.uuid) {
                UUID.fromString(HEART_RATE_UUID) -> {
                    val heartRate =
                        characteristic?.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "Heart Rate: $heartRate bpm")
                }

                UUID.fromString(HRV_UUID) -> {
                    val hrv =
                        characteristic?.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRV: $hrv")
                }

                UUID.fromString(HRMAD10_UUID) -> {
                    val hrmad10 =
                        characteristic?.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRMAD10: $hrmad10")
                }

                UUID.fromString(HRMAD30_UUID) -> {
                    val hrmad30 =
                        characteristic?.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRMAD30: $hrmad30")
                }

                UUID.fromString(HRMAD60_UUID) -> {
                    val hrmad60 =
                        characteristic?.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRMAD60: $hrmad60")
                }

                UUID.fromString(ECG_UUID) -> {
                    val ecgValue =
                        characteristic?.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "ECG Value: $ecgValue")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            when (characteristic.uuid) {
                UUID.fromString(HEART_RATE_UUID) -> {
                    val heartRate =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "Heart Rate: $heartRate bpm")
                }

                UUID.fromString(HRV_UUID) -> {
                    val hrv =
                        characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRV: $hrv")
                }

                UUID.fromString(HRMAD10_UUID) -> {
                    val hrmad10 =
                        characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRMAD10: $hrmad10")
                }

                UUID.fromString(HRMAD30_UUID) -> {
                    val hrmad30 =
                        characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRMAD30: $hrmad30")
                }

                UUID.fromString(HRMAD60_UUID) -> {
                    val hrmad60 =
                        characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "HRMAD60: $hrmad60")
                }

                UUID.fromString(ECG_UUID) -> {
                    val ecgValue =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 0)
                    Log.i(TAG, "ECG Value: $ecgValue")
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
        super.onDestroy()
        serviceScope.cancel()
    }
}