package entries

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

    private var setStrokeAlpha = false

    fun bind(item: EntriesAdapterItem) = binding.apply {
        if (!setStrokeAlpha) {
            card.setStrokeColor(card.strokeColorStateList!!.withAlpha(32))
            setStrokeAlpha = true
        }

        val cardMargin = root.resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

        val cardHeightMin = root.resources.getDimensionPixelSize(R.dimen.card_height_min)
        val cardHeightMax = root.resources.getDimensionPixelSize(R.dimen.card_height_max)

        Picasso.get().load(null as String?).into(imageView)
        imageView.isVisible = false
        imageProgress.isVisible = false

        if (item.showImage && item.entry.ogImageUrl.isNotBlank()) {
            imageView.isVisible = true
            imageProgress.isVisible = true

            val targetHeight =
                ((screenWidth - cardMargin) * (item.entry.ogImageHeight.toDouble() / item.entry.ogImageWidth.toDouble()))

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
                .load(item.entry.ogImageUrl)
                .resize(item.entry.ogImageWidth.toInt(), 0)
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

        primaryText.text = item.title
        primaryText.isVisible = primaryText.length() > 0
        secondaryText.text = item.subtitle

        supportingText.isVisible = item.summary.isNotBlank()
        supportingText.text = item.summary

        primaryText.isEnabled = !item.read
        secondaryText.isEnabled = !item.read
        supportingText.isEnabled = !item.read

        root.setOnClickListener {
            callback.onItemClick(item)
        }
    }
}