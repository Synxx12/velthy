package com.velthy.client.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class SmartTvDevice(
    val id: String,
    val name: String,
    val modelName: String = "",
    val manufacturer: String = "",
    val location: String,
    val controlUrl: String = "",
    val renderingControlUrl: String = "",
    val ipAddress: String = "",
    val icon: ImageVector = Icons.Rounded.Tv,
)

/**
 * 100% Client-Side Smart TV & Wireless Audio Cast Engine.
 *
 * Discovers DLNA / UPnP MediaRenderer devices (Samsung TV, LG webOS, Sony Bravia,
 * Xiaomi Mi TV, Android TV, Roku, Google Cast DIAL, etc.) on local Wi-Fi via multi-interface SSDP
 * broadcast & native Android Network Service Discovery (NSD), and streams audio directly.
 */
object SmartTvCastManager {

    private const val TAG = "SmartTvCast"
    private const val SSDP_IP = "239.255.255.250"
    private const val SSDP_PORT = 1900

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _discoveredDevices = MutableStateFlow<List<SmartTvDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _activeCastDevice = MutableStateFlow<SmartTvDevice?>(null)
    val activeCastDevice = _activeCastDevice.asStateFlow()

    private val _isCastPlaying = MutableStateFlow(false)
    val isCastPlaying = _isCastPlaying.asStateFlow()

    private val devicesMap = ConcurrentHashMap<String, SmartTvDevice>()
    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdManager: NsdManager? = null

    /**
     * Starts local Wi-Fi SSDP scan and Android NSD for Smart TVs and UPnP Media Renderers.
     */
    fun startDiscovery(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true

        scanJob?.cancel()
        scanJob = scope.launch {
            try {
                // 1. Acquire Wi-Fi Multicast Lock
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("velthy_tv_cast_discovery")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }

                // 2. Start Android NSD Discovery for Cast & DLNA
                startNsdDiscovery(context)

                // 3. Multi-Interface SSDP M-SEARCH Broadcasts
                val searchTargets = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "urn:dial-multiscreen-org:service:dial:1",
                    "ssdp:all",
                )

                for (target in searchTargets) {
                    if (!isActive) break
                    sendMultiInterfaceSsdpSearch(target)
                    delay(300)
                }

                // Listen for responses
                delay(4000)
            } catch (e: Exception) {
                Log.w(TAG, "Discovery error: ${e.message}")
            } finally {
                _isScanning.value = false
                runCatching {
                    multicastLock?.release()
                    multicastLock = null
                }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
        runCatching {
            multicastLock?.release()
            multicastLock = null
        }
    }

