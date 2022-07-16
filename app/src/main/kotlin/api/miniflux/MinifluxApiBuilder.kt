package api.miniflux

import co.appreactor.news.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import http.trustSelfSignedCerts
import log.LoggingInterceptor
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.IOException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MinifluxApiBuilder {

    fun build(
        url: String,
        username: String,
        password: String,
        trustSelfSignedCerts: Boolean,
    ): MinifluxApi {
        val authInterceptor = Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(username, password))
                    .build()
            )
        }

        val errorInterceptor = Interceptor {
            val response = it.proceed(it.request())

            if (!response.isSuccessful && response.body != null) {
                val json = runCatching {
                    Gson().fromJson(response.body!!.string(), JsonObject::class.java)
                }.getOrNull()

                val errorMessage = if (json != null && json.has("error_message")) {
                    json["error_message"].asString
                } else {
                    "Endpoint ${it.request().url} failed with response code ${response.code}"
                }

                throw IOException(errorMessage)
            }

            response
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (trustSelfSignedCerts) {
            clientBuilder.trustSelfSignedCerts()
        }

        if (BuildConfig.DEBUG) {
            clientBuilder.addInterceptor(LoggingInterceptor("miniflux"))
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("$url/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
            .build()

        return retrofit.create(MinifluxApi::class.java)
    }
}