package com.ssr.safitsafety.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat

object MessagingService {
    fun sendSMS(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
                val sanitizedNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
                val messageParts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(sanitizedNumber, null, messageParts, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}