package com.skydronex.fr24fortified.data.monitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MonitorRepository(private val ip: String, private val port: Int) {

    private val _data  = MutableStateFlow<MonitorData?>(null)
    val data: StateFlow<MonitorData?> = _data.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    suspend fun start() = withContext(Dispatchers.IO) {
        while (isActive) {
            try {
                val conn = URL("http://$ip:$port/monitor.json").openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout    = 5_000
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                _data.value = MonitorData(
                    feedStatus             = json.optString("feed_status"),
                    feedCurrentServer      = json.optString("feed_current_server"),
                    feedAlias              = json.optString("feed_alias"),
                    feedLegacyId           = json.optString("feed_legacy_id"),
                    feedCurrentMode        = json.optString("feed_current_mode"),
                    feedType               = json.optString("feed_type"),
                    feedNumAcTracked       = json.optString("feed_num_ac_tracked").toIntOrNull() ?: 0,
                    feedNumAcAdsbTracked   = json.optString("feed_num_ac_adsb_tracked").toIntOrNull() ?: 0,
                    feedNumAcNonAdsbTracked= json.optString("feed_num_ac_non_adsb_tracked").toIntOrNull() ?: 0,
                    numMessages            = json.optString("num_messages").toIntOrNull() ?: 0,
                    rxConnected            = json.optString("rx_connected") == "1",
                    lastRxConnectStatus    = json.optString("last_rx_connect_status"),
                    cfgHost                = json.optString("cfg_host"),
                    buildVersion           = json.optString("build_version"),
                    mlatOk                 = json.optString("mlat-ok"),
                    timeUpdateUtcS         = json.optString("time_update_utc_s")
                )
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Sin datos: ${e.message}"
            }
            if (isActive) delay(30_000L)
        }
    }
}
