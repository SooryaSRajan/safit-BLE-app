package com.ssr.safitsafety

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer

class MainActivity : ComponentActivity() {

    private var heartRateRecord by mutableStateOf(
        HearRate(
            heartRate = 0,
            ecgValue = 0,
            hrv = 0f,
            hrmad10 = 0f,
            hrmad30 = 0f,
            hrmad60 = 0f
        )
    )

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
            startForegroundServiceAndRegisterReceiver()
        } else {
            Toast.makeText(this, "Permissions are required for Bluetooth scanning and connection", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ValueDisplay(heartRateRecord) { checkPermissions() }
                }
            }
        }
    }

    private fun checkPermissions() {
        when {
            permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED } -> {
                // All permissions granted, proceed with service start and receiver registration
                startForegroundServiceAndRegisterReceiver()
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

    private fun startForegroundServiceAndRegisterReceiver() {
        Intent(this, ForegroundService::class.java).also { intent ->
            startForegroundService(intent)
        }

        ForegroundService.hearRate.observe(this@MainActivity, Observer {
            heartRateRecord = it
        })
    }
}

@Composable
fun ValueDisplay(hearRate: HearRate, onLaunch: () -> Unit) {

    LaunchedEffect(Unit) {
        onLaunch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Random Values",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Heart rate: ${hearRate.heartRate}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = "ECG: ${hearRate.ecgValue}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
