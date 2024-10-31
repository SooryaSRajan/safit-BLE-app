package com.ssr.safitsafety

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ssr.safitsafety.navigation.Screen
import com.ssr.safitsafety.navigation.pages.BluetoothListScreen
import com.ssr.safitsafety.navigation.pages.DataScreen
import com.ssr.safitsafety.service.ForegroundService

class MainActivity : ComponentActivity() {

    private lateinit var sharedPref: SharedPreferences;
    private var savedMac = mutableStateOf("")
    private var arePermissionsAllowed = mutableStateOf(false)
    companion object {
        const val PREF_KEY = "SAVED_MAC"
    }

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
            Toast.makeText(this, "Permissions are required for Bluetooth scanning and connection", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        savedMac.value = sharedPref.getString(PREF_KEY, "").toString()

        setContent {
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                checkPermissions()
                if (savedMac.value.isNullOrEmpty()) {
                    navController.navigate(route = Screen.Data.route)
                }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (arePermissionsAllowed.value) {
                        NavHost(navController = navController, startDestination = Screen.Scan.route) {
                            composable(route = Screen.Scan.route) {
                                BluetoothListScreen(navController = navController, sharedPref = sharedPref, onFabClick = {}, savedMac = savedMac)
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
                        Text(
                            text = "Please allow permissions",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(onClick = {checkPermissions() }) {
                            Text(
                                text = "Request permissions",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        when {
            permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED } -> {
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

    private fun startForegroundServiceAndRegisterReceiver() {
        Intent(this, ForegroundService::class.java).also { intent ->
            startForegroundService(intent)
        }
    }
}
