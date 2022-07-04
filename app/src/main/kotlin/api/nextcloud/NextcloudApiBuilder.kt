package api.nextcloud

import co.appreactor.news.BuildConfig
import http.trustSelfSignedCerts
import log.LoggingInterceptor
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NextcloudApiBuilder {

    fun build(
        url: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ): NextcloudApi {
        val authenticatingInterceptor = Interceptor {
            val request = it.request()
            val credential = Credentials.basic(username, password)
            it.proceed(request.newBuilder().header("Authorization", credential).build())
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authenticatingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (trustSelfSignedCerts) {
            clientBuilder.trustSelfSignedCerts()
        }

        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(LoggingInterceptor("miniflux"))
        }

        val client = clientBuilder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$url/index.php/apps/news/api/v1-2/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(NextcloudApi::class.java)
    }
}