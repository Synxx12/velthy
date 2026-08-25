package com.velthy.client.playback

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import androidx.core.content.ContextCompat

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import com.velthy.client.ui.icons.MusiqueIcons

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

enum class AudioDeviceType {
    SPEAKER,
    HEADPHONES,
    BLUETOOTH_TWS,
    BLUETOOTH_HEADPHONES,
    BLUETOOTH_SPEAKER,
    USB_DAC,
}

data class ConnectedAudioDevice(
    val name: String,
    val type: AudioDeviceType,
) {
    val isExternal: Boolean get() = type != AudioDeviceType.SPEAKER

    val icon: ImageVector get() = when (type) {
        AudioDeviceType.BLUETOOTH_TWS -> MusiqueIcons.Earbuds
        AudioDeviceType.BLUETOOTH_HEADPHONES, AudioDeviceType.HEADPHONES -> MusiqueIcons.Headphones
        AudioDeviceType.BLUETOOTH_SPEAKER -> MusiqueIcons.Speaker
        AudioDeviceType.USB_DAC -> MusiqueIcons.UsbDac
        AudioDeviceType.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
    }

    val typeLabel: String get() = when (type) {
        AudioDeviceType.BLUETOOTH_TWS -> "Wireless Earbuds (TWS)"
        AudioDeviceType.BLUETOOTH_HEADPHONES -> "Bluetooth Headphones"
        AudioDeviceType.BLUETOOTH_SPEAKER -> "Bluetooth Speaker"
        AudioDeviceType.HEADPHONES -> "Wired Headphones"
        AudioDeviceType.USB_DAC -> "USB DAC / Hi-Res Audio"
        AudioDeviceType.SPEAKER -> "Phone Speaker"
    }
}

data class AudioOutputOption(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val isSelected: Boolean,
    val deviceInfo: AudioDeviceInfo? = null,
) {
    val icon: ImageVector get() = when (type) {
        AudioDeviceType.BLUETOOTH_TWS -> MusiqueIcons.Earbuds
        AudioDeviceType.BLUETOOTH_HEADPHONES, AudioDeviceType.HEADPHONES -> MusiqueIcons.Headphones
        AudioDeviceType.BLUETOOTH_SPEAKER -> MusiqueIcons.Speaker
        AudioDeviceType.USB_DAC -> MusiqueIcons.UsbDac
        AudioDeviceType.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
    }
}

object AudioDeviceHelper {

    val preferredDevice = MutableStateFlow<AudioDeviceInfo?>(null)
    val forceSpeaker = MutableStateFlow(false)

    fun getActiveAudioDevice(context: Context): ConnectedAudioDevice {
        return runCatching {
            if (forceSpeaker.value) {
                return ConnectedAudioDevice("Phone Speaker", AudioDeviceType.SPEAKER)
            }
            val preferred = preferredDevice.value
            if (preferred != null) {
                if (preferred.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && preferred.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
                ) {
                    return ConnectedAudioDevice("Phone Speaker", AudioDeviceType.SPEAKER)
                }
                val rawName = preferred.productName?.toString()?.trim().orEmpty()
                val name = if (rawName.isNotBlank() && !rawName.equals("Bluetooth", ignoreCase = true)) rawName else "Bluetooth Audio"
                val type = classifyBluetoothDevice(name)
                return ConnectedAudioDevice(name = name, type = type)
            }

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
                    val type = classifyBluetoothDevice(name)
                    return ConnectedAudioDevice(name = name, type = type)
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
                    val name = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC Audio"
                    return ConnectedAudioDevice(name = name, type = AudioDeviceType.USB_DAC)
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
                    return ConnectedAudioDevice("Bluetooth Audio", AudioDeviceType.BLUETOOTH_TWS)
                }
                @Suppress("DEPRECATION")
                if (audioManager.isWiredHeadsetOn) {
                    return ConnectedAudioDevice("Headphones", AudioDeviceType.HEADPHONES)
                }
            }

