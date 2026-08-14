package studio.rocknite.blog.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

data class Post(
    val id: Long,
    val content: String,
    val images: List<String>,
    val published_at: String,
    val created_at: String,
)

data class PatchPostPayload(val content: String)

data class NowPlayingPayload(
    val source: String,
    val title: String,
    val subtitle: String?,
    val link: String?,
    val state: String,
    val image_base64: String?,
)

data class StatusPayload(val label: String)
data class StatusResponse(val id: Int, val label: String, val updated_at: String)

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

    // Avec le token (toujours envoyé via l'intercepteur), retourne aussi les posts programmés
    @GET("api/posts")
    suspend fun getPosts(@Query("limit") limit: Int = 30): Response<List<Post>>

    @PATCH("api/posts/{id}")
    suspend fun updatePostContent(@Path("id") id: Long, @Body payload: PatchPostPayload): Response<Unit>

    @Multipart
    @POST("api/posts/{id}/images")
    suspend fun addPostImages(@Path("id") id: Long, @Part images: List<MultipartBody.Part>): Response<Unit>

    @DELETE("api/posts/{id}/images/{filename}")
    suspend fun deletePostImage(@Path("id") id: Long, @Path("filename") filename: String): Response<Unit>

    @DELETE("api/posts/{id}")
    suspend fun deletePost(@Path("id") id: Long): Response<Unit>

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

    @GET("api/status")
    suspend fun getStatus(): Response<StatusResponse?>

    @POST("api/status")
    suspend fun postStatus(@Body payload: StatusPayload): Response<Unit>

    @DELETE("api/status")
    suspend fun deleteStatus(): Response<Unit>
}
