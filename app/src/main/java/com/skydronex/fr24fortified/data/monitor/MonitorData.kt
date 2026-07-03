package com.skydronex.fr24fortified.data.monitor

data class MonitorData(
    val feedStatus: String,
    val feedCurrentServer: String,
    val feedAlias: String,
    val feedLegacyId: String,
    val feedCurrentMode: String,
    val feedType: String,
    val feedNumAcTracked: Int,
    val feedNumAcAdsbTracked: Int,
    val feedNumAcNonAdsbTracked: Int,
    val numMessages: Int,
    val rxConnected: Boolean,
    val lastRxConnectStatus: String,
    val cfgHost: String,
    val buildVersion: String,
    val mlatOk: String,
    val timeUpdateUtcS: String
)
