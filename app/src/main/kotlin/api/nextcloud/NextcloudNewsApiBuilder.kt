package api.nextcloud

import co.appreactor.news.BuildConfig
import common.trustSelfSignedCerts
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NextcloudNewsApiBuilder {

    fun build(
        url: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ): NextcloudNewsApi {
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

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authenticatingInterceptor)
            .addInterceptor(httpLoggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (trustSelfSignedCerts) {
            clientBuilder.trustSelfSignedCerts()
        }

        val client = clientBuilder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$url/index.php/apps/news/api/v1-2/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(NextcloudNewsApi::class.java)
    }
}