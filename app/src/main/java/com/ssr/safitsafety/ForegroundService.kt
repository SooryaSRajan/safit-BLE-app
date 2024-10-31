package com.ssr.safitsafety

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo

class ForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val CHANNEL_ID = "BLEBroadcastChannel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_VALUE_BROADCAST = "com.ssr.safitsafety.VALUE_BROADCAST"
        const val EXTRA_VALUES = "values"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            createNotification("Foreground service running to receive ECG values"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        startBroadcasting()
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    private fun startBroadcasting() {
        serviceScope.launch {
            while (true) {
                val randomValues = List(10) { kotlin.random.Random.nextFloat() * 100 }
                broadcastValues(
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

    private fun generateNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun broadcastValues(data: HearRate) {
        Intent(ACTION_VALUE_BROADCAST).apply {
            putExtra(EXTRA_VALUES, data) // Send the HearRate object instead of string
            sendBroadcast(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}