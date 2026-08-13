package studio.rocknite.blog.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import studio.rocknite.blog.data.TokenStore

object ApiClient {

    fun create(tokenStore: TokenStore): BlogApi {
        val authInterceptor = Interceptor { chain ->
            val token = tokenStore.apiToken
            val request = chain.request().newBuilder().apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }.build()
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        val baseUrl = tokenStore.serverUrl.let { if (it.endsWith("/")) it else "$it/" }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BlogApi::class.java)
    }
}
