package co.appreactor.news.entry

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
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

    override fun onBitmapLoaded(unprocessedBitmap: Bitmap, from: Picasso.LoadedFrom) {
        val scaleToFullWidth = unprocessedBitmap.width >= textView.width / 5

        val bitmap = if (scaleToFullWidth) {
            val scaleFactor = textView.width.toFloat() / unprocessedBitmap.width.toFloat()
            Bitmap.createScaledBitmap(
                unprocessedBitmap,
                textView.width,
                (unprocessedBitmap.height * scaleFactor).toInt(),
                true
            )
        } else {
            unprocessedBitmap
        }

        val verticalCutoff =
            (bitmap.height * textView.lineSpacingMultiplier - bitmap.height) / textView.lineSpacingMultiplier

        setBounds(0, 0, bitmap.width, (bitmap.height / textView.lineSpacingMultiplier).toInt())

        this.drawable = BitmapDrawable(textView.context.resources, bitmap).apply {
            setBounds(0, -verticalCutoff.toInt(), bitmap.width, -verticalCutoff.toInt() + bitmap.height)
        }

        Handler().post {
            val text = SpannableStringBuilder(textView.text)
            val spans = text.getSpans(0, text.length - 1, Any::class.java)

            spans.forEach {
                when (it) {
                    is ImageSpan -> {
                        val spanEnd = text.getSpanEnd(it)

                        if (scaleToFullWidth && spanEnd + 2 <= text.length - 1) {
                            if (text[spanEnd] != '\n' && text[spanEnd + 1] != '\n') {
                                text.insert(spanEnd, "\n\n")

                                if (text[spanEnd + 2] == ' ') {
                                    text.delete(spanEnd + 2, spanEnd + 3)
                                }
                            }

                            if (text[spanEnd] == '\n' && text[spanEnd + 1] != '\n') {
                                text.insert(spanEnd, "\n")
                            }
                        }
                    }
                }
            }

            textView.text = text
        }
    }

    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {

    }

    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

    }
}