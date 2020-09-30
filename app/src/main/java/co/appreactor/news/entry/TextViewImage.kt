package co.appreactor.news.entry

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.TextView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class TextViewImage(
    private val textView: TextView
) : Drawable(), Target {

    private var drawable: Drawable? = null

    override fun draw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity() = PixelFormat.OPAQUE

    override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
        if (bitmap.width < textView.width / 5) {
            return
        }

        val scaleFactor = textView.width.toFloat() / bitmap.width.toFloat()

        val scaledBitmap =
            Bitmap.createScaledBitmap(bitmap, textView.width, (bitmap.height * scaleFactor).toInt(), true)

        val verticalCutoff =
            (scaledBitmap.height * textView.lineSpacingMultiplier - scaledBitmap.height) / textView.lineSpacingMultiplier

        setBounds(0, 0, scaledBitmap.width, (scaledBitmap.height / textView.lineSpacingMultiplier).toInt())

        this.drawable = BitmapDrawable(textView.context.resources, scaledBitmap).apply {
            setBounds(0, -verticalCutoff.toInt(), scaledBitmap.width, -verticalCutoff.toInt() + scaledBitmap.height)
        }

        textView.text = textView.text
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {

    }

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

    }
}