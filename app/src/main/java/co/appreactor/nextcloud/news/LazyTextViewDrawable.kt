package co.appreactor.nextcloud.news

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.TextView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target


class LazyTextViewDrawable(private val textView: TextView) : Drawable(), Target {

    private var drawable: Drawable? = null
        set(value) {
            field = value
            val width = textView.width
            val height =
                field!!.intrinsicHeight.toDouble() * (textView.width.toDouble() / field!!.intrinsicWidth.toDouble())
            field!!.setBounds(0, 0, width, height.toInt())
            setBounds(0, 0, width, height.toInt())
            textView.text = textView.text
        }

    override fun draw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity() = PixelFormat.OPAQUE

    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        drawable = BitmapDrawable(textView.context.resources, bitmap)
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {

    }

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

    }
}