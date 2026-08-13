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
import studio.rocknite.blog.data.TokenStore
import studio.rocknite.blog.network.AnalyticsSummary
import studio.rocknite.blog.network.ApiClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MainUiState(
    val analytics: AnalyticsSummary? = null,
    val isPublishing: Boolean = false,
    val lastError: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application)
    private val api = ApiClient.create(tokenStore)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

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
