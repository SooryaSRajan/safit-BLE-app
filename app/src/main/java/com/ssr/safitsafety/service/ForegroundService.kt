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
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import com.ssr.safitsafety.MainActivity
import com.ssr.safitsafety.MainActivity.Companion.AGE_UUID
import com.ssr.safitsafety.MainActivity.Companion.ECG_UUID
import com.ssr.safitsafety.MainActivity.Companion.HEART_RATE_PROFILE
import com.ssr.safitsafety.MainActivity.Companion.HEART_RATE_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRMAD10_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRMAD30_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRMAD60_UUID
import com.ssr.safitsafety.MainActivity.Companion.HRV_UUID
import com.ssr.safitsafety.MainActivity.Companion.LEADS_UUID
import com.ssr.safitsafety.MainActivity.Companion.WEIGHT_UUID
import com.ssr.safitsafety.data.MacDataStoreManager
import com.ssr.safitsafety.data.HeartRate
import com.ssr.safitsafety.data.UserDataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.system.exitProcess


private const val TAG = "BluetoothLeService"

class ForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val CHANNEL_ID = "BLEBroadcastChannel"
    private val NOTIFICATION_ID = 1
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var lastWriteTimestamp: Long = 0
    private val writeIntervalMillis = 500L
    private var userDataCollectorJob: Job? = null

    private var weightCharacteristic: BluetoothGattCharacteristic? = null
    private var ageCharacteristic: BluetoothGattCharacteristic? = null
    private val gattQueue = GattOperationQueue()

    companion object {
        private val database =
            Firebase.database("https://safit33-6b519-default-rtdb.asia-southeast1.firebasedatabase.app")
        val heartRate = MutableLiveData<HeartRate>()
        private var databaseReference: DatabaseReference? = null

        fun toggleState(toggle: Boolean) {
            if (toggle && databaseReference == null) {
                val timestamp = System.currentTimeMillis()
                val referenceName = "HEARTRATE-$timestamp"
                databaseReference = database.getReference(referenceName)
                Log.d(TAG, "State set: $referenceName")
            } else if (!toggle && databaseReference != null) {
                databaseReference = null
                Log.d(TAG, "State reset to null")
            }
        }

        fun getState(): Boolean {
            val state = databaseReference != null
            Log.d(TAG, "State checked: $state")
            return state
        }
    }

    private fun observeUserData() {
        userDataCollectorJob = CoroutineScope(Dispatchers.IO).launch {
            UserDataStoreManager.getUserData(this@ForegroundService)
                .collect { userData ->
                    userData?.let {
                        Log.i(TAG, "User data value change observed in Foreground Service: $userData")

                        if (ActivityCompat.checkSelfPermission(
                                this@ForegroundService,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            // Write weight
                            weightCharacteristic?.let { characteristic ->
                                val weightBytes = ByteBuffer.allocate(4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .putFloat(userData.weight)
                                    .array()

                                bluetoothGatt?.let { gatt ->
                                    gattQueue.enqueue(
                                        GattOperationQueue.GattOperation(
                                            characteristic = characteristic,
                                            gatt = gatt,
                                            name = "Weight Write",
                                            value = weightBytes
                                        )
                                    )
                                    Log.d(TAG, "Queued weight write: ${userData.weight}")
                                }
                            }

                            // Write age
                            ageCharacteristic?.let { characteristic ->
                                val ageBytes = ByteBuffer.allocate(4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .putInt(userData.age)
                                    .array()

                                bluetoothGatt?.let { gatt ->
                                    gattQueue.enqueue(
                                        GattOperationQueue.GattOperation(
                                            characteristic = characteristic,
                                            gatt = gatt,
                                            name = "Age Write",
                                            value = ageBytes
                                        )
                                    )
                                    Log.d(TAG, "Queued age write: ${userData.age}")
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun writeHeartRateToFirebase(heartRate: HeartRate) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastWriteTimestamp >= writeIntervalMillis && !heartRate.leadsOff) {
            lastWriteTimestamp = currentTime

            try {
                val userData = runBlocking {
                    UserDataStoreManager.getUserData(this@ForegroundService).first()
                }

                if (userData != null) {
                    val newRecord = mapOf(
                        "heartRate" to heartRate.heartRate,
                        "hrv" to heartRate.hrv,
                        "hrmad10" to heartRate.hrmad10,
                        "hrmad30" to heartRate.hrmad30,
                        "hrmad60" to heartRate.hrmad60,
                        "weight" to userData.weight,
                        "age" to userData.age,
                        "timestamp" to currentTime
                    )

                    databaseReference?.let { ref ->
                        ref.push().setValue(newRecord)
                            .addOnSuccessListener {
                                Log.d(TAG, "Successfully wrote data to Firebase: $newRecord")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to write data to Firebase", e)
                            }
                    } ?: Log.e(TAG, "DatabaseReference is null, cannot write data")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from DataStore", e)
            }
        }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF) {
                    createNotification("Bluetooth turned off, terminating service. Please restart bluetooth")
                    showToast("Bluetooth turned off, terminating service. Please restart bluetooth")
                    stopForegroundService()
                    exitProcess(0)
                }
            }
        }
    }


    private fun initializeBluetoothAdapter(): Boolean {
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(
                this,
                "Please turn on bluetooth before launching app",
                Toast.LENGTH_SHORT
            ).show()
            return false;
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
        } else {
            registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            observeUserData()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            MacDataStoreManager.getMacAddress(this@ForegroundService).collect { macAddress ->
                if (!connect(macAddress)) {
                    if (ActivityCompat.checkSelfPermission(
                            this@ForegroundService,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        bluetoothGatt?.disconnect()
                    }
                    createNotification("Failed to connect to device")
                    showToast("Failed to connect to emergency device")
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

    private fun setupUserDataCharacteristics(service: BluetoothGattService?) {
        weightCharacteristic = service?.getCharacteristic(UUID.fromString(WEIGHT_UUID))
        ageCharacteristic = service?.getCharacteristic(UUID.fromString(AGE_UUID))
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

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w("GattQueue", "Discovered services")

                val heartService = gatt?.getService(UUID.fromString(HEART_RATE_PROFILE))
                setupUserDataCharacteristics(heartService)
                Log.d("GattQueue", "Heart service found: ${heartService != null}")

                // List of all characteristic UUIDs we're looking for
                val targetUuids = listOf(
                    HEART_RATE_UUID to "Heart Rate",
                    HRV_UUID to "HRV",
                    HRMAD10_UUID to "HRMAD10",
                    HRMAD30_UUID to "HRMAD30",
                    HRMAD60_UUID to "HRMAD60",
                    ECG_UUID to "ECG",
                    LEADS_UUID to "LEADS",
                    AGE_UUID to "Age",          // Add Age characteristic
                    WEIGHT_UUID to "Weight"     // Add Weight characteristic
                )

                targetUuids.forEach { (uuid, name) ->
                    val characteristic = heartService?.getCharacteristic(UUID.fromString(uuid))
                    if (characteristic == null) {
                        Log.e("GattQueue", "$name characteristic not found!")
                    } else {
                        Log.d("GattQueue", "$name characteristic found")

                        if (ActivityCompat.checkSelfPermission(
                                this@ForegroundService,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            // Enable notifications for read characteristics
                            if (uuid !in listOf(AGE_UUID, WEIGHT_UUID)) {
                                val success = gatt.setCharacteristicNotification(characteristic, true)
                                if (success) {
                                    gattQueue.enqueue(
                                        GattOperationQueue.GattOperation(
                                            characteristic = characteristic,
                                            gatt = gatt,
                                            name = name
                                        )
                                    )
                                } else {
                                    Log.d("GattQueue", "Setting up notification for $name failed")
                                }
                            }

                            // For age and weight, configure them for write operations
                            if (uuid in listOf(AGE_UUID, WEIGHT_UUID)) {
                                Log.d("GattQueue", "Setting up $name for write operations")
                                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic != null) {
                    Log.d("GattQueue", "Characteristic ${characteristic.uuid} write successful")
                }
            } else {
                if (characteristic != null) {
                    Log.e("GattQueue", "Characteristic ${characteristic.uuid} write failed with status: $status")
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
                Log.d("GattQueue", "Descriptor write successful")
                gattQueue.onOperationComplete()
            } else {
                Log.e("GattQueue", "Descriptor write failed with status: $status")
                // You might want to handle the error case differently
                gattQueue.onOperationComplete() // Skip to next operation even if this one failed
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            val floatValue = ByteBuffer.wrap(value)
                .order(ByteOrder.LITTLE_ENDIAN)
                .float

            val currentHeartRate = heartRate.value ?: HeartRate(0, 0, 0f, 0f, 0f, 0f, false)

            when (characteristic.uuid.toString()) {
                HEART_RATE_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(heartRate = floatValue.toInt())
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                HRV_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(hrv = floatValue)
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                HRMAD10_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(hrmad10 = floatValue)
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                HRMAD30_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(hrmad30 = floatValue)
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                HRMAD60_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(hrmad60 = floatValue)
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                ECG_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(ecgValue = floatValue.toInt())
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                LEADS_UUID -> {
                    val updatedHeartRate = currentHeartRate.copy(leadsOff = (floatValue == 1.0F))
                    if (!currentHeartRate.leadsOff && floatValue == 1.0F) {
                        createNotification("Leads are off, please make sure they are properly seated")
                    }
                    heartRate.postValue(updatedHeartRate)
                    writeHeartRateToFirebase(updatedHeartRate)
                }

                WEIGHT_UUID -> {
                    Log.e("GattQueue", "Weight UUID changes and received from arduino $floatValue")
                }

                AGE_UUID -> {
                    Log.e("GattQueue", "Age UUID changes and received from arduino $floatValue")
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
            MacDataStoreManager.clearMacAddress(context)
        }
    }

    fun stopForegroundService() {
        clearSavedMacAddress(this@ForegroundService)
        createNotification("Terminating background service, reconnect to device to start service again")
        showToast("Terminating background service, reconnect to device to start service again")
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    override fun onDestroy() {
        createNotification("Terminating background service, reconnect to device to start service again")
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        serviceScope.cancel()
    }
}