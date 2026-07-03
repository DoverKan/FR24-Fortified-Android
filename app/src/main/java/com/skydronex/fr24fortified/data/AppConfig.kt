package com.skydronex.fr24fortified.data

enum class DeviceType { FEEDER, BOX }

data class AppConfig(
    val ipAddress: String,
    val deviceType: DeviceType,
    val consolePort: Int,
    val feederPort: Int,
    val mapboxToken: String
)
