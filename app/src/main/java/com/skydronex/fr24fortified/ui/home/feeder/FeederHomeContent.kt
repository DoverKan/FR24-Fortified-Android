package com.skydronex.fr24fortified.ui.home.feeder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skydronex.fr24fortified.data.monitor.MonitorData
import com.skydronex.fr24fortified.data.monitor.MonitorRepository
import com.skydronex.fr24fortified.data.sbs.SbsAircraft
import com.skydronex.fr24fortified.data.sbs.SbsRepository
import com.skydronex.fr24fortified.ui.theme.Amber

private val ColorGreen  = Color(0xFF34D399)
private val ColorOrange = Color(0xFFFBBF24)

@Composable
fun FeederHomeContent(sbsRepo: SbsRepository, monitorRepo: MonitorRepository) {
    val aircraft     by sbsRepo.aircraft.collectAsState()
    val connected    by sbsRepo.connected.collectAsState()
    val msgRate      by sbsRepo.msgRate.collectAsState()
    val monitorData  by monitorRepo.data.collectAsState()
    val monitorError by monitorRepo.error.collectAsState()

    val sorted     = aircraft.values.sortedByDescending { it.altitude ?: -1 }.take(50)
    val total      = aircraft.size
    val withPos    = aircraft.values.count { it.hasPosition }
    val withoutPos = total - withPos

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MonitorCard(monitorData, monitorError)
        if (connected) {
            ResumenCard(total, withPos, withoutPos, msgRate)
            if (sorted.isNotEmpty()) TraficoCard(sorted)
        }
    }
}

// ─── AccentCard ─────────────────────────────────────────────────────────────

@Composable
private fun AccentCard(
    modifier: Modifier = Modifier,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier.weight(1f).padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun CardTitle(title: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ─── Monitor feeder ──────────────────────────────────────────────────────────

@Composable
private fun MonitorCard(data: MonitorData?, error: String?) {
    val accent = MaterialTheme.colorScheme.primary
    AccentCard(modifier = Modifier.fillMaxWidth(), accentColor = accent) {
        CardTitle("Monitor feeder", accent)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        when {
            data == null && error != null ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            data == null ->
                Text("Cargando…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val feedColor = if (data.feedStatus == "connected") ColorGreen else MaterialTheme.colorScheme.error
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(feedColor))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${data.feedStatus.replaceFirstChar { it.uppercase() }} · ${data.feedCurrentServer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(data.feedNumAcTracked.toString(),         "Aviones",   accent)
                    StatItem(data.feedNumAcAdsbTracked.toString(),     "ADS-B",     ColorGreen)
                    StatItem(data.feedNumAcNonAdsbTracked.toString(),  "No ADS-B",  MaterialTheme.colorScheme.onSurfaceVariant)
                    StatItem(data.numMessages.toString(),              "Mensajes",  MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                MonitorInfoRow("Alias",    data.feedAlias)
                MonitorInfoRow("ID",       data.feedLegacyId)
                MonitorInfoRow("Tipo",     data.feedType.uppercase())
                MonitorInfoRow("Modo",     data.feedCurrentMode)
                MonitorInfoRow("Receptor", "${data.lastRxConnectStatus} · ${data.cfgHost}")
                MonitorInfoRow("MLAT",     data.mlatOk)
                MonitorInfoRow("Versión",  data.buildVersion)
                if (data.timeUpdateUtcS.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Actualizado: ${data.timeUpdateUtcS}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Tráfico SBS ─────────────────────────────────────────────────────────────

@Composable
private fun ResumenCard(total: Int, withPos: Int, withoutPos: Int, msgRate: Int) {
    val accent = MaterialTheme.colorScheme.secondary
    AccentCard(modifier = Modifier.fillMaxWidth(), accentColor = accent) {
        CardTitle("Tráfico SBS", accent)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ColorGreen))
            Spacer(Modifier.width(8.dp))
            Text("Conectado · Puerto 30003",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(total.toString(),      "Detectados",   MaterialTheme.colorScheme.primary)
            StatItem(withPos.toString(),    "Con posición", ColorGreen)
            StatItem(withoutPos.toString(), "Sin posición", MaterialTheme.colorScheme.onSurfaceVariant)
            if (msgRate > 0) StatItem(msgRate.toString(), "Msg / min", accent)
        }
    }
}

// ─── Aviones activos ─────────────────────────────────────────────────────────

@Composable
private fun TraficoCard(aircraft: List<SbsAircraft>) {
    AccentCard(modifier = Modifier.fillMaxWidth(), accentColor = Amber) {
        CardTitle("Aviones activos", Amber)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("ICAO", "Vuelo", "Alt (ft)", "Vel (kt)", "Pos").forEach { col ->
                Text(col,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider()

        aircraft.forEach { ac ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(ac.icao, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(ac.callsign ?: "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(ac.altitude?.toString() ?: "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(ac.speed?.toString() ?: "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Box(modifier = Modifier
                        .size(8.dp).clip(CircleShape)
                        .background(if (ac.hasPosition) ColorGreen else MaterialTheme.colorScheme.outlineVariant))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun StatItem(value: String, label: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonitorInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(2f))
    }
}
