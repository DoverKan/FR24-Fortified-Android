package com.skydronex.fr24fortified.ui.home.box

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.skydronex.fr24fortified.data.box.BoxRepository
import com.skydronex.fr24fortified.data.box.BoxSnapshot
import com.skydronex.fr24fortified.data.sbs.SbsRepository
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun BoxHomeContent(ip: String, sbsRepo: SbsRepository, refreshKey: Int = 0) {
    val repo = remember(ip) { BoxRepository(ip) }
    var snapshot by remember { mutableStateOf<BoxSnapshot?>(null) }
    val aircraft by sbsRepo.aircraft.collectAsState()
    val msgRate  by sbsRepo.msgRate.collectAsState()

    LaunchedEffect(ip, refreshKey) {
        snapshot = repo.fetch()           // refresco inmediato al cambiar refreshKey
        while (true) {
            delay(2.seconds)
            snapshot = repo.fetch()
        }
    }

    val total      = aircraft.size
    val withPos    = aircraft.values.count { it.hasPosition }
    val withoutPos = total - withPos

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EstadoCard(snapshot, total, withPos, withoutPos, msgRate)
        SistemaCard(snapshot)
        RedCard(snapshot)
        GpsCard(snapshot)
    }
}

// ─── Tarjeta: Estado ────────────────────────────────────────────────────────

@Composable
private fun EstadoCard(
    snapshot: BoxSnapshot?,
    total: Int,
    withPos: Int,
    withoutPos: Int,
    msgRate: Int
) {
    val ov = snapshot?.overview
    InfoCard(title = "Estado", error = snapshot?.errors?.get("/index.php")) {
        // FR24 Feed status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            ov == null      -> Color(0xFFFBBF24)
                            ov.fr24Feeding  -> Color(0xFF34D399)
                            else            -> MaterialTheme.colorScheme.error
                        }
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    ov == null     -> "Comprobando..."
                    ov.fr24Feeding -> "Activo"
                    else           -> "Inactivo"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "FR24 Feed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        // Grid 2 columnas con métricas
        val metrics = listOf(
            ov?.aircraft1090        to "1090MHz aircraft",
            ov?.temperature         to "Temperatura",
            ov?.radarCode           to "Radar code",
            total.toString()        to "Aviones detectados",
            withPos.toString()      to "Con posición",
            withoutPos.toString()   to "Sin posición",
            msgRate.takeIf { it > 0 }?.toString() to "Msg / min (SBS)"
        )

        metrics.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (value, label) ->
                    MetricTile(value = value ?: "—", label = label, modifier = Modifier.weight(1f))
                }
                // Si el chunk tiene solo 1 elemento, rellena el espacio
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Tarjeta: Sistema ───────────────────────────────────────────────────────

@Composable
private fun SistemaCard(snapshot: BoxSnapshot?) {
    CollapsibleCard(title = "Sistema", error = snapshot?.errors?.get("/index.php")) {
        val s = snapshot?.system
        DataRow("Versión",     s?.version   ?: "—")
        DataRow("Actualizado", s?.updated   ?: "—")
        DataRow("Uptime",      s?.uptime    ?: "—")
        DataRow("Partición",   s?.partition ?: "—")
        DataRow("MAC",         s?.mac       ?: "—")
    }
}

// ─── Tarjeta: Red ───────────────────────────────────────────────────────────

@Composable
private fun RedCard(snapshot: BoxSnapshot?) {
    CollapsibleCard(title = "Red", error = snapshot?.errors?.get("/index.php")) {
        val n = snapshot?.network
        DataRow("IP externa",  n?.externalIp ?: "—")
        DataRow("IP interna",  n?.internalIp ?: "—")
        DataRow("DNS público", n?.dnsPublic  ?: "—")
        DataRow("DNS config.", n?.dnsConfig  ?: "—")
    }
}

// ─── Tarjeta: GPS ───────────────────────────────────────────────────────────

@Composable
private fun GpsCard(snapshot: BoxSnapshot?) {
    CollapsibleCard(title = "GPS", error = snapshot?.errors?.get("/index.php")) {
        val g = snapshot?.gpsInfo
        DataRow("Estado",    g?.status       ?: "—")
        DataRow("Satélites", g?.satellites   ?: "—")
        DataRow("Posición",  g?.position     ?: "—")
        DataRow("Señal",     g?.signalLevels ?: "—")
    }
}

// ─── Componentes compartidos ────────────────────────────────────────────────

// Tarjeta siempre abierta (Estado)
@Composable
private fun InfoCard(title: String, error: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            if (error != null) {
                Text("Error: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                content()
            }
        }
    }
}

// Tarjeta colapsable (Sistema, Red, GPS)
@Composable
private fun CollapsibleCard(title: String, error: String? = null, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    if (error != null) {
                        Text("Error: $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    } else {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
