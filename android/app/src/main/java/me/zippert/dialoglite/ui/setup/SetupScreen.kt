package me.zippert.dialoglite.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.zippert.dialoglite.data.remote.BaseUrlInterceptor

/**
 * O endereco base e digitado pelo usuario: sao duas instancias distintas
 * (uma no ymir, outra no attila), uma por pessoa, e o mesmo APK atende as
 * duas. Nada de endereco embutido no binario.
 */
@Composable
fun SetupScreen(
    current: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(current) { mutableStateOf(current.orEmpty()) }
    val parsed = remember(value) { BaseUrlInterceptor.parse(value) }
    val valid = value.isNotBlank() && parsed != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Endereço do servidor", style = MaterialTheme.typography.headlineSmall)

        Text(
            "O app fala com a sua instância do Dia Log Lite por dentro da mesh " +
                "netbird. Não há login: quem controla o acesso é a mesh.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("URL base") },
            placeholder = { Text("https://dialog.zippert.me") },
            singleLine = true,
            isError = value.isNotBlank() && !valid,
            supportingText = {
                Text(
                    if (value.isNotBlank() && !valid) {
                        "Endereço inválido."
                    } else {
                        "Aceita host, host:porta ou URL completa. Sem esquema, assume http://."
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        if (valid) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vai chamar:", style = MaterialTheme.typography.labelMedium)
                    Text("${parsed}api/history", style = MaterialTheme.typography.bodySmall)
                    Text("${parsed}day/bulk_update", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(
            onClick = { onSave(value) },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar")
        }

        Text(
            "Fora da mesh o endereço fica inalcançável — isso é normal. As " +
                "edições ficam na fila e sobem sozinhas quando a rede voltar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
