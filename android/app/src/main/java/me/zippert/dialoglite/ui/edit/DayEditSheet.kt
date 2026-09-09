package me.zippert.dialoglite.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.zippert.dialoglite.data.DayUi
import me.zippert.dialoglite.data.local.PeriodValue
import me.zippert.dialoglite.util.DateFormats
import me.zippert.dialoglite.util.TimeFormats

private data class SlotState(var entry: String, var exit: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditSheet(
    day: DayUi,
    onDismiss: () -> Unit,
    onSave: (notes: String?, periods: List<PeriodValue>?) -> Unit,
    onDiscardPending: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // A edicao rapida cobre no maximo 2 periodos — e o que o servidor aceita
    // em /day/bulk_update. Dia com mais periodos entra em modo somente leitura.
    val initial = remember(day.date, day.periods) {
        List(2) { index ->
            val p = day.periods.getOrNull(index)
            SlotState(entry = p?.entry.orEmpty(), exit = p?.exit.orEmpty())
        }
    }

    var entry1 by remember(day.date) { mutableStateOf(initial[0].entry) }
    var exit1 by remember(day.date) { mutableStateOf(initial[0].exit) }
    var entry2 by remember(day.date) { mutableStateOf(initial[1].entry) }
    var exit2 by remember(day.date) { mutableStateOf(initial[1].exit) }
    var notes by remember(day.date) { mutableStateOf(day.notes) }

    val fields = listOf(entry1, exit1, entry2, exit2)
    val invalid = fields.any { it.isNotBlank() && !TimeFormats.isValidHourMinute(it) }
    // Saida sem entrada nao forma periodo: o servidor pula pela entrada vazia
    // e o horario de saida sumiria em silencio.
    val orphanExit = (exit1.isNotBlank() && entry1.isBlank()) || (exit2.isNotBlank() && entry2.isBlank())
    val canSave = !invalid && !orphanExit && !day.requiresAdvancedEditing

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${DateFormats.weekdayShort(day.date)} ${DateFormats.prettyDayMonth(day.date)}",
                style = MaterialTheme.typography.headlineSmall,
            )

            if (day.hasPendingEdit) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (day.pendingBlocked) "Edição recusada pelo servidor" else "Edição pendente de envio",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        day.pendingError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "As horas e o saldo abaixo ainda são os do servidor — ele é " +
                                "quem calcula, e só recalcula depois que a edição subir.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onDiscardPending) { Text("Descartar edição local") }
                    }
                }
            }

            if (day.requiresAdvancedEditing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Dia com ${day.serverPeriods.size} períodos", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "A edição rápida só trabalha com até 2 períodos — é o que o " +
                                "servidor aceita. Use a edição avançada na web para este dia.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        day.serverPeriods.forEach { p ->
                            Text("• ${p.entry} — ${p.exit ?: "em aberto"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                TimeSlotRow(
                    label = "1º período",
                    entry = entry1,
                    exit = exit1,
                    onEntry = { entry1 = it },
                    onExit = { exit1 = it },
                )
                TimeSlotRow(
                    label = "2º período",
                    entry = entry2,
                    exit = exit2,
                    onEntry = { entry2 = it },
                    onExit = { exit2 = it },
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Observação") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (invalid) {
                Text(
                    "Horário inválido. Use HH:MM (24h).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (orphanExit) {
                Text(
                    "Um período com saída precisa de entrada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(
                    onClick = {
                        val periods = buildPeriods(entry1, exit1, entry2, exit2)
                        val notesChanged = notes != day.notes
                        val periodsChanged = periods != day.serverPeriods

                        onSave(
                            // null = "nao mexi nisso"; o campo sai do payload e
                            // o servidor preserva o valor que ja tinha.
                            if (notesChanged) notes else null,
                            // Idem pros horarios — e aqui isso importa mais:
                            // `entries: []` no servidor APAGA os periodos do dia.
                            if (periodsChanged && !day.requiresAdvancedEditing) periods else null,
                        )
                    },
                    enabled = canSave,
                ) {
                    Text("Salvar")
                }
            }
        }
    }
}

@Composable
private fun TimeSlotRow(
    label: String,
    entry: String,
    exit: String,
    onEntry: (String) -> Unit,
    onExit: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = entry,
                onValueChange = { onEntry(sanitizeTimeInput(it)) },
                label = { Text("Entrada") },
                placeholder = { Text("09:00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = exit,
                onValueChange = { onExit(sanitizeTimeInput(it)) },
                label = { Text("Saída") },
                placeholder = { Text("12:00") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Mascara leve `HH:MM`: so digito, dois-pontos automatico, no maximo 5 chars. */
private fun sanitizeTimeInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(4)
    return when {
        digits.isEmpty() -> ""
        digits.length <= 2 -> digits
        else -> "${digits.substring(0, 2)}:${digits.substring(2)}"
    }
}

/**
 * Monta a lista de periodos que vai pro servidor. Entrada vazia descarta o
 * slot inteiro — e o mesmo criterio de `core.update_day_periods`
 * (`if not entry_str: continue`).
 */
private fun buildPeriods(entry1: String, exit1: String, entry2: String, exit2: String): List<PeriodValue> =
    listOf(entry1 to exit1, entry2 to exit2)
        .filter { (entry, _) -> entry.isNotBlank() }
        .map { (entry, exit) -> PeriodValue(entry = entry, exit = exit.ifBlank { null }) }
