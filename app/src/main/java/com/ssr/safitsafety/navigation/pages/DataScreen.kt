package com.ssr.safitsafety.navigation.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.ssr.safitsafety.data.HearRate
import com.ssr.safitsafety.service.ForegroundService

@Composable
fun DataScreen() {
    var heartRateRecord by remember {
        mutableStateOf(
            HearRate(
                heartRate = 0,
                ecgValue = 0,
                hrv = 0f,
                hrmad10 = 0f,
                hrmad30 = 0f,
                hrmad60 = 0f
            )
        )
    }

    ForegroundService.hearRate.observe(LocalLifecycleOwner.current) {
        heartRateRecord = it
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
            text = "Heart rate: ${heartRateRecord.heartRate}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = "ECG: ${heartRateRecord.ecgValue}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}