package co.appreactor.nextcloud.news.api

import co.appreactor.nextcloud.news.BuildConfig
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class DirectApiBuilder {

    fun build(serverUrl: String, username: String, password: String): NewsApi {
        val authenticatingInterceptor = Interceptor {
            val request = it.request()
            val credential = Credentials.basic(username, password)
            it.proceed(request.newBuilder().header("Authorization", credential).build())
        }

        val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            redactHeader("Authorization")
            redactHeader("Cookie")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authenticatingInterceptor)
            .addInterceptor(httpLoggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$serverUrl/index.php/apps/news/api/v1-2/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(NewsApi::class.java)
    }
}