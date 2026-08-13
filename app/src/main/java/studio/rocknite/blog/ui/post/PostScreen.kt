package studio.rocknite.blog.ui.post

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Écran de publication : texte + photos + date/heure de publication.
 * Par défaut la date/heure = maintenant (l'utilisateur peut programmer un post plus tard).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    isPublishing: Boolean,
    lastError: String?,
    onPublish: (content: String, images: List<Uri>, publishedAt: Date) -> Unit,
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var publishedAt by remember { mutableStateOf(Date()) }

    // Quand une publication en cours se termine sans erreur, on vide le formulaire.
    var wasPublishing by remember { mutableStateOf(false) }
    LaunchedEffect(isPublishing, lastError) {
        if (wasPublishing && !isPublishing && lastError == null) {
            content = ""
            images = emptyList()
            publishedAt = Date()
        }
        wasPublishing = isPublishing
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> images = uris }

    val calendar = remember { Calendar.getInstance() }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.FRANCE) }

    fun openDateTimePicker() {
        calendar.time = publishedAt
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute)
                        publishedAt = calendar.time
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nouveau post") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (isPublishing) "Publication…" else "Publier") },
                icon = { Icon(Icons.Filled.Send, contentDescription = null) },
                onClick = {
                    if (content.isNotBlank() && !isPublishing) {
                        onPublish(content, images, publishedAt)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Quoi de neuf ?") },
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 140.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { imagePicker.launch("image/*") },
                    label = { Text("Photos (${images.size})") },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                )
                AssistChip(
                    onClick = ::openDateTimePicker,
                    label = { Text(dateFormat.format(publishedAt)) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                )
            }

            if (lastError != null) {
                Text(
                    "Erreur : $lastError",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (images.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    items(images) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                }
            }
        }
    }
}
