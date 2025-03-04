package com.ssr.safitsafety.navigation.pages

import android.annotation.SuppressLint
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Gray
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ssr.safitsafety.MainActivity.Companion.popUpToTop
import com.ssr.safitsafety.data.BluetoothScan
import com.ssr.safitsafety.data.MacDataStoreManager
import com.ssr.safitsafety.navigation.Screen
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@SuppressLint("MissingPermission")
@Composable
fun BluetoothDeviceList(
    savedMac: MutableState<String>,
    bluetoothDeviceList: List<BluetoothScan>,
    navController: NavHostController
) {
    val context = LocalContext.current

    if (savedMac.value != "") {
        Column(
            modifier = Modifier
                .padding(30.dp)
                .fillMaxWidth()
                .wrapContentSize(Alignment.Center)
                .clickable(onClick = { })
                .clip(shape = RoundedCornerShape(16.dp)),
        ) {
            Box(
                modifier = Modifier
                    .border(width = 4.dp, color = Gray, shape = RoundedCornerShape(16.dp))
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "MAC Address: ${savedMac.value}",
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    style = typography.bodySmall,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Text(
                    text = "Bluetooth Devices",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp
                )
                if (bluetoothDeviceList.isEmpty()) {
                    Text(
                        text = "No devices found, try scanning again",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PaddingValues(top = 20.dp))
                    )
                }
            }
            items(items = bluetoothDeviceList.toSet().toList(), key = { device ->
                device.macAddress
            }) { record ->
                ElevatedCard(
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    ),
                    modifier = Modifier
                        .padding(paddingValues = PaddingValues(vertical = 8.dp))
                ) {
                    ListItem(
                        modifier = Modifier.clickable {
                            GlobalScope.launch {
                                MacDataStoreManager.saveMacAddress(context, record.macAddress)
                                savedMac.value = record.macAddress
                            }

                            navController.navigate(Screen.Data.route) { popUpToTop(navController) }
                        },
                        headlineText = { Text(record.deviceName) },
                        supportingText = { Text(record.macAddress) },
                        overlineText = { Text(record.uuid) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothListScreen(
    savedMac: MutableState<String>,
    navController: NavHostController,
    loadDevices: () -> Unit,
    bluetoothDevices: SnapshotStateList<BluetoothScan>,
) {

    LaunchedEffect(Unit) {
        loadDevices()
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
                }
            )
        },
        floatingActionButton = {
            if (savedMac.value == "") {
                FloatingActionButton(
                    onClick = loadDevices,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Filled.Refresh, "Refresh.")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BluetoothDeviceList(
                savedMac = savedMac,
                bluetoothDeviceList = bluetoothDevices,
                navController = navController
            )
        }
    }
}