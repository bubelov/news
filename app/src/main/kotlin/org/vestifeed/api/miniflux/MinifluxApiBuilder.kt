package org.vestifeed.api.miniflux

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import org.vestifeed.BuildConfig
import org.vestifeed.http.tokenAuthInterceptor
import org.vestifeed.http.trustSelfSignedCerts
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MinifluxApiBuilder {

    fun build(
        url: String,
        token: String,
        trustSelfSignedCerts: Boolean,
    ): MinifluxApi {
        val builder = OkHttpClient.Builder()
            .addInterceptor(tokenAuthInterceptor(token))
            .addInterceptor(errorInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        if (trustSelfSignedCerts) {
            builder.trustSelfSignedCerts()
        }

        return MinifluxApi(
            client = builder.build(),
            baseUrl = "$url/v1/".toHttpUrl(),
        )
    }

    private fun errorInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (!response.isSuccessful) {
                val bodyString = response.body.string()
                val errorMessage = runCatching {
                    val json = Gson().fromJson(bodyString, com.google.gson.JsonObject::class.java)
                    if (json != null && json.has("error_message")) {
                        json["error_message"].asString
                    } else {
                        "Endpoint ${request.url} failed with response code ${response.code}"
                    }
                }.getOrElse {
                    "Endpoint ${request.url} failed with response code ${response.code}"
                }

                throw java.io.IOException(errorMessage)
            }

            response
        }
    }
}