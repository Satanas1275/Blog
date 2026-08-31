package studio.rocknite.blog

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import studio.rocknite.blog.data.LastDetectionStore
import studio.rocknite.blog.data.QuickStatusStore
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.data.TrackedPackagesCache
import studio.rocknite.blog.media.MediaDetector
import studio.rocknite.blog.network.AnalyticsSummary
import studio.rocknite.blog.network.ApiClient
import studio.rocknite.blog.network.MediaApp
import studio.rocknite.blog.network.MediaAppUpsertPayload
import studio.rocknite.blog.network.PatchPostPayload
import studio.rocknite.blog.network.Post
import studio.rocknite.blog.network.StatusPayload
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val analytics: AnalyticsSummary? = null,
    val isPublishing: Boolean = false,
    val lastError: String? = null,
    val serverUrl: String = "",
    val token: String? = null,
    val quickStatusLabels: List<String> = emptyList(),
    val lastStatusError: String? = null,
    val posts: List<Post> = emptyList(),
    val currentStatusLabel: String? = null,
    val mediaApps: List<MediaApp> = emptyList(),
    val lastDetection: LastDetectionStore.Snapshot? = null,
    val isCheckingNow: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private val quickStatusStore = QuickStatusStore(application)
    private val lastDetectionStore = LastDetectionStore(application)
    private val trackedPackagesCache = TrackedPackagesCache(application)
    private var api = ApiClient.create(tokenStore)

    private val _uiState = MutableStateFlow(
        MainUiState(
            serverUrl = tokenStore.serverUrl,
            token = tokenStore.apiToken,
            quickStatusLabels = quickStatusStore.getLabels(),
            lastDetection = lastDetectionStore.get(),
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    fun refreshLastDetection() {
        _uiState.value = _uiState.value.copy(lastDetection = lastDetectionStore.get())
    }

    /**
     * Force une détection immédiate sans dépendre du service en arrière-plan (utile quand il
     * plante ou que le système l'a tué). Marche tant que l'accès aux notifications est accordé.
     */
    fun checkMediaNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingNow = true)
            MediaDetector.checkOnce(
                context = getApplication(),
                tokenStore = tokenStore,
                trackedPackagesCache = trackedPackagesCache,
                lastDetectionStore = lastDetectionStore,
                force = true,
            )
            _uiState.value = _uiState.value.copy(
                isCheckingNow = false,
                lastDetection = lastDetectionStore.get(),
            )
        }
    }

    fun loadMediaApps() {
        viewModelScope.launch {
            runCatching { api.getMediaApps() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(mediaApps = response.body() ?: emptyList())
                    }
                }
        }
    }

    fun addMediaApp(packageName: String, label: String, templates: List<String>) {
        viewModelScope.launch {
            runCatching { api.createMediaApp(MediaAppUpsertPayload(packageName, label.ifBlank { null }, templates)) }
                .onSuccess { if (it.isSuccessful) loadMediaApps() }
        }
    }

    fun updateMediaApp(id: Long, packageName: String, label: String, templates: List<String>) {
        viewModelScope.launch {
            runCatching { api.updateMediaApp(id, MediaAppUpsertPayload(packageName, label.ifBlank { null }, templates)) }
                .onSuccess { if (it.isSuccessful) loadMediaApps() }
        }
    }

    fun deleteMediaApp(id: Long) {
        viewModelScope.launch {
            runCatching { api.deleteMediaApp(id) }
                .onSuccess { if (it.isSuccessful) loadMediaApps() }
        }
    }

    fun saveSettings(serverUrl: String, token: String) {
        tokenStore.serverUrl = serverUrl
        tokenStore.apiToken = token
        api = ApiClient.create(tokenStore) // recrée le client : l'URL de base est figée à la construction
        _uiState.value = _uiState.value.copy(serverUrl = serverUrl, token = token)
    }

    fun pickQuickStatus(label: String) {
        viewModelScope.launch {
            runCatching { api.postStatus(StatusPayload(label)) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        lastStatusError = if (response.isSuccessful) null else "Erreur serveur (${response.code()})",
                        currentStatusLabel = if (response.isSuccessful) label else _uiState.value.currentStatusLabel,
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(lastStatusError = e.message) }
        }
    }

    fun clearCurrentStatus() {
        viewModelScope.launch {
            runCatching { api.deleteStatus() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(currentStatusLabel = null, lastStatusError = null)
                    } else {
                        _uiState.value = _uiState.value.copy(lastStatusError = "Erreur serveur (${response.code()})")
                    }
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(lastStatusError = e.message) }
        }
    }

    fun loadCurrentStatus() {
        viewModelScope.launch {
            runCatching { api.getStatus() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(currentStatusLabel = response.body()?.label)
                    }
                }
        }
    }

    fun addQuickStatus(label: String) {
        quickStatusStore.addLabel(label)
        _uiState.value = _uiState.value.copy(quickStatusLabels = quickStatusStore.getLabels())
    }

    fun removeQuickStatus(label: String) {
        quickStatusStore.removeLabel(label)
        _uiState.value = _uiState.value.copy(quickStatusLabels = quickStatusStore.getLabels())
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            runCatching { api.getAnalyticsSummary() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(analytics = response.body())
                    }
                }
        }
    }

    fun loadPosts() {
        viewModelScope.launch {
            runCatching { api.getPosts() }
                .onSuccess { response ->
                    if (response.isSuccessful) {
                        _uiState.value = _uiState.value.copy(posts = response.body() ?: emptyList())
                    }
                }
        }
    }

    fun editPostContent(postId: Long, newContent: String) {
        viewModelScope.launch {
            runCatching { api.updatePostContent(postId, PatchPostPayload(newContent)) }
                .onSuccess { if (it.isSuccessful) loadPosts() }
        }
    }

    fun addPostImages(postId: Long, images: List<Uri>) {
        viewModelScope.launch {
            val imageParts = images.mapNotNull { uri ->
                uriToTempFile(uri)?.let { file ->
                    MultipartBody.Part.createFormData(
                        "images",
                        file.name,
                        file.asRequestBody("image/*".toMediaTypeOrNull()),
                    )
                }
            }
            runCatching { api.addPostImages(postId, imageParts) }
                .onSuccess { if (it.isSuccessful) loadPosts() }
        }
    }

    fun removePostImage(postId: Long, filename: String) {
        viewModelScope.launch {
            runCatching { api.deletePostImage(postId, filename) }
                .onSuccess { if (it.isSuccessful) loadPosts() }
        }
    }

    fun deletePost(postId: Long) {
        viewModelScope.launch {
            runCatching { api.deletePost(postId) }
                .onSuccess { if (it.isSuccessful) loadPosts() }
        }
    }

    fun publishPost(content: String, images: List<Uri>, publishedAt: Date) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPublishing = true, lastError = null)

            val contentBody = content.toRequestBody("text/plain".toMediaTypeOrNull())
            val publishedAtBody = isoFormat.format(publishedAt).toRequestBody("text/plain".toMediaTypeOrNull())

            val imageParts = images.mapNotNull { uri ->
                uriToTempFile(uri)?.let { file ->
                    MultipartBody.Part.createFormData(
                        "images",
                        file.name,
                        file.asRequestBody("image/*".toMediaTypeOrNull()),
                    )
                }
            }

            runCatching { api.createPost(contentBody, publishedAtBody, imageParts) }
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isPublishing = false,
                        lastError = if (response.isSuccessful) null else "Erreur serveur (${response.code()})",
                    )
                    if (response.isSuccessful) loadPosts()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isPublishing = false, lastError = e.message)
                }
        }
    }

    private fun uriToTempFile(uri: Uri): File? {
        val context = getApplication<Application>()
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val temp = File.createTempFile("upload_", ".jpg", context.cacheDir)
        temp.outputStream().use { output -> input.use { it.copyTo(output) } }
        return temp
    }
}
