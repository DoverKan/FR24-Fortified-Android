package com.skydronex.fr24fortified

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.skydronex.fr24fortified.BuildConfig
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.skydronex.fr24fortified.data.AppState
import com.skydronex.fr24fortified.data.ConfigRepository
import com.skydronex.fr24fortified.data.WhatsNewRepository
import com.skydronex.fr24fortified.ui.about.AboutScreen
import com.skydronex.fr24fortified.ui.console.SbsConsoleScreen
import com.skydronex.fr24fortified.ui.home.HomeScreen
import com.skydronex.fr24fortified.ui.map.MapScreen
import com.skydronex.fr24fortified.ui.setup.SetupScreen
import com.skydronex.fr24fortified.ui.theme.FR24FortifiedTheme
import com.skydronex.fr24fortified.ui.whatsnew.WhatsNewDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository    by lazy { ConfigRepository(applicationContext) }
    private val whatsNewRepo  by lazy { WhatsNewRepository(applicationContext, repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FR24FortifiedTheme {
                val appState    by repository.appState.collectAsState(initial = AppState.Loading)
                val newEntries  by whatsNewRepo.newEntries(BuildConfig.VERSION_CODE).collectAsState(initial = emptyList())
                var editMode    by rememberSaveable { mutableStateOf(false) }
                var showAbout   by rememberSaveable { mutableStateOf(false) }
                var showConsole by rememberSaveable { mutableStateOf(false) }
                var showMap     by rememberSaveable { mutableStateOf(false) }

                if (newEntries.isNotEmpty()) {
                    WhatsNewDialog(
                        entries   = newEntries,
                        onDismiss = {
                            lifecycleScope.launch {
                                whatsNewRepo.markSeen(BuildConfig.VERSION_CODE)
                            }
                        }
                    )
                }

                when {
                    appState == AppState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    showAbout -> AboutScreen(onBack = { showAbout = false })

                    showConsole && appState is AppState.Ready -> SbsConsoleScreen(
                        ip   = (appState as AppState.Ready).config.ipAddress,
                        port = (appState as AppState.Ready).config.consolePort,
                        onBack = { showConsole = false }
                    )

                    showMap -> MapScreen(
                        config       = (appState as AppState.Ready).config,
                        onBack       = { showMap = false },
                        onEditConfig = { showMap = false; editMode = true },
                        onAbout      = { showAbout = true },
                        onRefresh    = { /* Por ahora no hace nada en el mapa */ }
                    )

                    appState == AppState.NeedsSetup || editMode -> SetupScreen(
                        initialConfig = (appState as? AppState.Ready)?.config,
                        onSave = { config ->
                            lifecycleScope.launch { repository.saveConfig(config) }
                            editMode = false
                        },
                        onCancel = if (editMode) ({ editMode = false }) else null
                    )

                    appState is AppState.Ready -> HomeScreen(
                        config       = (appState as AppState.Ready).config,
                        onEditConfig = { editMode = true },
                        onAbout      = { showAbout = true },
                        onConsole    = { showConsole = true },
                        onMap        = { showMap = true }
                    )
                }
            }
        }
    }
}
