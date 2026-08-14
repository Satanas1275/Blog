package studio.rocknite.blog.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Chips de statut rapide ("En voiture", "Dodo"...) : tap = envoi immédiat, pas de post permanent.
 * Éditable : "+" pour ajouter un statut custom, la petite croix retire un chip de la liste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStatusBar(
    labels: List<String>,
    onPick: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column {
        Text("Statut rapide", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels) { label ->
                InputChip(
                    selected = false,
                    onClick = { onPick(label) },
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Retirer",
                            modifier = Modifier.size(16.dp).clickableSimple { onRemove(label) },
                        )
                    },
                )
            }
            item {
                AssistChip(
                    onClick = { showAddDialog = true },
                    label = { Text("Ajouter") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        }
    }

    if (showAddDialog) {
        var newLabel by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nouveau statut rapide") },
            text = {
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    placeholder = { Text("ex: En cours de stream") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newLabel.isNotBlank()) onAdd(newLabel.trim())
                        showAddDialog = false
                    },
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Annuler") }
            },
        )
    }
}

private fun Modifier.clickableSimple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = MutableInteractionSource(),
        indication = null,
        onClick = onClick,
    )

