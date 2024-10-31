package com.ssr.safitsafety

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import java.io.Serializable

class MainActivity : ComponentActivity() {
    private val REQUEST_LOCATION_PERMISSION = 696969696

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

    fun <T : Serializable?> getSerializable(activity: Activity, name: String, clazz: Class<T>): T {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            activity.intent.getSerializableExtra(name, clazz)!!
        else
            activity.intent.getSerializableExtra(name) as T
    }

    private val valueReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ForegroundService.ACTION_VALUE_BROADCAST) {
                Toast.makeText(this@MainActivity, intent.getStringExtra(ForegroundService.EXTRA_VALUES), Toast.LENGTH_LONG).show()
            }
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
                    ValueDisplay(heartRateRecord) { checkLocationPermission() }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(valueReceiver)
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed to find wearable device")
                    .setMessage("This app needs the Location permission to scan for Bluetooth devices.")
                    .setPositiveButton("OK") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            REQUEST_LOCATION_PERMISSION
                        )
                    }
                    .create()
                    .show()
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSION
                )
            }
        } else {
            // Start the service
            Intent(this, ForegroundService::class.java).also { intent ->
                startForegroundService(intent)
            }

            // Register broadcast receiver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    valueReceiver,
                    IntentFilter(ForegroundService.ACTION_VALUE_BROADCAST),
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(
                    valueReceiver,
                    IntentFilter(ForegroundService.ACTION_VALUE_BROADCAST),
                )
            }
        }
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
