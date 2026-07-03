package com.skydronex.fr24fortified.ui.console

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

private const val MAX_LINES = 500

// Tipos de mensaje SBS con su color y etiqueta
private data class MsgType(val key: String, val label: String, val color: Color)
private val MSG_TYPES = listOf(
    MsgType("1",  "MSG,1 · ID",       Color(0xFF64B5F6)),
    MsgType("2",  "MSG,2 · Superficie",Color(0xFF80CBC4)),
    MsgType("3",  "MSG,3 · Posición",  Color(0xFF81C784)),
    MsgType("4",  "MSG,4 · Velocidad", Color(0xFFFFF176)),
    MsgType("56", "MSG,5/6 · Altitud", Color(0xFFFFB74D)),
    MsgType("?",  "Otros",             Color(0xFF9E9E9E)),
)

private fun lineType(line: String): String {
    val t = line.split(",").getOrNull(1)?.trim() ?: return "?"
    return when (t) {
        "5", "6" -> "56"
        "1", "2", "3", "4" -> t
        else -> "?"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SbsConsoleScreen(ip: String, port: Int, onBack: () -> Unit) {
    BackHandler { onBack() }

    val lines     = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    var connected by remember { mutableStateOf(false) }
    var paused    by remember { mutableStateOf(false) }

    val activeFilters = remember { mutableStateOf(MSG_TYPES.map { it.key }.toSet()) }
    val visibleLines by remember { derivedStateOf { lines.filter { activeFilters.value.contains(lineType(it)) } } }
    var started     by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(ip, port, started) {
        if (!started) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    val socket = Socket()
                    socket.soTimeout = 1_000
                    socket.connect(InetSocketAddress(ip, port), 5_000)
                    socket.use {
                        withContext(Dispatchers.Main) { connected = true }
                        val reader = it.getInputStream().bufferedReader()
                        while (isActive) {
                            try {
                                val line = reader.readLine() ?: break
                                if (!paused) {
                                    withContext(Dispatchers.Main) {
                                        lines.add(line)
                                        if (lines.size > MAX_LINES) lines.removeAt(0)
                                    }
                                }
                            } catch (_: SocketTimeoutException) { }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    withContext(Dispatchers.Main) { connected = false }
                }
                if (isActive) delay(3_000L)
            }
        }
    }

    // Auto-scroll: snapshotFlow rastrea cambios en visibleLines sin cancelarse en cada mensaje
    LaunchedEffect(listState) {
        snapshotFlow { visibleLines.size to paused }
            .collect { (size, isPaused) ->
                if (!isPaused && size > 0) listState.scrollToItem(size - 1)
            }
    }

    val dotColor = when {
        !started   -> Color(0xFF555555)
        !connected -> Color(0xFFFF9800)
        paused     -> Color(0xFF64B5F6)
        else       -> Color(0xFF4CAF50)
    }
    val statusText = when {
        !started   -> "Detenido"
        !connected -> "Conectando…"
        paused     -> "Pausado"
        else       -> "En vivo · $ip:$port"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Consola SBS", color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Exportar") },
                                onClick = {
                                    menuExpanded = false
                                    val content = visibleLines.joinToString("\n")
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, content)
                                        putExtra(Intent.EXTRA_SUBJECT, "Consola SBS $ip:$port")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Exportar consola SBS"))
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Barra de controles ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${visibleLines.size} / ${lines.size} líneas",
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    when {
                        !started       -> started = true
                        paused         -> paused = false
                        else           -> paused = true
                    }
                }) {
                    Text(
                        text = when {
                            !started -> "▶  Iniciar"
                            paused   -> "▶  Reanudar"
                            else     -> "⏸  Pausar"
                        },
                        color = when {
                            !started -> Color(0xFF81C784)
                            paused   -> Color(0xFF81C784)
                            else     -> Color(0xFFFFF176)
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(onClick = { lines.clear() }) {
                    Text(
                        text = "✕  Limpiar",
                        color = Color(0xFFEF9A9A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ── Filtros de tipo de mensaje ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161616))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MSG_TYPES.forEach { type ->
                    val active = activeFilters.value.contains(type.key)
                    FilterChip(
                        label = type.label,
                        color = type.color,
                        active = active,
                        onClick = {
                            activeFilters.value = if (active)
                                activeFilters.value - type.key
                            else
                                activeFilters.value + type.key
                        }
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF333333))

            // ── Consola ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
            ) {
                if (visibleLines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                !started        -> "Pulsa ▶ Iniciar para conectar"
                                !connected      -> "Conectando a $ip:$port…"
                                lines.isEmpty() -> "Esperando mensajes SBS…"
                                else            -> "Ningún mensaje coincide con los filtros activos"
                            },
                            color = Color(0xFF555555),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(visibleLines) { _, line ->
                            ConsoleLine(line)
                        }
                    }
                }

                if (paused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .background(Color(0xDD1A1A1A), shape = MaterialTheme.shapes.small)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "⏸  PAUSADO — buffer: ${lines.size} / $MAX_LINES líneas",
                            color = Color(0xFF64B5F6),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (active) color else Color(0xFF444444), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) color else Color(0xFF444444))
        )
        Text(
            text = label,
            color = if (active) color else Color(0xFF555555),
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ConsoleLine(line: String) {
    val parts = line.split(",")
    val color = when (parts.getOrNull(1)?.trim()) {
        "1"      -> Color(0xFF64B5F6)
        "3"      -> Color(0xFF81C784)
        "4"      -> Color(0xFFFFF176)
        "5", "6" -> Color(0xFFFFB74D)
        "2"      -> Color(0xFF80CBC4)
        else     -> Color(0xFF9E9E9E)
    }
    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = color,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}
