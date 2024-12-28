package com.ssr.safitsafety.navigation.pages

import android.annotation.SuppressLint
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ssr.safitsafety.MainActivity.Companion.popUpToTop
import com.ssr.safitsafety.data.HeartRate
import com.ssr.safitsafety.data.MacDataStoreManager
import com.ssr.safitsafety.data.UserDataStoreManager
import com.ssr.safitsafety.navigation.Screen
import com.ssr.safitsafety.service.ForegroundService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun DataScreen(navController: NavHostController) {
    val context = LocalContext.current
    var isCollectingData by remember { mutableStateOf(ForegroundService.getState()) }

    var heartRateRecord by remember {
        mutableStateOf(
            HeartRate(
                heartRate = 0,
                ecgValue = 0,
                hrv = 0f,
                hrmad10 = 0f,
                hrmad30 = 0f,
                hrmad60 = 0f,
                false
            )
        )
    }

    ForegroundService.heartRate.observe(LocalLifecycleOwner.current) { heartRate ->
        heartRateRecord = heartRate
    }

    LaunchedEffect(Unit) {
        UserDataStoreManager.getUserData(context).collect { userData ->
            if (userData == null || userData.weight == 0 || userData.age == 0) {
                navController.navigate(route = Screen.UserData.route) {
                    popUpToTop(navController)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Safit")
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Collect Data",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Switch(
                            checked = isCollectingData,
                            onCheckedChange = { isChecked ->
                                isCollectingData = isChecked
                                ForegroundService.toggleState(isChecked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                checkedIconColor = MaterialTheme.colorScheme.onPrimary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = {
                        navController.navigate(route = Screen.UserData.route) {
                            popUpToTop(navController)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Localized description"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    GlobalScope.launch {
                        MacDataStoreManager.clearMacAddress(context)
                    }
                },
                icon = { Icon(Icons.Filled.Close, "Disconnect.") },
                text = { Text(text = "Disconnect") },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Safit Readings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (heartRateRecord.leadsOff) {
                ErrorCard(message = "Leads disconnected! Please connect leads properly")
            } else {
                ReadingCard(
                    title = "Heart Rate",
                    value = "${heartRateRecord.heartRate}",
                    unit = "BPM"
                )

                ReadingCard(
                    title = "ECG",
                    value = "${heartRateRecord.ecgValue}",
                    unit = "mV"
                )

                ReadingCard(
                    title = "Heart Rate Variability",
                    value = "%.2f".format(heartRateRecord.hrv),
                    unit = "ms"
                )

                ReadingCard(
                    title = "HRMAD10",
                    value = "%.2f".format(heartRateRecord.hrmad10),
                    unit = ""
                )

                ReadingCard(
                    title = "HRMAD30",
                    value = "%.2f".format(heartRateRecord.hrmad30),
                    unit = ""
                )

                ReadingCard(
                    title = "HRMAD60",
                    value = "%.2f".format(heartRateRecord.hrmad60),
                    unit = ""
                )
            }
        }
    }
}

@Composable
private fun ReadingCard(
    title: String,
    value: String,
    unit: String
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = 2.dp,
                color = Color(0xFF8B1D1D),  // Darker red border
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE),  // Light red background
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF8B1D1D),  // Darker red text
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}