package api.miniflux

import co.appreactor.news.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import common.trustSelfSignedCerts
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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
                throw MinifluxApiException(json["error_message"].asString)
            }

            response
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

            redactHeader("Authorization")
        }

        val clientBuilder = OkHttpClient.Builder()
            .addInterceptor(authenticatingInterceptor)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(errorInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (trustSelfSignedCerts) {
            clientBuilder.trustSelfSignedCerts()
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