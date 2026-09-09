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
import androidx.compose.material3.CardDefaults
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
import me.zippert.dialoglite.data.remote.BaseUrlValidation

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
    val validation = remember(value) { BaseUrlInterceptor.validate(value) }
    val valid = validation is BaseUrlValidation.Valid

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
            placeholder = { Text("https://ponto.exemplo  ·  http://100.70.0.1") },
            singleLine = true,
            isError = validation is BaseUrlValidation.Malformed ||
                validation is BaseUrlValidation.CleartextNotAllowed,
            supportingText = {
                Text(
                    when (validation) {
                        is BaseUrlValidation.Malformed -> "Endereço inválido."
                        is BaseUrlValidation.CleartextNotAllowed ->
                            "\"${validation.host}\" não é um IP privado, então precisa ser https://."
                        else ->
                            "Aceita https://<host> ou http://<ip da mesh>[:porta]. " +
                                "Sem esquema, assume http://."
                    }
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        (validation as? BaseUrlValidation.Valid)?.let { ok ->
            if (ok.cleartext) {
                // O usuario precisa saber que este endereco especifico nao tem
                // TLS: a protecao vem so do tunel WireGuard da mesh.
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sem TLS", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Este endereço usa HTTP em claro. O tráfego só fica protegido " +
                                "por dentro do túnel da mesh netbird — em qualquer outra rede, " +
                                "ele vai exposto.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vai chamar:", style = MaterialTheme.typography.labelMedium)
                    Text("${ok.url}api/history", style = MaterialTheme.typography.bodySmall)
                    Text("${ok.url}day/bulk_update", style = MaterialTheme.typography.bodySmall)
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
