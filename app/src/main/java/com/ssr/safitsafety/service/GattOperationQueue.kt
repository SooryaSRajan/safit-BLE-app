package com.ssr.safitsafety.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import android.util.Log
import java.util.LinkedList
import java.util.UUID

class GattOperationQueue {
    private val queue = LinkedList<GattOperation>()
    private var isProcessing = false

    data class GattOperation(
        val characteristic: BluetoothGattCharacteristic,
        val gatt: BluetoothGatt,
        val name: String
    )

    @Synchronized
    fun enqueue(operation: GattOperation) {
        queue.add(operation)
        if (!isProcessing) {
            processNextOperation()
        }
    }

    @Synchronized
    fun onOperationComplete() {
        isProcessing = false
        queue.poll() // Remove the completed operation
        if (queue.isNotEmpty()) {
            processNextOperation()
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun processNextOperation() {
        if (isProcessing) return

        queue.peek()?.let { operation ->
            isProcessing = true

            val descriptor = operation.characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )

            if (descriptor != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    operation.gatt.writeDescriptor(descriptor)
                } else {
                    operation.gatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                }
                Log.d("GattQueue", "${operation.name} descriptor write initiated")
            } else {
                Log.e("GattQueue", "${operation.name} descriptor not found!")
                onOperationComplete() // Move to next operation if this one failed
            }
        }
    }
}