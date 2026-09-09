package me.zippert.dialoglite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.zippert.dialoglite.sync.SyncScheduler
import me.zippert.dialoglite.ui.days.DaysScreen
import me.zippert.dialoglite.ui.days.DaysViewModel
import me.zippert.dialoglite.ui.setup.SetupScreen
import me.zippert.dialoglite.ui.theme.DiaLogTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DiaLogTheme {
                Root()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Abrir o app e um bom momento pra tentar: se estiver fora da mesh,
        // o worker simplesmente reagenda.
        SyncScheduler.syncNow(this)
    }
}

@Composable
private fun Root() {
    val viewModel: DaysViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    if (state.needsSetup || showSettings) {
        SetupScreen(
            current = state.baseUrl,
            onSave = {
                viewModel.saveBaseUrl(it)
                showSettings = false
            },
        )
    } else {
        DaysScreen(
            state = state,
            viewModel = viewModel,
            onOpenSettings = { showSettings = true },
        )
    }
}