            ConnectedAudioDevice("Phone Speaker", AudioDeviceType.SPEAKER)
        }.getOrElse {
            ConnectedAudioDevice("Phone Speaker", AudioDeviceType.SPEAKER)
        }
    }

    fun getAvailableAudioOutputs(context: Context): List<AudioOutputOption> {
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return listOf(AudioOutputOption("speaker", "Phone Speaker", AudioDeviceType.SPEAKER, true))

            val active = getActiveAudioDevice(context)
            val outputs = mutableListOf<AudioOutputOption>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

                // 1. Phone Speaker
                val isSpeakerSelected = active.type == AudioDeviceType.SPEAKER
                outputs.add(
                    AudioOutputOption(
                        id = "speaker",
                        name = "Phone Speaker",
                        type = AudioDeviceType.SPEAKER,
                        isSelected = isSpeakerSelected,
                    ),
                )

                // 2. Connected Bluetooth Devices (Deduplicated by name & primary media profile)
                val btDevices = devices.filter {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                }
                val distinctBtDevices = btDevices.groupBy { bt ->
                    val rawName = bt.productName?.toString()?.trim().orEmpty()
                    if (rawName.isNotBlank() && !rawName.equals("Bluetooth", ignoreCase = true)) rawName else "Bluetooth Audio"
                }.map { (name, group) ->
                    val bestInfo = group.minByOrNull {
                        when (it.type) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
                            AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
                            AudioDeviceInfo.TYPE_BLE_SPEAKER -> 2
                            else -> 3
                        }
                    } ?: group.first()
                    name to bestInfo
                }

                for ((name, bt) in distinctBtDevices) {
                    val type = classifyBluetoothDevice(name)
                    val isSelected = active.name == name && active.isExternal
                    outputs.add(
                        AudioOutputOption(
                            id = "bt_${bt.id}",
                            name = name,
                            type = type,
                            isSelected = isSelected,
                            deviceInfo = bt,
                        ),
                    )
                }

                // 3. Wired Headphones
                val wired = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                }
                if (wired != null) {
                    val name = wired.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Wired Headphones"
                    outputs.add(
                        AudioOutputOption(
                            id = "wired_${wired.id}",
                            name = name,
                            type = AudioDeviceType.HEADPHONES,
                            isSelected = active.type == AudioDeviceType.HEADPHONES,
                            deviceInfo = wired,
                        ),
                    )
                }

                // 4. USB DAC
                val usb = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                if (usb != null) {
                    val name = usb.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC Audio"
                    outputs.add(
                        AudioOutputOption(
                            id = "usb_${usb.id}",
                            name = name,
                            type = AudioDeviceType.USB_DAC,
                            isSelected = active.type == AudioDeviceType.USB_DAC,
                            deviceInfo = usb,
                        ),
                    )
                }
            } else {
                outputs.add(
                    AudioOutputOption("speaker", "Phone Speaker", AudioDeviceType.SPEAKER, !active.isExternal),
                )
                if (active.isExternal) {
                    outputs.add(
                        AudioOutputOption("external", active.name, active.type, true),
                    )
                }
            }

            outputs
        }.getOrElse {
            listOf(AudioOutputOption("speaker", "Phone Speaker", AudioDeviceType.SPEAKER, true))
        }
    }

    fun selectAudioOutput(context: Context, option: AudioOutputOption): Boolean {
        return runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                if (option.type == AudioDeviceType.SPEAKER) {
                    forceSpeaker.value = true
                    val speaker = devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE)
                    }
                    preferredDevice.value = speaker
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && speaker != null) {
                        runCatching { audioManager.setCommunicationDevice(speaker) }
                    }
                    @Suppress("DEPRECATION")
                    runCatching { audioManager.isSpeakerphoneOn = true }
                } else {
                    forceSpeaker.value = false
                    preferredDevice.value = option.deviceInfo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            if (option.deviceInfo != null) {
                                audioManager.setCommunicationDevice(option.deviceInfo)
                            } else {
                                audioManager.clearCommunicationDevice()
                            }
                        }
                    }
                    @Suppress("DEPRECATION")
                    runCatching { audioManager.isSpeakerphoneOn = false }
                }
            } else {
                @Suppress("DEPRECATION")
                if (option.type == AudioDeviceType.SPEAKER) {
                    forceSpeaker.value = true
                    audioManager.isSpeakerphoneOn = true
                } else {
                    forceSpeaker.value = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun classifyBluetoothDevice(name: String): AudioDeviceType {
        val lower = name.lowercase()
        return when {
            lower.contains("speaker") || lower.contains("soundbar") || lower.contains("flip") ||
                lower.contains("charge") || lower.contains("boombox") || lower.contains("clip") ||
                lower.contains("go") || lower.contains("megaboom") || lower.contains("wonderboom") ||
                lower.contains("echo") || lower.contains("nest") || lower.contains("homepod") ||
                lower.contains("aura") || lower.contains("onyx") -> {
                AudioDeviceType.BLUETOOTH_SPEAKER
            }
            lower.contains("wh-") || lower.contains("headphone") || lower.contains("headset") ||
                lower.contains("over-ear") || lower.contains("studio") || lower.contains("solo") ||
                lower.contains("bose") || lower.contains("xm4") || lower.contains("xm5") ||
                lower.contains("major") || lower.contains("monitor") || lower.contains("wireless headset") -> {
                AudioDeviceType.BLUETOOTH_HEADPHONES
            }
            else -> AudioDeviceType.BLUETOOTH_TWS // Buds, AirPods, Earphones, TWS, etc.
        }
    }

    /**
     * Opens system Media Output dialog or Sound Settings.
     */
    fun openSystemMediaOutput(context: Context) {
        // 1. Try launching SystemUI Media Output broadcast
        runCatching {
            val broadcastIntent = Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").apply {
                setPackage("com.android.systemui")
                putExtra("package_name", context.packageName)
            }
            context.sendBroadcast(broadcastIntent)
        }

        // 2. Try Android Settings Panel for Media Output (Skip on Xiaomi/HyperOS due to OEM Fragment/ViewModel bug)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isXiaomiOrMiui()) {
            val launchedPanel = runCatching {
                val intent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    true
                } else {
                    false
                }
            }.getOrDefault(false)

            if (launchedPanel) return
        }

        // 3. Fallback to Sound Settings (Rock-solid across all OEMs including Xiaomi HyperOS)
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private fun isXiaomiOrMiui(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco")
    }

    /**
     * Opens system Bluetooth settings.
     */
    fun openBluetoothSettings(context: Context) {
        val launchedBt = runCatching {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!launchedBt) {
            runCatching {
                val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
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

    val forceSpeaker by AudioDeviceHelper.forceSpeaker.collectAsStateWithLifecycle()
    val preferredDevice by AudioDeviceHelper.preferredDevice.collectAsStateWithLifecycle()

    LaunchedEffect(forceSpeaker, preferredDevice) {
        deviceState.value = AudioDeviceHelper.getActiveAudioDevice(context)
    }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val audioCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    deviceState.value = AudioDeviceHelper.getActiveAudioDevice(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    deviceState.value = AudioDeviceHelper.getActiveAudioDevice(context)
                }
            }
        } else null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioCallback != null) {
            audioManager?.registerAudioDeviceCallback(audioCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        }

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
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure {
            runCatching {
                context.registerReceiver(receiver, filter)
            }
        }

        // Refresh initially
        deviceState.value = AudioDeviceHelper.getActiveAudioDevice(context)

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioCallback != null) {
                audioManager?.unregisterAudioDeviceCallback(audioCallback)
            }
            runCatching {
                context.unregisterReceiver(receiver)
            }
        }
    }

    return deviceState
}
