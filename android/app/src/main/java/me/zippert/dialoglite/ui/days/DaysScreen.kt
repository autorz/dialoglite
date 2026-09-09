package me.zippert.dialoglite.ui.days

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.zippert.dialoglite.data.DayUi
import me.zippert.dialoglite.ui.edit.DayEditSheet
import me.zippert.dialoglite.ui.theme.BalanceColors
import me.zippert.dialoglite.util.DateFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaysScreen(
    state: DaysUiState,
    viewModel: DaysViewModel,
    onOpenSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    val editingDay = state.days.firstOrNull { it.date == editing }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dia Log Lite", style = MaterialTheme.typography.titleMedium)
                        BalanceLine(state)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Endereço do servidor")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.sync.inProgress,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.pendingCount > 0 || state.blockedCount > 0 || state.sync.reachable == false) {
                    item { StatusBanner(state) }
                }
                if (state.days.isEmpty()) {
                    item { EmptyState(state) }
                }
                items(state.days, key = { it.date }) { day ->
                    DayRow(day = day, onClick = { editing = day.date })
                    HorizontalDivider()
                }
            }
        }
    }

    editingDay?.let { day ->
        DayEditSheet(
            day = day,
            onDismiss = { editing = null },
            onSave = { notes, periods ->
                viewModel.saveEdit(day.date, notes, periods)
                editing = null
            },
            onDiscardPending = {
                viewModel.discardEdit(day.date)
                editing = null
            },
        )
    }
}

@Composable
private fun BalanceLine(state: DaysUiState) {
    val balance = state.balancePretty ?: "—"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Saldo $balance",
            style = MaterialTheme.typography.bodyMedium,
            color = balanceColor(balance),
        )
        // O saldo vem calculado do servidor. Com edicao na fila, o numero na
        // tela ainda e o de antes da edicao — dizer isso e obrigacao da UI.
        if (state.balanceIsStale) {
            Text(
                "· não inclui edições pendentes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusBanner(state: DaysUiState) {
    val container = when {
        state.blockedCount > 0 -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.sync.inProgress) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.CloudOff, contentDescription = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (state.pendingCount > 0) {
                    Text(
                        "${state.pendingCount} ${plural(state.pendingCount, "edição", "edições")} na fila",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (state.blockedCount > 0) {
                    Text(
                        "${state.blockedCount} ${plural(state.blockedCount, "edição recusada", "edições recusadas")} pelo servidor",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    state.sync.lastError
                        ?: "Sobem sozinhas quando o servidor estiver alcançável (dentro da mesh).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(state: DaysUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Nenhum dia em cache", style = MaterialTheme.typography.titleMedium)
        Text(
            if (state.sync.reachable == false) {
                "O servidor está inalcançável. Conecte-se à mesh netbird e sincronize."
            } else {
                "Puxe para baixo para sincronizar."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DayRow(day: DayUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(56.dp)) {
            Text(DateFormats.prettyDayMonth(day.date), style = MaterialTheme.typography.titleSmall)
            Text(
                DateFormats.weekdayShort(day.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                day.periods.takeIf { it.isNotEmpty() }
                    ?.joinToString("  ") { "${it.entry}–${it.exit ?: "…"}" }
                    ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (day.hasPendingEdit) {
                    Badge(if (day.pendingBlocked) "recusada" else "pendente", day.pendingBlocked)
                }
                if (day.isHoliday) Badge("feriado", false)
                if (day.isWeekend) Badge("fds", false)
                if (day.requiresAdvancedEditing) Badge("${day.serverPeriods.size} períodos", true)
            }
            if (day.notes.isNotBlank()) {
                Text(
                    day.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                day.dailyDeltaPretty.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = balanceColor(day.dailyDeltaPretty),
            )
            Text(
                day.balancePretty.ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Badge(text: String, warning: Boolean) {
    val bg = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}

/** O servidor formata com sinal (`+01:30` / `-00:45`); a cor segue o sinal. */
@Composable
private fun balanceColor(pretty: String): Color = when {
    pretty.startsWith("-") -> BalanceColors.negative
    pretty.startsWith("+") -> BalanceColors.positive
    else -> MaterialTheme.colorScheme.onSurface
}

private fun plural(count: Int, one: String, many: String) = if (count == 1) one else many

