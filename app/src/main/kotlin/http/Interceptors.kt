package http

import android.util.Log
import okhttp3.Credentials
import okhttp3.Interceptor

fun authInterceptor(username: String, password: String): Interceptor {
    return Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
        )
    }
}

fun loggingInterceptor(tag: String): Interceptor {
    return Interceptor { chain ->
        val req = chain.request()
        Log.d(tag, "Requesting ${req.url}")
        val res = chain.proceed(req)
        Log.d(tag, "Got response code ${res.code} from ${req.url}")
        res
    }
}