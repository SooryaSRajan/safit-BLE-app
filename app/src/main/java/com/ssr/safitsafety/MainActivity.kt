package com.ssr.safitsafety

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssr.safitsafety.data.BluetoothScan
import com.ssr.safitsafety.navigation.Screen
import com.ssr.safitsafety.navigation.pages.BluetoothListScreen
import com.ssr.safitsafety.navigation.pages.DataScreen
import com.ssr.safitsafety.service.ForegroundService
import java.util.function.Consumer

class MainActivity : ComponentActivity() {

    private lateinit var sharedPref: SharedPreferences;
    private var savedMac = mutableStateOf("")
    private var arePermissionsAllowed = mutableStateOf(false)

    companion object {
        const val PREF_KEY = "SAVED_MAC"
    }

    private val bluetoothDevices = mutableStateListOf<BluetoothScan>()

    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private var leScanCallback: ScanCallback? = null

    // Permissions required for location and Bluetooth functionality
    private val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION // Required for all Bluetooth scanning below API 31
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted; start the service and register the receiver
            arePermissionsAllowed.value = true
        } else {
            Toast.makeText(
                this,
                "Permissions are required for Bluetooth scanning and connection",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        savedMac.value = sharedPref.getString(PREF_KEY, "").toString()


        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter?.let { adapter -> bluetoothLeScanner = adapter.bluetoothLeScanner }
        leScanCallback = object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val scanRecord = result.scanRecord
                val serviceUuids = scanRecord?.serviceUuids

                val advertisedUuid = serviceUuids?.firstOrNull()?.toString() ?: "Unknown UUID"

                //TODO:  filter specific profiles (services)
                bluetoothDevices.add(
                    BluetoothScan(
                        deviceName = result.device.name ?: "Unnamed Device",
                        macAddress = result.device.address,
                        uuid = advertisedUuid
                    )
                )
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach(Consumer { result: ScanResult ->
                    onScanResult(
                        ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
                        result
                    )
                })
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(this.javaClass.name, "Error Code while scanning: $errorCode")
            }
        }

        fun startScan() {
            bluetoothDevices.clear()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothLeScanner!!.startScan(leScanCallback)
            }
        }

        fun stopScan() {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothLeScanner!!.stopScan(leScanCallback)
            }
        }

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                checkPermissions()
            }

            if (arePermissionsAllowed.value) {
                if (savedMac.value.isNotEmpty()) {
                    Intent(this, ForegroundService::class.java).also { intent ->
                        startForegroundService(intent)
                    }
                    navController.navigate(route = Screen.Data.route)
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (arePermissionsAllowed.value) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Scan.route
                        ) {
                            composable(route = Screen.Scan.route) {
                                BluetoothListScreen(
                                    navController = navController,
                                    sharedPref = sharedPref,
                                    loadDevices = {
                                        stopScan()
                                        startScan()
                                    },
                                    savedMac = savedMac,
                                    bluetoothDevices = bluetoothDevices
                                )
                            }
                            composable(
                                route = Screen.Scan.route + "?text={text}",
                                arguments = listOf(
                                    navArgument("text") {
                                        type = NavType.StringType
                                        nullable = true
                                    }
                                )
                            ) {
                                DataScreen()
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Permissions Needed",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "To proceed, please allow permissions in settings",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        when {
            permissions.all {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) == PackageManager.PERMISSION_GRANTED
            } -> {
                // All permissions granted, proceed with service start and receiver registration
                arePermissionsAllowed.value = true
            }

            permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) } -> {
                // Show rationale dialog explaining why permissions are needed
                AlertDialog.Builder(this)
                    .setTitle("Permissions Needed")
                    .setMessage("This app requires location and Bluetooth permissions to scan and connect to devices.")
                    .setPositiveButton("OK") { _, _ ->
                        requestPermissionLauncher.launch(permissions)
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                    .show()
            }

            else -> {
                // Directly request all permissions
                requestPermissionLauncher.launch(permissions)
            }
        }
    }
}
