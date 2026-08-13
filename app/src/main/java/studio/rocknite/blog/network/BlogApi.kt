package studio.rocknite.blog.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

data class NowPlayingPayload(
    val source: String,
    val title: String,
    val subtitle: String?,
    val link: String?,
    val state: String,
    val image_base64: String?,
)

data class StatusPayload(val label: String)

data class AnalyticsSummary(
    val days: Int,
    val totalPosts: Int,
    val totalViews: Int,
    val viewsPerDay: List<DayCount>,
    val topPaths: List<PathCount>,
)

data class DayCount(val day: String, val count: Int)
data class PathCount(val path: String, val count: Int)

interface BlogApi {

    // published_at et images sont envoyés en multipart depuis le repository
    @Multipart
    @POST("api/posts")
    suspend fun createPost(
        @Part("content") content: RequestBody,
        @Part("published_at") publishedAt: RequestBody?,
        @Part images: List<MultipartBody.Part>,
    ): Response<Unit>

    @GET("api/analytics/summary")
    suspend fun getAnalyticsSummary(@Query("days") days: Int = 30): Response<AnalyticsSummary>

    @POST("api/now-playing")
    suspend fun pushNowPlayingBody(@Body payload: NowPlayingPayload): Response<Unit>

    suspend fun pushNowPlaying(
        source: String,
        title: String,
        subtitle: String?,
        link: String?,
        state: String,
        imageBase64: String? = null,
    ) {
        pushNowPlayingBody(NowPlayingPayload(source, title, subtitle, link, state, imageBase64))
    }

    @POST("api/status")
    suspend fun postStatus(@Body payload: StatusPayload): Response<Unit>
}
