package com.skydronex.fr24fortified.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skydronex.fr24fortified.data.AppConfig
import com.skydronex.fr24fortified.data.ConnectionChecker
import com.skydronex.fr24fortified.data.ConnectionResult
import com.skydronex.fr24fortified.data.DeviceType
import com.skydronex.fr24fortified.data.monitor.MonitorRepository
import com.skydronex.fr24fortified.data.sbs.SbsRepository
import com.skydronex.fr24fortified.ui.home.box.BoxHomeContent
import com.skydronex.fr24fortified.ui.home.feeder.FeederHomeContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
fun HomeScreen(config: AppConfig, onEditConfig: () -> Unit, onAbout: () -> Unit, onConsole: () -> Unit, onMap: () -> Unit) {
    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val scope        = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var connStatus   by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Checking) }
    var refreshKey   by remember { mutableStateOf(0) }

    val sbsRepo = remember(config.ipAddress, config.consolePort) {
        SbsRepository(config.ipAddress, config.consolePort)
    }
    LaunchedEffect(sbsRepo) { sbsRepo.start() }

    val monitorRepo = remember(config.ipAddress, config.feederPort) {
        MonitorRepository(config.ipAddress, config.feederPort)
    }
    LaunchedEffect(monitorRepo) { monitorRepo.start() }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 28.dp)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val iconBitmap = remember {
                        val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.skydronex.fr24fortified.R.mipmap.ic_launcher)!!
                        val bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, 192, 192)
                        drawable.draw(canvas)
                        bmp.asImageBitmap()
                    }
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "FR24 Fortified",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    config.deviceType.name.lowercase().replaceFirstChar { it.uppercase() } +
                        " · " + config.ipAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    label = { Text("Inicio") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF34D399)) },
                    label = { Text("Mapa") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onMap() }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFBBF24)) },
                    label = { Text("Consola SBS") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onConsole() }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    label = { Text("Acerca de") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() }; onAbout() }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FR24 Fortified")
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir menú")
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
                                    onClick = { menuExpanded = false; refreshKey++ }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (config.deviceType) {
                    DeviceType.BOX    -> BoxHomeContent(ip = config.ipAddress, sbsRepo = sbsRepo, refreshKey = refreshKey)
                    DeviceType.FEEDER -> FeederHomeContent(sbsRepo = sbsRepo, monitorRepo = monitorRepo)
                }
            }
        }
    }
}
