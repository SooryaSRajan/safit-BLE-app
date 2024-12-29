package com.ssr.safitsafety.data

import java.io.Serializable


data class HeartRate(
    val heartRate: Int,
    val ecgValue: Int,
    val hrv: Float,
    val hrmad10: Float,
    val hrmad30: Float,
    val hrmad60: Float,
    val leadsOff: Boolean,
    val panic: Boolean
) : Serializable
