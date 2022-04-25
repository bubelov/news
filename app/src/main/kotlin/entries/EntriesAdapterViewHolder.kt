package entries

import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.ListItemEntryBinding
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso

class EntriesAdapterViewHolder(
    private val binding: ListItemEntryBinding,
    private val screenWidth: Int,
    private val callback: EntriesAdapterCallback,
) : RecyclerView.ViewHolder(binding.root) {

    private var setStrokeAlpha = false

    fun bind(item: EntriesAdapterItem) = binding.apply {
        if (!setStrokeAlpha) {
            card.setStrokeColor(card.strokeColorStateList!!.withAlpha(32))
            downloadPodcast.strokeColor = downloadPodcast.strokeColor.withAlpha(32)
            playPodcast.strokeColor = playPodcast.strokeColor.withAlpha(32)
            setStrokeAlpha = true
        }

        println(screenWidth)
        val cardMargin = root.resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

        val cardHeightMin = root.resources.getDimensionPixelSize(R.dimen.card_height_min)
        val cardHeightMax = root.resources.getDimensionPixelSize(R.dimen.card_height_max)

        Picasso.get().load(null as String?).into(imageView)
        imageView.isVisible = false
        imageProgress.isVisible = false
        imageView.tag = item

        val handleImage = fun(url: String, width: Long, height: Long) {
            if (
                imageView.tag != item
                || imageView.drawable != null
                || url.isBlank()
                || width == 0L
                || height == 0L
            ) {
                return
            }

            imageView.isVisible = true
            imageProgress.isVisible = true

            val targetHeight =
                ((screenWidth - cardMargin) * (height.toDouble() / width.toDouble()))

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

            val picassoRequestCreator = Picasso.get().load(url)

            if (width > 0) {
                picassoRequestCreator.resize(width.toInt(), 0)
            }

            picassoRequestCreator.into(imageView, object : Callback {
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

        if (item.ogImageUrl.isNotBlank()) {
            handleImage(item.ogImageUrl, item.ogImageWidth, item.ogImageHeight)
        }

        primaryText.text = item.title.trim()
        primaryText.isVisible = primaryText.length() > 0
        secondaryText.text = item.subtitle

        supportingText.isVisible = item.supportingText.isNotBlank()
        supportingText.text = item.supportingText

        podcastPanel.isVisible = false
        podcastPanel.tag = item

        if (item.podcast) {
            podcastPanel.isVisible = true

            if (item.podcastDownloadPercent == null) {
                downloadPodcast.isVisible = true
                downloadingPodcast.isVisible = false
                downloadPodcastProgress.isVisible = false
                playPodcast.isVisible = false
            } else {
                downloadPodcast.isVisible = false
                downloadingPodcast.isVisible = item.podcastDownloadPercent != 100L
                downloadPodcastProgress.isVisible = item.podcastDownloadPercent != 100L
                downloadPodcastProgress.progress = item.podcastDownloadPercent.toInt()
                playPodcast.isVisible = item.podcastDownloadPercent == 100L
            }

            downloadPodcast.setOnClickListener {
                callback.onDownloadPodcastClick(item)
            }

            playPodcast.setOnClickListener {
                callback.onPlayPodcastClick(item)
            }
        }

        primaryText.isEnabled = !item.read
        secondaryText.isEnabled = !item.read
        supportingText.isEnabled = !item.read

        root.setOnClickListener {
            callback.onItemClick(item)
        }
    }
}