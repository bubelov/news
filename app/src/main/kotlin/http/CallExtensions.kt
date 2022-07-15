package http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okio.IOException

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }

        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(Result.success(response))
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }
        })
    }
}