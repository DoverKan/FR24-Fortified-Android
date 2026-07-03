package com.skydronex.fr24fortified.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skydronex.fr24fortified.data.AppConfig
import com.skydronex.fr24fortified.data.ConnectionChecker
import com.skydronex.fr24fortified.data.ConnectionResult
import com.skydronex.fr24fortified.data.DeviceType
import com.skydronex.fr24fortified.data.Validation
import kotlinx.coroutines.launch

private sealed class CheckState {
    data object Idle : CheckState()
    data object Checking : CheckState()
    data object Ok : CheckState()
    data class Error(val message: String) : CheckState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSave: (AppConfig) -> Unit,
    initialConfig: AppConfig? = null,
    onCancel: (() -> Unit)? = null
) {
    val isEditing = initialConfig != null
    val scope = rememberCoroutineScope()

    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    var ip by remember { mutableStateOf(initialConfig?.ipAddress ?: "") }
    var deviceType by remember { mutableStateOf(initialConfig?.deviceType ?: DeviceType.FEEDER) }
    var consolePort by remember { mutableStateOf(initialConfig?.consolePort?.toString() ?: "30003") }
    var feederPort by remember { mutableStateOf(initialConfig?.feederPort?.toString() ?: "8754") }
    var mapboxToken by remember { mutableStateOf(initialConfig?.mapboxToken ?: "") }

    var ipError by remember { mutableStateOf<String?>(null) }
    var consolePortError by remember { mutableStateOf<String?>(null) }
    var feederPortError by remember { mutableStateOf<String?>(null) }
    var checkState by remember { mutableStateOf<CheckState>(CheckState.Idle) }

    fun resetFieldErrors() {
        ipError = null
        consolePortError = null
        feederPortError = null
    }

    fun validateFields(): Boolean {
        ipError = Validation.validateIp(ip)
        consolePortError = Validation.validatePort(consolePort, "Puerto consola SBS")
        feederPortError = if (deviceType == DeviceType.FEEDER) {
            Validation.validatePort(feederPort, "Puerto feeder")
        } else null
        return ipError == null && consolePortError == null && feederPortError == null
    }

    fun checkConnection() {
        if (!validateFields()) return
        val cPort = consolePort.trim().toInt()
        val fPort = feederPort.trim().toIntOrNull() ?: 8754
        scope.launch {
            checkState = CheckState.Checking
            checkState = when (val result = ConnectionChecker.check(ip.trim(), deviceType, cPort, fPort)) {
                is ConnectionResult.Success -> CheckState.Ok
                is ConnectionResult.Failure -> CheckState.Error(result.message)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isEditing) {
                TopAppBar(
                    title = { Text("Editar configuración") },
                    navigationIcon = {
                        IconButton(onClick = { onCancel?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = if (isEditing) 16.dp else 48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (!isEditing) {
                Text("Configuración inicial", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Introduce los datos de tu dispositivo FR24",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))
            }

            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it; resetFieldErrors(); checkState = CheckState.Idle },
                label = { Text("Dirección IP") },
                placeholder = { Text("192.168.1.100") },
                isError = ipError != null,
                supportingText = { Text(ipError ?: "Formato: 192.168.1.100") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            Text("Tipo de dispositivo", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeviceType.entries.forEach { type ->
                    FilterChip(
                        selected = deviceType == type,
                        onClick = { deviceType = type; checkState = CheckState.Idle },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = consolePort,
                onValueChange = { consolePort = it; consolePortError = null; checkState = CheckState.Idle },
                label = { Text("Puerto consola SBS") },
                isError = consolePortError != null,
                supportingText = { Text(consolePortError ?: "Por defecto: 30003") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (deviceType == DeviceType.FEEDER) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = feederPort,
                    onValueChange = { feederPort = it; feederPortError = null; checkState = CheckState.Idle },
                    label = { Text("Puerto servicio feeder") },
                    isError = feederPortError != null,
                    supportingText = { Text(feederPortError ?: "Por defecto: 8754") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = mapboxToken,
                onValueChange = { mapboxToken = it },
                label = { Text("Token Mapbox (opcional)") },
                placeholder = { Text("pk.eyJ1Ijo...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // Resultado de la comprobación
            when (val state = checkState) {
                is CheckState.Ok -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Conexión establecida correctamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                is CheckState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(12.dp))
                }
                else -> {}
            }

            OutlinedButton(
                onClick = ::checkConnection,
                enabled = checkState !is CheckState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (checkState is CheckState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Comprobando...")
                } else {
                    Text("Comprobar conexión")
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!validateFields()) return@Button
                    val cPort = consolePort.trim().toInt()
                    val fPort = feederPort.trim().toIntOrNull() ?: 8754
                    scope.launch {
                        checkState = CheckState.Checking
                        val result = ConnectionChecker.check(ip.trim(), deviceType, cPort, fPort)
                        when (result) {
                            is ConnectionResult.Success -> onSave(
                                AppConfig(
                                    ipAddress = ip.trim(),
                                    deviceType = deviceType,
                                    consolePort = cPort,
                                    feederPort = fPort,
                                    mapboxToken = mapboxToken.trim()
                                )
                            )
                            is ConnectionResult.Failure -> checkState = CheckState.Error(result.message)
                        }
                    }
                },
                enabled = checkState !is CheckState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (checkState is CheckState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Guardando...")
                } else {
                    Text(if (isEditing) "Guardar cambios" else "Guardar configuración")
                }
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onCancel?.invoke() },
                    enabled = checkState !is CheckState.Checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}
