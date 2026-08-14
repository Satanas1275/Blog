package studio.rocknite.blog.ui.mediaapps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import studio.rocknite.blog.network.MediaApp

/**
 * Gestion des apps suivies pour la détection MediaSession : package_name, nom affiché,
 * et la liste de messages (avec placeholders {name}/{subtitle}) parmi lesquels le site
 * en choisit un au hasard à chaque affichage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAppsScreen(
    apps: List<MediaApp>,
    onAdd: (packageName: String, label: String, templates: List<String>) -> Unit,
    onUpdate: (id: Long, packageName: String, label: String, templates: List<String>) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Apps suivies") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter une app")
            }
        },
    ) { padding ->
        if (apps.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune app suivie. Touche + pour en ajouter une.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(apps, key = { it.id }) { app ->
                    MediaAppCard(
                        app = app,
                        onUpdate = { packageName, label, templates -> onUpdate(app.id, packageName, label, templates) },
                        onDelete = { onDelete(app.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        MediaAppEditDialog(
            initialPackageName = "",
            initialLabel = "",
            initialTemplates = emptyList(),
            title = "Nouvelle app suivie",
            onConfirm = { packageName, label, templates ->
                onAdd(packageName, label, templates)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun MediaAppCard(
    app: MediaApp,
    onUpdate: (packageName: String, label: String, templates: List<String>) -> Unit,
    onDelete: () -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(app.label?.takeIf { it.isNotBlank() } ?: app.package_name, style = MaterialTheme.typography.titleMedium)
            Text(app.package_name, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(4.dp))
            if (app.templates.isEmpty()) {
                Text("Aucun message configuré (affichera juste le titre brut).", style = MaterialTheme.typography.bodySmall)
            } else {
                app.templates.forEach { t -> Text("• $t", style = MaterialTheme.typography.bodySmall) }
            }

            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showEditDialog = true }) { Text("Éditer") }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Retirer", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showEditDialog) {
        MediaAppEditDialog(
            initialPackageName = app.package_name,
            initialLabel = app.label ?: "",
            initialTemplates = app.templates,
            title = "Éditer l'app",
            onConfirm = { packageName, label, templates ->
                onUpdate(packageName, label, templates)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Retirer cette app ?") },
            text = { Text("Elle ne sera plus détectée par le service.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Retirer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun MediaAppEditDialog(
    initialPackageName: String,
    initialLabel: String,
    initialTemplates: List<String>,
    title: String,
    onConfirm: (packageName: String, label: String, templates: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var packageName by remember { mutableStateOf(initialPackageName) }
    var label by remember { mutableStateOf(initialLabel) }
    var templates by remember { mutableStateOf(initialTemplates) }
    var newTemplate by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package name") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nom affiché (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Messages (placeholders {name} et {subtitle})", style = MaterialTheme.typography.labelLarge)
                templates.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { templates = templates - t }) {
                            Icon(Icons.Filled.Close, contentDescription = "Retirer ce message")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTemplate,
                        onValueChange = { newTemplate = it },
                        placeholder = { Text("Écoute {name}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        if (newTemplate.isNotBlank()) {
                            templates = templates + newTemplate.trim()
                            newTemplate = ""
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Ajouter ce message")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(packageName.trim(), label.trim(), templates) },
                enabled = packageName.isNotBlank(),
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
