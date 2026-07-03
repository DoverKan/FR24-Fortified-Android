package com.skydronex.fr24fortified.data.box

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class BoxRepository(private val ip: String) {

    private data class HttpResult(val body: String?, val error: String?)

    private fun get(path: String): HttpResult = try {
        val conn = URL("http://$ip$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout    = 5000
        conn.requestMethod  = "GET"
        val code = conn.responseCode
        if (code == 200) HttpResult(conn.inputStream.bufferedReader().readText(), null)
        else HttpResult(null, "HTTP $code")
    } catch (e: Exception) {
        HttpResult(null, e.message ?: e.javaClass.simpleName)
    }

    suspend fun fetch(): BoxSnapshot = withContext(Dispatchers.IO) {
        val dIndex   = async { get("/index.php") }
        val dFlights = async { get("/flights.js") }
        val dStats   = async { get("/stats.json") }

        val rIndex   = dIndex.await()
        val rFlights = dFlights.await()
        val rStats   = dStats.await()

        val errors = mutableMapOf<String, String>()
        if (rIndex.error != null) errors["/index.php"] = rIndex.error

        val kv = parseHtmlToMap(rIndex.body)

        val (withPos, withoutPos) = parseFlightCounts(rFlights.body)
        val messagesPerMin = parseMessagesPerMin(rStats.body)

        val overview = if (rIndex.body != null) BoxOverview(
            fr24Feeding      = kv["fr24 feeding"]?.trim()?.uppercase() == "YES",
            radarCode        = kv.pick("fr24 radar code"),
            aircraft1090     = kv.pick("1090mhz aicraft", "1090mhz aircraft", "1090 mhz aircraft"),
            temperature      = kv.pick("temperature"),
            aircraftDetected = withPos?.let { w -> withoutPos?.let { wo -> w + wo } },
            aircraftWithPos  = withPos,
            aircraftWithoutPos = withoutPos,
            messagesPerMin   = messagesPerMin
        ) else null

        val system = if (rIndex.body != null) BoxSystem(
            version   = extractVersion(rIndex.body, kv),
            updated   = extractDate(rIndex.body, kv),
            uptime    = kv.pick("uptime"),
            partition = kv.pick("persistent partition usage", "partition usage"),
            mac       = kv.pick("mac address", "mac")
        ) else null

        val network = if (rIndex.body != null) BoxNetwork(
            externalIp = kv.pick("external ip"),
            internalIp = kv.pick("internal ip"),
            dnsPublic  = kv.pick("dns public"),
            dnsConfig  = kv.pick("dns configured", "dns config")
        ) else null

        val gpsInfo = if (rIndex.body != null) BoxGpsInfo(
            status       = kv.pick("status"),
            satellites   = kv.pick("satellites used", "satellites"),
            position     = kv.pick("gps position", "position"),
            signalLevels = kv.pick("signal levels"),
            antenna      = kv.pick("antenna", "antenna status")
        ) else null

        BoxSnapshot(overview, system, network, gpsInfo, errors)
    }

    // Construye un mapa clave(lowercase) → valor de todas las filas de tabla
    private fun parseHtmlToMap(html: String?): Map<String, String> {
        html ?: return emptyMap()
        val kv = mutableMapOf<String, String>()
        runCatching {
            val doc = Jsoup.parse(html)
            doc.select("tr").forEach { row ->
                val cells = row.select("td")
                if (cells.size >= 2) {
                    val k = cells[0].text().trim().trimEnd(':').lowercase()
                    val v = cells[1].text().trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) kv[k] = v
                }
                val th = row.select("th").firstOrNull()
                val td = row.select("td").firstOrNull()
                if (th != null && td != null) {
                    kv[th.text().trim().trimEnd(':').lowercase()] = td.text().trim()
                }
            }
        }
        return kv
    }

    // Versión del sistema: aparece al inicio del HTML como texto suelto (antes que cualquier versión de GPS)
    private fun extractVersion(html: String, kv: Map<String, String>): String {
        return Regex("""\d+\.\d+\.\d+""").find(html)?.value ?: "—"
    }

    // Fecha de actualización: primera fecha encontrada en el HTML (aparece al inicio)
    private fun extractDate(html: String, kv: Map<String, String>): String {
        return Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}""").find(html)?.value ?: "—"
    }

    // Cuenta aeronaves con/sin posición desde flights.js
    private fun parseFlightCounts(body: String?): Pair<Int?, Int?> {
        body ?: return Pair(null, null)
        return runCatching {
            val json = body.trim()
                .removePrefix("var SX_ACLIST=")
                .removeSuffix(";")
            val arr = JSONArray(json)
            var withPos = 0; var withoutPos = 0
            for (i in 0 until arr.length()) {
                val ac = arr.optJSONObject(i) ?: continue
                if (ac.has("Lat") && ac.has("Long")) withPos++ else withoutPos++
            }
            Pair(withPos, withoutPos)
        }.recoverCatching {
            val arr = JSONArray(body.trim())
            var withPos = 0; var withoutPos = 0
            for (i in 0 until arr.length()) {
                val ac = arr.optJSONObject(i) ?: continue
                if (ac.has("Lat") && ac.has("Long")) withPos++ else withoutPos++
            }
            Pair(withPos, withoutPos)
        }.getOrElse { Pair(null, null) }
    }

    // Mensajes por minuto desde stats.json
    private fun parseMessagesPerMin(body: String?): String? {
        body ?: return null
        return runCatching {
            val j = JSONObject(body)
            val rate = j.optDouble("messages_rate", -1.0)
                .takeIf { it >= 0 }
                ?: j.optJSONObject("aircraft")?.optDouble("messages_rate", -1.0)?.takeIf { it >= 0 }
            rate?.let { "%.3f".format(it) }
        }.getOrNull()
    }

    // Busca el primer valor que coincida con alguna de las claves (insensible a mayúsculas)
    private fun Map<String, String>.pick(vararg keys: String): String {
        for (key in keys) {
            val hit = entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value
            if (!hit.isNullOrEmpty()) return hit
        }
        return "—"
    }
}
