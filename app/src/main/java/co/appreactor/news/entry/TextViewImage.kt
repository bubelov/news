package co.appreactor.news.entry

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.TextView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class TextViewImage(private val textView: TextView) : Drawable(), Target {

    private var drawable: Drawable? = null

    override fun draw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity() = PixelFormat.OPAQUE

    override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
        setBounds(0, 0, bitmap.width, bitmap.height)

        this.drawable = BitmapDrawable(textView.context.resources, bitmap).apply {
            setBounds(0, 0, bitmap.width, bitmap.height)
        }

        textView.text = textView.text
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {

    }

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

    }
}