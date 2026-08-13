package studio.rocknite.blog.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Écran de config : URL du serveur + token d'API, stockés chiffrés (TokenStore).
 * Les valeurs initiales viennent de currentServerUrl/currentToken, la sauvegarde se fait via onSave.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentServerUrl: String,
    currentToken: String?,
    onSave: (serverUrl: String, token: String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf(currentServerUrl) }
    var token by remember { mutableStateOf(currentToken ?: "") }
    var showToken by remember { mutableStateOf(false) }
    var justSaved by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Configuration") }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Ces infos ne quittent jamais le téléphone en clair : elles sont stockées chiffrées.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; justSaved = false },
                label = { Text("URL du serveur") },
                placeholder = { Text("https://bastian-riot.rocknite-studio.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = token,
                onValueChange = { token = it; justSaved = false },
                label = { Text("Token d'API") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val cleanUrl = serverUrl.trim().trimEnd('/')
                    onSave(cleanUrl, token.trim())
                    justSaved = true
                },
                enabled = serverUrl.isNotBlank() && token.isNotBlank(),
            ) {
                Text("Enregistrer")
            }

            if (justSaved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Enregistré", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
