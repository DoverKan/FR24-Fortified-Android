package com.skydronex.fr24fortified.data.box

data class BoxOverview(
    val fr24Feeding: Boolean,
    val radarCode: String,
    val aircraft1090: String,
    val temperature: String,
    val aircraftDetected: Int?,
    val aircraftWithPos: Int?,
    val aircraftWithoutPos: Int?,
    val messagesPerMin: String?
)

data class BoxSystem(
    val version: String,
    val updated: String,
    val uptime: String,
    val partition: String,
    val mac: String
)

data class BoxNetwork(
    val externalIp: String,
    val internalIp: String,
    val dnsPublic: String,
    val dnsConfig: String
)

data class BoxGpsInfo(
    val status: String,
    val satellites: String,
    val position: String,
    val signalLevels: String,
    val antenna: String
)

data class BoxSnapshot(
    val overview: BoxOverview?,
    val system: BoxSystem?,
    val network: BoxNetwork?,
    val gpsInfo: BoxGpsInfo?,
    val errors: Map<String, String> = emptyMap()
)
