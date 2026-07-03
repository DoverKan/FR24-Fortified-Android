package com.skydronex.fr24fortified.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

sealed class ConnectionResult {
    data object Success : ConnectionResult()
    data class Failure(val port: Int, val message: String) : ConnectionResult()
}

object ConnectionChecker {

    private const val TIMEOUT_MS = 4000

    suspend fun check(ip: String, deviceType: DeviceType, consolePort: Int, feederPort: Int): ConnectionResult {
        val ports = when (deviceType) {
            DeviceType.FEEDER -> listOf(feederPort)
            DeviceType.BOX -> listOf(consolePort)
        }
        return withContext(Dispatchers.IO) {
            for (port in ports) {
                val result = testPort(ip, port)
                if (result != null) return@withContext result
            }
            ConnectionResult.Success
        }
    }

    private fun testPort(ip: String, port: Int): ConnectionResult.Failure? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), TIMEOUT_MS)
            }
            null
        } catch (e: SocketTimeoutException) {
            ConnectionResult.Failure(port, "Tiempo de espera agotado (puerto $port)")
        } catch (e: Exception) {
            ConnectionResult.Failure(port, "No se pudo conectar al puerto $port: ${e.message ?: "error desconocido"}")
        }
    }
}
