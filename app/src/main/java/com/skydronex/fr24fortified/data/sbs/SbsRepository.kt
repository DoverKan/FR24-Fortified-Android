package com.skydronex.fr24fortified.data.sbs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class SbsRepository(private val ip: String, private val port: Int) {

    private val _aircraft  = MutableStateFlow<Map<String, SbsAircraft>>(emptyMap())
    val aircraft: StateFlow<Map<String, SbsAircraft>> = _aircraft.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _msgRate      = MutableStateFlow(0)
    val msgRate: StateFlow<Int> = _msgRate.asStateFlow()

    // Ventana deslizante de 60 segundos para calcular la tasa de mensajes
    private val msgTimestamps = ArrayDeque<Long>()

    suspend fun start() = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val socket = Socket()
                socket.soTimeout = 1_000
                socket.connect(InetSocketAddress(ip, port), 5_000)
                socket.use {
                    _connected.value = true
                    val reader = it.getInputStream().bufferedReader()
                    while (isActive) {
                        try {
                            val line = reader.readLine() ?: break
                            parseLine(line)
                            pruneStale()
                        } catch (_: SocketTimeoutException) {
                            pruneStale()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                _connected.value = false
            }
            if (isActive) delay(5_000L)
        }
    }

    private fun parseLine(raw: String) {
        val f = raw.trim().split(",")
        if (f.size < 5 || f[0] != "MSG") return
        val icao = f.getOrNull(4)?.uppercase()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val msgType = f.getOrNull(1)?.toIntOrNull() ?: return

        val now = System.currentTimeMillis()
        val cur = _aircraft.value[icao] ?: SbsAircraft(icao, lastSeen = now)

        val upd = when (msgType) {
            1 -> cur.copy(
                callsign = f.getOrNull(10)?.trim()?.takeIf { it.isNotEmpty() } ?: cur.callsign,
                lastSeen = now
            )
            2 -> cur.copy(
                lat      = f.getOrNull(14)?.toDoubleOrNull() ?: cur.lat,
                lon      = f.getOrNull(15)?.toDoubleOrNull() ?: cur.lon,
                speed    = f.getOrNull(12)?.toIntOrNull() ?: cur.speed,
                track    = f.getOrNull(13)?.toIntOrNull() ?: cur.track,
                onGround = true,
                lastSeen = now
            )
            3 -> cur.copy(
                altitude = f.getOrNull(11)?.toIntOrNull() ?: cur.altitude,
                lat      = f.getOrNull(14)?.toDoubleOrNull() ?: cur.lat,
                lon      = f.getOrNull(15)?.toDoubleOrNull() ?: cur.lon,
                onGround = f.getOrNull(21)?.trim() == "1",
                lastSeen = now
            )
            4 -> cur.copy(
                speed    = f.getOrNull(12)?.toIntOrNull() ?: cur.speed,
                track    = f.getOrNull(13)?.toIntOrNull() ?: cur.track,
                vertRate = f.getOrNull(16)?.toIntOrNull() ?: cur.vertRate,
                lastSeen = now
            )
            5, 6 -> cur.copy(
                altitude = f.getOrNull(11)?.toIntOrNull() ?: cur.altitude,
                lastSeen = now
            )
            else -> cur.copy(lastSeen = now)
        }
        _aircraft.value = _aircraft.value + (icao to upd)

        // Ventana deslizante: cuenta mensajes en el último minuto
        msgTimestamps.addLast(now)
        while (msgTimestamps.isNotEmpty() && msgTimestamps.first() < now - 60_000L) {
            msgTimestamps.removeFirst()
        }
        _msgRate.value = msgTimestamps.size
    }

    private fun pruneStale() {
        val cutoff = System.currentTimeMillis() - STALE_MS
        val pruned = _aircraft.value.filter { it.value.lastSeen >= cutoff }
        if (pruned.size != _aircraft.value.size) _aircraft.value = pruned
    }

    companion object {
        private const val STALE_MS = 60_000L
    }
}
