package entry

import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.squareup.picasso.Picasso
import kotlinx.coroutines.*

class TextViewImageGetter(
    private val textView: TextView,
    private val scope: LifecycleCoroutineScope,
) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val drawable = TextViewImage(textView)

        scope.launchWhenResumed {
            withContext(Dispatchers.Main) {
                Picasso.get()
                    .load(source)
                    .resize(textView.width, 0)
                    .onlyScaleDown()
                    .into(drawable)
            }
        }

        return drawable
    }
}

