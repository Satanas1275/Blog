package studio.rocknite.blog.ui.feed

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import studio.rocknite.blog.network.Post

/**
 * Liste des posts (y compris programmés dans le futur, le token donne accès à tout) avec
 * édition du texte, ajout/suppression d'images, et suppression du post entier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    posts: List<Post>,
    imageBaseUrl: String,
    onEditContent: (postId: Long, newContent: String) -> Unit,
    onAddImages: (postId: Long, images: List<Uri>) -> Unit,
    onRemoveImage: (postId: Long, filename: String) -> Unit,
    onDeletePost: (postId: Long) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mes posts") }) }) { padding ->
        if (posts.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Aucun post pour l'instant.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    imageBaseUrl = imageBaseUrl,
                    onEditContent = { newContent -> onEditContent(post.id, newContent) },
                    onAddImages = { images -> onAddImages(post.id, images) },
                    onRemoveImage = { filename -> onRemoveImage(post.id, filename) },
                    onDelete = { onDeletePost(post.id) },
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    imageBaseUrl: String,
    onEditContent: (String) -> Unit,
    onAddImages: (List<Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var isEditing by remember(post.id) { mutableStateOf(false) }
    var editedContent by remember(post.id, post.content) { mutableStateOf(post.content) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) onAddImages(uris) }

    ElevatedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.published_at, style = MaterialTheme.typography.bodySmall)

            if (isEditing) {
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        onEditContent(editedContent)
                        isEditing = false
                    }) { Text("Enregistrer") }
                    TextButton(onClick = {
                        editedContent = post.content
                        isEditing = false
                    }) { Text("Annuler") }
                }
            } else {
                Text(post.content)
            }

            if (post.images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.images) { filename ->
                        Box {
                            AsyncImage(
                                model = "$imageBaseUrl/uploads/$filename",
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                            )
                            IconButton(
                                onClick = { onRemoveImage(filename) },
                                modifier = Modifier.size(24.dp).align(androidx.compose.ui.Alignment.TopEnd),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Retirer l'image",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Éditer le texte")
                }
                IconButton(onClick = { imagePicker.launch("image/*") }) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Ajouter des photos")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer le post", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer ce post ?") },
            text = { Text("Action définitive, texte et images seront supprimés.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
            },
        )
    }
}
