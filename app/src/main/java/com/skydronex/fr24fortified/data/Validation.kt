package com.skydronex.fr24fortified.data

object Validation {

    fun validateIp(ip: String): String? {
        if (ip.isBlank()) return "La dirección IP no puede estar vacía"
        val parts = ip.trim().split(".")
        if (parts.size != 4) return "Formato inválido (ej. 192.168.1.100)"
        if (parts.any { part -> part.toIntOrNull()?.let { it < 0 || it > 255 } != false }) {
            return "Cada octeto debe estar entre 0 y 255"
        }
        return null
    }

    fun validatePort(value: String, label: String): String? {
        val n = value.trim().toIntOrNull()
            ?: return "$label debe ser un número"
        if (n < 1 || n > 65535) return "$label debe estar entre 1 y 65535"
        return null
    }
}
