package com.music.bitchord.playback

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

enum class AudioDeviceType {
    SPEAKER,
    HEADPHONES,
    BLUETOOTH,
    USB,
}

data class ConnectedAudioDevice(
    val name: String,
    val type: AudioDeviceType,
)

object AudioDeviceHelper {

    fun getActiveAudioDevice(context: Context): ConnectedAudioDevice {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ConnectedAudioDevice("Phone Speaker", AudioDeviceType.SPEAKER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // Check for Bluetooth A2DP / Headset / BLE
            val bt = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            }
            if (bt != null) {
                val rawName = bt.productName?.toString()?.trim().orEmpty()
                val name = when {
                    rawName.isNotBlank() && !rawName.equals("Bluetooth", ignoreCase = true) -> rawName
                    else -> "Bluetooth Audio"
                }
                return ConnectedAudioDevice(name = name, type = AudioDeviceType.BLUETOOTH)
            }

            // Check for Wired Headphones / Headset
            val wired = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            if (wired != null) {
                val name = wired.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Headphones"
                return ConnectedAudioDevice(name = name, type = AudioDeviceType.HEADPHONES)
            }

            // Check for USB Headset / DAC
            val usb = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (usb != null) {
                val name = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB Audio"
                return ConnectedAudioDevice(name = name, type = AudioDeviceType.USB)
            }
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                return ConnectedAudioDevice("Bluetooth Audio", AudioDeviceType.BLUETOOTH)
            }
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn) {
                return ConnectedAudioDevice("Headphones", AudioDeviceType.HEADPHONES)
            }
        }

        return ConnectedAudioDevice("Phone Speaker", AudioDeviceType.SPEAKER)
    }

    /**
     * Opens the system Bluetooth settings or Media Output switcher.
     */
    fun openAudioOutputSettings(context: Context) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val intent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }
}

@Composable
fun rememberActiveAudioDevice(): State<ConnectedAudioDevice> {
    val context = LocalContext.current
    val deviceState = remember { mutableStateOf(AudioDeviceHelper.getActiveAudioDevice(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                deviceState.value = AudioDeviceHelper.getActiveAudioDevice(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)

        // Refresh initially
        deviceState.value = AudioDeviceHelper.getActiveAudioDevice(context)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    return deviceState
}
