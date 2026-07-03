package com.skydronex.fr24fortified.data.sbs

data class SbsAircraft(
    val icao: String,
    val callsign: String? = null,
    val altitude: Int? = null,
    val speed: Int? = null,
    val track: Int? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val vertRate: Int? = null,
    val onGround: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val hasPosition: Boolean get() = lat != null && lon != null
}
