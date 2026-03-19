package org.vestifeed.entry

import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class TextViewImageGetter(
    private val textView: TextView,
    private val scope: LifecycleCoroutineScope,
    private val baseUrl: HttpUrl?,
) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val drawable = TextViewImage(textView)
        val width = textView.width

        if (width == 0) {
            return drawable
        }

        val isAbsolute = source.startsWith("http")

        val url = if (isAbsolute) source.toHttpUrlOrNull() else {
            if (baseUrl == null) {
                null
            } else {
                "${baseUrl.scheme}://${baseUrl.host}/$source".toHttpUrlOrNull()
            }
        }

        if (url == null) {
            return drawable
        }

        scope.launchWhenResumed {
            runCatching {
                withContext(Dispatchers.Main) {
                    val request = ImageRequest.Builder(textView.context)
                        .data(url.toString())
                        .size(width, 0)
                        .target(drawable)
                        .build()
                    ImageLoader(textView.context).enqueue(request)
                }
            }
        }

        return drawable
    }
}