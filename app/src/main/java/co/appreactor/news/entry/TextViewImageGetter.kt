package co.appreactor.news.entry

import android.graphics.drawable.Drawable
import android.text.Html
import android.widget.TextView
import com.squareup.picasso.Picasso

class TextViewImageGetter(private val textView: TextView) : Html.ImageGetter {

    override fun getDrawable(source: String): Drawable {
        val drawable = TextViewImage(textView)

        Picasso.get()
            .load(source)
            .resize(textView.width, 0)
            .onlyScaleDown()
            .into(drawable)

        return drawable
    }
}

