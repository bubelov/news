package log

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class LoggingInterceptor(private val tag: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        Log.d(tag, "Requesting ${req.url}")
        val res = chain.proceed(req)
        Log.d(tag, "Got response code ${res.code} from ${req.url}")
        return res
    }
}