    private suspend fun sendMultiInterfaceSsdpSearch(searchTarget: String) = withContext(Dispatchers.IO) {
        runCatching {
            val query = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $SSDP_IP:$SSDP_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 3\r\n" +
                "ST: $searchTarget\r\n\r\n"

            val queryBytes = query.toByteArray()
            val socket = DatagramSocket()
            socket.soTimeout = 2000
            socket.broadcast = true

            // Send to global multicast
            runCatching {
                val packet = DatagramPacket(queryBytes, queryBytes.size, InetAddress.getByName(SSDP_IP), SSDP_PORT)
                socket.send(packet)
            }

            // Send to global broadcast 255.255.255.255
            runCatching {
                val broadcastPacket = DatagramPacket(queryBytes, queryBytes.size, InetAddress.getByName("255.255.255.255"), SSDP_PORT)
                socket.send(broadcastPacket)
            }

            // Send to all Wi-Fi subnet broadcast addresses
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
                for (intf in interfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    for (intfAddr in intf.interfaceAddresses) {
                        val bcast = intfAddr.broadcast
                        if (bcast != null) {
                            val subnetPacket = DatagramPacket(queryBytes, queryBytes.size, bcast, SSDP_PORT)
                            socket.send(subnetPacket)
                        }
                    }
                }
            }

            // Receive incoming SSDP responses
            val receiveBuffer = ByteArray(4096)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 2000) {
                try {
                    val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val location = extractHeader(response, "LOCATION")
                    val hostAddr = receivePacket.address.hostAddress.orEmpty()
                    if (!location.isNullOrBlank()) {
                        scope.launch { parseDeviceDescription(location, hostAddr) }
                    } else if (response.contains("200 OK") || response.contains("NOTIFY")) {
                        // Attempt fallback DIAL descriptor on port 8008 / 8080
                        scope.launch { tryProbeCommonPorts(hostAddr) }
                    }
                } catch (_: Exception) {
                    break
                }
            }
            socket.close()
        }
    }

    private fun startNsdDiscovery(context: Context) {
        runCatching {
            if (nsdManager == null) {
                nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            }
            val nsd = nsdManager ?: return

            val serviceTypes = listOf(
                "_googlecast._tcp.",
                "_dial._tcp.",
                "_raop._tcp.",
                "_dlna._tcp.",
            )

            for (type in serviceTypes) {
                runCatching {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                        override fun onDiscoveryStarted(serviceType: String?) {}
                        override fun onDiscoveryStopped(serviceType: String?) {}
                        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
                        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                            if (serviceInfo == null) return
                            runCatching {
                                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                        val host = resolvedInfo?.host?.hostAddress ?: return
                                        val serviceName = resolvedInfo.serviceName ?: "Smart TV / Cast"
                                        val port = resolvedInfo.port
                                        scope.launch {
                                            registerNsdDevice(serviceName, host, port)
                                        }
                                    }
                                })
                            }
                        }
                    })
                }
            }
        }
    }

    private suspend fun registerNsdDevice(name: String, ip: String, port: Int) = withContext(Dispatchers.IO) {
        val cleanName = name.replace(Regex("-[0-9a-f]{8,}$", RegexOption.IGNORE_CASE), "").trim()
        val id = "nsd_${ip}_$cleanName"
        if (devicesMap.containsKey(id)) return@withContext

        // Probe for UPnP descriptor first
        val probeSuccess = tryProbeCommonPorts(ip)
        if (!probeSuccess) {
            val device = SmartTvDevice(
                id = id,
                name = cleanName,
                modelName = "Cast / Display",
                manufacturer = "Smart Display",
                location = "http://$ip:$port/",
                controlUrl = "http://$ip:8008/apps/YouTube",
                ipAddress = ip,
                icon = Icons.Rounded.Cast,
            )
            devicesMap[id] = device
            _discoveredDevices.value = devicesMap.values.toList().sortedBy { it.name }
        }
    }

    private suspend fun tryProbeCommonPorts(ip: String): Boolean = withContext(Dispatchers.IO) {
        val candidateUrls = listOf(
            "http://$ip:8008/ssdp/device-desc.xml",
            "http://$ip:8080/description.xml",
            "http://$ip:7676/smp_2_",
            "http://$ip:52235/dmr/SamsungMRDesc.xml",
        )
        for (url in candidateUrls) {
            if (devicesMap.containsKey(url)) return@withContext true
            val success = runCatching {
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        parseDeviceDescription(url, ip, body)
                        true
                    } else {
                        false
                    }
                }
            }.getOrDefault(false)
            if (success) return@withContext true
        }
        false
    }

    private suspend fun parseDeviceDescription(location: String, ipAddress: String, preloadedXml: String? = null) = withContext(Dispatchers.IO) {
        if (devicesMap.containsKey(location)) return@withContext
        runCatching {
            val xml = preloadedXml ?: run {
                val request = Request.Builder().url(location).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use ""
                    response.body?.string().orEmpty()
                }
            }
            if (xml.isBlank()) return@runCatching

            val friendlyName = extractTag(xml, "friendlyName")?.trim().orEmpty()
            val manufacturer = extractTag(xml, "manufacturer")?.trim().orEmpty()
            val modelName = extractTag(xml, "modelName")?.trim().orEmpty()
            val udn = extractTag(xml, "UDN")?.trim() ?: location

            if (friendlyName.isNotBlank()) {
                val isTvOrAudio = isSmartTvOrAudioDevice(friendlyName, manufacturer, modelName, xml)
                if (isTvOrAudio) {
                    val avTransportControlUrl = extractServiceControlUrl(xml, location, "urn:schemas-upnp-org:service:AVTransport:1")
                    val renderingControlUrl = extractServiceControlUrl(xml, location, "urn:schemas-upnp-org:service:RenderingControl:1")

                    val cleanName = friendlyName
                        .replace(Regex("^\\[TV\\]\\s*", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\s*\\[TV\\]$", RegexOption.IGNORE_CASE), "")

                    val device = SmartTvDevice(
                        id = udn,
                        name = cleanName,
                        modelName = modelName,
                        manufacturer = manufacturer,
                        location = location,
                        controlUrl = avTransportControlUrl.ifBlank { "http://$ipAddress:8008/apps" },
                        renderingControlUrl = renderingControlUrl,
                        ipAddress = ipAddress,
                    )

                    devicesMap[location] = device
                    _discoveredDevices.value = devicesMap.values.toList().sortedBy { it.name }
                    Log.i(TAG, "Discovered Smart TV: $cleanName ($manufacturer $modelName) at $location")
                }
            }
        }
    }

    private fun isSmartTvOrAudioDevice(name: String, manufacturer: String, modelName: String, xml: String): Boolean {
        val combined = "$name $manufacturer $modelName $xml".lowercase()
        return combined.contains("mediarenderer") ||
            combined.contains("avtransport") ||
            combined.contains("tv") ||
            combined.contains("bravia") ||
            combined.contains("samsung") ||
            combined.contains("webos") ||
            combined.contains("chromecast") ||
            combined.contains("roku") ||
            combined.contains("speaker") ||
            combined.contains("soundbar") ||
            combined.contains("dial")
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.lines()
        for (line in lines) {
            if (line.startsWith("$headerName:", ignoreCase = true)) {
                return line.substring(headerName.length + 1).trim()
            }
        }
        return null
    }

    private fun extractTag(xml: String, tagName: String): String? {
        val pattern = "<$tagName>(.*?)</$tagName>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun extractServiceControlUrl(xml: String, baseUrl: String, serviceType: String): String {
        return runCatching {
            val servicePattern = "<service>.*?<serviceType>\\s*$serviceType\\s*</serviceType>.*?<controlURL>(.*?)</controlURL>.*?</service>"
                .toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = servicePattern.find(xml)
            val rawControlUrl = match?.groupValues?.get(1)?.trim().orEmpty()
            if (rawControlUrl.isBlank()) return@runCatching ""

            if (rawControlUrl.startsWith("http://", ignoreCase = true) || rawControlUrl.startsWith("https://", ignoreCase = true)) {
                rawControlUrl
            } else {
                val uri = URI(baseUrl)
                val base = "${uri.scheme}://${uri.host}:${if (uri.port != -1) uri.port else 80}"
                if (rawControlUrl.startsWith("/")) "$base$rawControlUrl" else "$base/$rawControlUrl"
            }
        }.getOrDefault("")
    }

    /**
     * Casts audio stream directly to the target Smart TV.
     */
    suspend fun castAudio(
        device: SmartTvDevice,
        streamUrl: String,
        title: String,
        artist: String,
        artworkUrl: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        if (device.controlUrl.isBlank()) {
            Log.w(TAG, "No control URL available for ${device.name}")
            return@withContext false
        }

        runCatching {
            // 1. Stop current playback on TV
            sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Stop", "<InstanceID>0</InstanceID>")

            // 2. Send SetAVTransportURI
            val metaDataXml = """
                &lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;
                &lt;item id="1" parentID="0" restricted="1"&gt;
                &lt;dc:title&gt;${escapeXml(title)}&lt;/dc:title&gt;
                &lt;dc:creator&gt;${escapeXml(artist)}&lt;/dc:creator&gt;
                &lt;upnp:class&gt;object.item.audioItem.musicTrack&lt;/upnp:class&gt;
                &lt;upnp:albumArtURI&gt;${escapeXml(artworkUrl)}&lt;/upnp:albumArtURI&gt;
                &lt;res protocolInfo="http-get:*:audio/mp4:*"&gt;${escapeXml(streamUrl)}&lt;/res&gt;
                &lt;/item&gt;
                &lt;/DIDL-Lite&gt;
            """.trimIndent()

            val setUriArgs = "<InstanceID>0</InstanceID><CurrentURI>${escapeXml(streamUrl)}</CurrentURI><CurrentURIMetaData>$metaDataXml</CurrentURIMetaData>"
            sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "SetAVTransportURI", setUriArgs)

            // 3. Send Play
            val playArgs = "<InstanceID>0</InstanceID><Speed>1</Speed>"
            val playSuccess = sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Play", playArgs)

            if (playSuccess) {
                _activeCastDevice.value = device
                _isCastPlaying.value = true
                Log.i(TAG, "Now Casting to ${device.name}: $title by $artist")
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    suspend fun pauseCast(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeCastDevice.value ?: return@withContext false
        val ok = sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Pause", "<InstanceID>0</InstanceID>")
        if (ok) _isCastPlaying.value = false
        ok
    }

    suspend fun resumeCast(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeCastDevice.value ?: return@withContext false
        val ok = sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
        if (ok) _isCastPlaying.value = true
        ok
    }

    suspend fun stopCast(): Boolean = withContext(Dispatchers.IO) {
        val device = _activeCastDevice.value ?: return@withContext false
        sendSoapAction(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1", "Stop", "<InstanceID>0</InstanceID>")
        _activeCastDevice.value = null
        _isCastPlaying.value = false
        true
    }

    suspend fun setVolume(volume0to100: Int): Boolean = withContext(Dispatchers.IO) {
        val device = _activeCastDevice.value ?: return@withContext false
        if (device.renderingControlUrl.isBlank()) return@withContext false
        val vol = volume0to100.coerceIn(0, 100)
        val args = "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>$vol</DesiredVolume>"
        sendSoapAction(device.renderingControlUrl, "urn:schemas-upnp-org:service:RenderingControl:1", "SetVolume", args)
    }

    private fun sendSoapAction(controlUrl: String, serviceType: String, action: String, arguments: String): Boolean {
        if (controlUrl.isBlank()) return false
        return runCatching {
            val soapBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:$action xmlns:u="$serviceType">
                      $arguments
                    </u:$action>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            val request = Request.Builder()
                .url(controlUrl)
                .addHeader("SOAPAction", "\"$serviceType#$action\"")
                .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .post(soapBody.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
