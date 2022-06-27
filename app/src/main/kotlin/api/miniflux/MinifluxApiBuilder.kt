package api.miniflux

import co.appreactor.news.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import http.trustSelfSignedCerts
import log.LoggingInterceptor
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
        val authenticatingInterceptor = Interceptor {
            val request = it.request()
            val credential = Credentials.basic(username, password)
            it.proceed(request.newBuilder().header("Authorization", credential).build())
        }

        val errorInterceptor = Interceptor {
            val response = it.proceed(it.request())

            if (!response.isSuccessful) {
                val json = Gson().fromJson(response.body!!.string(), JsonObject::class.java)

                val errorMessage = if (json != null && json.has("error_message")) {
                    json["error_message"].asString
                } else {
                    "HTTP request ${it.request().url} failed with response code ${response.code}"
                }

                throw Exception(errorMessage)
            }

            response
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authenticatingInterceptor)
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

        val client = clientBuilder.build()

        val retrofit = Retrofit.Builder()
            .baseUrl("$url/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        return retrofit.create(MinifluxApi::class.java)
    }
}