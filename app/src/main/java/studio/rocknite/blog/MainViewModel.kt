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
import studio.rocknite.blog.data.QuickStatusStore
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.network.AnalyticsSummary
import studio.rocknite.blog.network.ApiClient
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
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private val quickStatusStore = QuickStatusStore(application)
    private var api = ApiClient.create(tokenStore)

    private val _uiState = MutableStateFlow(
        MainUiState(
            serverUrl = tokenStore.serverUrl,
            token = tokenStore.apiToken,
            quickStatusLabels = quickStatusStore.getLabels(),
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

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
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(lastStatusError = e.message) }
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
