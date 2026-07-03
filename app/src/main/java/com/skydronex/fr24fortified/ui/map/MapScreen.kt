package com.skydronex.fr24fortified.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.skydronex.fr24fortified.data.AppConfig
import com.skydronex.fr24fortified.data.ConnectionChecker
import com.skydronex.fr24fortified.data.ConnectionResult
import com.skydronex.fr24fortified.data.DeviceType
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.time.Duration.Companion.seconds

private sealed class ConnectionStatus {
    data object Checking : ConnectionStatus()
    data object Connected : ConnectionStatus()
    data object Disconnected : ConnectionStatus()
}

private val ColorOrange = Color(0xFFFBBF24)
private val ColorGreen  = Color(0xFF34D399)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    config: AppConfig,
    onBack: () -> Unit,
    onEditConfig: () -> Unit,
    onAbout: () -> Unit,
    onRefresh: () -> Unit
) {
    BackHandler { onBack() }
    var menuExpanded by remember { mutableStateOf(false) }
    var connStatus   by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Checking) }

    LaunchedEffect(config) {
        while (true) {
            connStatus = ConnectionStatus.Checking
            connStatus = when (ConnectionChecker.check(config.ipAddress, config.deviceType, config.consolePort, config.feederPort)) {
                is ConnectionResult.Success -> ConnectionStatus.Connected
                is ConnectionResult.Failure -> ConnectionStatus.Disconnected
            }
            delay(30.seconds)
        }
    }

    val statusColor = when (connStatus) {
        ConnectionStatus.Checking     -> ColorOrange
        ConnectionStatus.Connected    -> ColorGreen
        ConnectionStatus.Disconnected -> MaterialTheme.colorScheme.error
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mapa")
                        Spacer(Modifier.width(8.dp))
                        val isFeeder = config.deviceType == DeviceType.FEEDER
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isFeeder) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    config.deviceType.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFeeder) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Actualizar") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = { menuExpanded = false; onRefresh() }
                            )
                            DropdownMenuItem(
                                text = { Text("Configuración") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { menuExpanded = false; onEditConfig() }
                            )
                            DropdownMenuItem(
                                text = { Text("Acerca de") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { menuExpanded = false; onAbout() }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (config.mapboxToken.isNotBlank()) {
                MapboxMapView(token = config.mapboxToken)
            } else {
                OsmMapView()
            }
        }
    }
}

@OptIn(MapboxExperimental::class)
@Composable
fun MapboxMapView(token: String) {
    // Configurar el token público para Mapbox v11 antes de crear cualquier componente
    remember(token) {
        com.mapbox.common.MapboxOptions.accessToken = token
        token
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(-3.70379, 40.41678)) // Madrid por defecto
            zoom(5.0)
        }
    }

    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = mapViewportState
    )
}

@Composable
fun OsmMapView() {
    val context = LocalContext.current
    val mapView = remember {
        // Configuración requerida para osmdroid
        org.osmdroid.config.Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(40.41678, -3.70379)) // Madrid
        }
    }

    LifecycleResumeEffect(mapView) {
        mapView.onResume()
        onPauseOrDispose {
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}
