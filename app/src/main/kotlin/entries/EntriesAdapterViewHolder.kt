package entries

import android.text.Html
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.ListItemEntryBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso

class EntriesAdapterViewHolder(
    private val binding: ListItemEntryBinding,
    private val callback: EntriesAdapterCallback,
    private val screenWidth: Int,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: EntriesAdapter.Item) = binding.apply {
        val cardMargin = root.resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

        val cardHeightMin = root.resources.getDimensionPixelSize(R.dimen.card_height_min)
        val cardHeightMax = root.resources.getDimensionPixelSize(R.dimen.card_height_max)

        Picasso.get().load(null as String?).into(imageView)
        imageView.isVisible = false
        imageProgress.isVisible = false

        if (item.showImage && item.imageUrl.isNotEmpty()) {
            imageView.isVisible = true
            imageProgress.isVisible = true

            val targetHeight =
                ((screenWidth - cardMargin) * (item.imageHeight.toDouble() / item.imageWidth.toDouble()))

            if (item.cropImage) {
                var croppedHeight = targetHeight.toInt()

                if (croppedHeight < cardHeightMin) {
                    croppedHeight = cardHeightMin
                }

                if (croppedHeight > cardHeightMax) {
                    croppedHeight = cardHeightMax
                }

                if (imageView.height != croppedHeight) {
                    imageView.layoutParams.height = croppedHeight
                }
            } else {
                if (imageView.height != targetHeight.toInt()) {
                    imageView.layoutParams.height = targetHeight.toInt()
                }
            }

            Picasso.get()
                .load(item.imageUrl)
                .resize(item.imageWidth, 0)
                .onlyScaleDown()
                .into(imageView, object : Callback {
                    override fun onSuccess() {
                        imageProgress.isVisible = false
                        imageProgress.isVisible = false
                    }

                    override fun onError(e: Exception) {
                        imageView.isVisible = false
                        imageProgress.isVisible = false
                    }
                })
        }

        primaryText.text = Html.fromHtml(item.title, Html.FROM_HTML_MODE_COMPACT).toString()
        primaryText.isVisible = primaryText.length() > 0
        secondaryText.text = item.subtitle

        supportingText.isVisible = item.summary.isNotBlank()
        supportingText.text = item.summary

        primaryText.isEnabled = !item.read
        secondaryText.isEnabled = !item.read
        supportingText.isEnabled = !item.read

        root.setOnClickListener { callback.onItemClick(item) }
    }
}