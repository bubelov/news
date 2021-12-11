package entry

import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class TextViewImageGetter(
    private val textView: TextView,
    private val scope: LifecycleCoroutineScope,
) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val drawable = TextViewImage(textView)
        val width = textView.width

        if (width == 0) {
            Timber.e(Exception("Entry text view has zero width"))
            return drawable
        }

        scope.launchWhenResumed {
            runCatching {
                withContext(Dispatchers.Main) {
                    Picasso.get()
                        .load(source)
                        .resize(width, 0)
                        .onlyScaleDown()
                        .into(drawable)
                }
            }.onFailure {
                if (it !is CancellationException) {
                    Timber.e(it, "Failed to display image $source")
                }
            }
        }

        return drawable
    }
}