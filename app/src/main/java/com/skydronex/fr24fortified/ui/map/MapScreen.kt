package com.skydronex.fr24fortified.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.content.ContextCompat
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.rasterLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.rasterSource
import com.skydronex.fr24fortified.data.AppConfig
import com.skydronex.fr24fortified.data.ConnectionChecker
import com.skydronex.fr24fortified.data.ConnectionResult
import com.skydronex.fr24fortified.data.DeviceType
import com.skydronex.fr24fortified.data.map.RainViewerRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.time.Duration.Companion.seconds

private sealed class ConnectionStatus {
    data object Checking : ConnectionStatus()
    data object Connected : ConnectionStatus()
    data object Disconnected : ConnectionStatus()
}

private val ColorOrange = Color(0xFFFBBF24)
private val ColorGreen  = Color(0xFF34D399)
private const val RainSourceId = "rainviewer-source"
private const val RainLayerId = "rainviewer-layer"
private const val RainMaxZoom = 7

private data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    config: AppConfig,
    onBack: () -> Unit,
    onEditConfig: () -> Unit,
    onAbout: () -> Unit,
    onRefresh: () -> Unit
) {
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var isFollowingUser by rememberSaveable { mutableStateOf(false) }
    var isRainLayerVisible by rememberSaveable { mutableStateOf(false) }
    var rainTemplate by remember { mutableStateOf<String?>(null) }
    val rainRepo = remember { RainViewerRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userLocation = rememberUserLocation(isFollowingUser = isFollowingUser)
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            isFollowingUser = true
        } else {
            Toast.makeText(
                context,
                "Debes conceder permiso de ubicación para centrar el mapa",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onToggleFollowUser() {
        if (isFollowingUser) {
            isFollowingUser = false
            return
        }

        if (hasLocationPermission(context)) {
            isFollowingUser = true
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun onToggleRainLayer() {
        if (isRainLayerVisible) {
            isRainLayerVisible = false
            return
        }
        if (rainTemplate != null) {
            isRainLayerVisible = true
            return
        }

        scope.launch {
            val template = rainRepo.latestRadarTileTemplate()
            if (template == null) {
                Toast.makeText(
                    context,
                    "No se pudo cargar la capa de lluvia",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            rainTemplate = template
            isRainLayerVisible = true
        }
    }

    BackHandler {
        if (isFullscreen) isFullscreen = false else onBack()
    }
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

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapContent(
                config = config,
                isFollowingUser = isFollowingUser,
                userLocation = userLocation,
                isRainLayerVisible = isRainLayerVisible,
                rainTemplate = rainTemplate
            )
            IconButton(
                onClick = ::onToggleRainLayer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = if (isRainLayerVisible) "Ocultar capa de lluvia" else "Mostrar capa de lluvia",
                    tint = if (isRainLayerVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = ::onToggleFollowUser,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = if (isFollowingUser) "Desactivar seguimiento de ubicación" else "Centrar en mi ubicación",
                    tint = if (isFollowingUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = { isFullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .zIndex(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        shape = CircleShape
                    )
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Salir de pantalla completa")
            }
        }
    } else {
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
                        IconButton(onClick = ::onToggleRainLayer) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = if (isRainLayerVisible) "Ocultar capa de lluvia" else "Mostrar capa de lluvia",
                                tint = if (isRainLayerVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = ::onToggleFollowUser) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = if (isFollowingUser) "Desactivar seguimiento de ubicación" else "Centrar en mi ubicación",
                                tint = if (isFollowingUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Pantalla completa")
                        }
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
                MapContent(
                    config = config,
                    isFollowingUser = isFollowingUser,
                    userLocation = userLocation,
                    isRainLayerVisible = isRainLayerVisible,
                    rainTemplate = rainTemplate
                )
            }
        }
    }
}

@Composable
private fun MapContent(
    config: AppConfig,
    isFollowingUser: Boolean,
    userLocation: UserLocation?,
    isRainLayerVisible: Boolean,
    rainTemplate: String?
) {
    if (config.mapboxToken.isNotBlank()) {
        MapboxMapView(
            token = config.mapboxToken,
            isFollowingUser = isFollowingUser,
            userLocation = userLocation,
            isRainLayerVisible = isRainLayerVisible,
            rainTemplate = rainTemplate
        )
    } else {
        OsmMapView(
            isFollowingUser = isFollowingUser,
            userLocation = userLocation,
            isRainLayerVisible = isRainLayerVisible,
            rainTemplate = rainTemplate
        )
    }
}

@OptIn(MapboxExperimental::class)
@Composable
private fun MapboxMapView(
    token: String,
    isFollowingUser: Boolean,
    userLocation: UserLocation?,
    isRainLayerVisible: Boolean,
    rainTemplate: String?
) {
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

    LaunchedEffect(isFollowingUser, userLocation) {
        val location = userLocation ?: return@LaunchedEffect
        if (!isFollowingUser) return@LaunchedEffect

        mapViewportState.flyTo(
            CameraOptions.Builder()
                .center(Point.fromLngLat(location.longitude, location.latitude))
                .zoom(13.5)
                .build()
        )
    }

    var rainApplied by remember { mutableStateOf(false) }

    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = mapViewportState
    ) {
        if (isRainLayerVisible && !rainTemplate.isNullOrBlank()) {
            MapEffect(rainTemplate) { mapView ->
                mapView.mapboxMap.getStyle { style ->
                    if (!style.styleSourceExists(RainSourceId)) {
                        style.addSource(
                            rasterSource(RainSourceId) {
                                tileSize(256L)
                                tiles(listOf(rainTemplate))
                                maxzoom(RainMaxZoom.toLong())
                            }
                        )
                    }
                    if (!style.styleLayerExists(RainLayerId)) {
                        style.addLayer(
                            rasterLayer(RainLayerId, RainSourceId) {
                                rasterOpacity(0.65)
                            }
                        )
                    }
                    rainApplied = true
                }
            }
        } else if (rainApplied) {
            MapEffect("remove-rain") { mapView ->
                mapView.mapboxMap.getStyle { style ->
                    if (style.styleLayerExists(RainLayerId)) {
                        style.removeStyleLayer(RainLayerId)
                    }
                    if (style.styleSourceExists(RainSourceId)) {
                        style.removeStyleSource(RainSourceId)
                    }
                    rainApplied = false
                }
            }
        }
    }
}

@Composable
private fun OsmMapView(
    isFollowingUser: Boolean,
    userLocation: UserLocation?,
    isRainLayerVisible: Boolean,
    rainTemplate: String?
) {
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
    var rainOverlay by remember { mutableStateOf<TilesOverlay?>(null) }

    LaunchedEffect(isFollowingUser, userLocation) {
        val location = userLocation ?: return@LaunchedEffect
        if (!isFollowingUser) return@LaunchedEffect
        mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
        if (mapView.zoomLevelDouble < 13.5) {
            mapView.controller.setZoom(13.5)
        }
    }

    LaunchedEffect(isRainLayerVisible, rainTemplate) {
        val current = rainOverlay
        if (!isRainLayerVisible || rainTemplate.isNullOrBlank()) {
            if (current != null) {
                mapView.overlays.remove(current)
                rainOverlay = null
                mapView.invalidate()
            }
            return@LaunchedEffect
        }

        val newOverlay = createRainTilesOverlay(context, rainTemplate)
        if (current != null) {
            mapView.overlays.remove(current)
        }
        mapView.overlays.add(newOverlay)
        rainOverlay = newOverlay
        mapView.invalidate()
    }

    LifecycleResumeEffect(mapView) {
        mapView.onResume()
        onPauseOrDispose {
            rainOverlay?.let { mapView.overlays.remove(it) }
            mapView.onPause()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

private fun hasLocationPermission(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return hasFine || hasCoarse
}

@Composable
private fun rememberUserLocation(isFollowingUser: Boolean): UserLocation? {
    val context = LocalContext.current
    var userLocation by remember { mutableStateOf<UserLocation?>(null) }

    androidx.compose.runtime.DisposableEffect(context, isFollowingUser) {
        if (!isFollowingUser || !hasLocationPermission(context)) {
            onDispose { }
        } else {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = LocationListener { location: Location ->
                userLocation = UserLocation(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            }

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }

            providers.forEach { provider ->
                runCatching {
                    locationManager.getLastKnownLocation(provider)
                }.getOrNull()?.let { lastLocation ->
                    if (userLocation == null) {
                        userLocation = UserLocation(
                            latitude = lastLocation.latitude,
                            longitude = lastLocation.longitude
                        )
                    }
                }

                runCatching {
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L,
                        2f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            }

            onDispose {
                runCatching { locationManager.removeUpdates(listener) }
            }
        }
    }

    return userLocation
}

private fun createRainTilesOverlay(context: Context, template: String): TilesOverlay {
    val source = object : OnlineTileSourceBase(
        "RainViewer",
        0,
        RainMaxZoom,
        256,
        ".png",
        arrayOf("")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex).coerceAtMost(RainMaxZoom)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return template
                .replace("{z}", z.toString())
                .replace("{x}", x.toString())
                .replace("{y}", y.toString())
        }
    }

    val provider = MapTileProviderBasic(context, source)
    return TilesOverlay(provider, context).apply {
        setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT)
        setLoadingLineColor(android.graphics.Color.TRANSPARENT)
    }
}
