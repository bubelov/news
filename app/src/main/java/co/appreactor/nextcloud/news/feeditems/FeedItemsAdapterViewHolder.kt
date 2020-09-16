package co.appreactor.nextcloud.news.feeditems

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.db.OpenGraphImage
import co.appreactor.nextcloud.news.opengraph.OpenGraphImagesRepository
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.list_item_feed_item.view.*
import kotlinx.coroutines.flow.collect

class FeedItemsAdapterViewHolder(
    private val view: View,
    private val screenWidth: Int,
    private val scope: LifecycleCoroutineScope,
    private val callback: FeedItemsAdapterCallback,
) : RecyclerView.ViewHolder(view) {

    fun bind(item: FeedItemsAdapterItem) {
        view.apply {
            val cardMargin = resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

            val cardHeightMin = resources.getDimensionPixelSize(R.dimen.card_height_min)
            val cardHeightMax = resources.getDimensionPixelSize(R.dimen.card_height_max)

            Picasso.get().load(null as String?).into(imageView)
            imageView.isVisible = false
            imageProgress.isVisible = false
            imageView.tag = item

            if (item.showImage) {
                val handleImage = fun(image: OpenGraphImage?) {
                    if (
                        imageView.tag != item
                        || image == null
                        || image.url.isBlank()
                        || image.width == 0L
                        || image.height == 0L
                    ) {
                        return
                    }

                    imageView.isVisible = true
                    imageProgress.isVisible = true

                    val targetHeight =
                        ((screenWidth - cardMargin) * (image.height.toDouble() / image.width.toDouble()))

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
                        .load(if (image.url.isBlank()) null else image.url)
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

                if (item.cachedImage != null && item.cachedImage.status == OpenGraphImagesRepository.STATUS_PROCESSED) {
                    handleImage(item.cachedImage)
                } else {
                    scope.launchWhenResumed {
                        item.image.collect {
                            handleImage(it)
                        }
                    }
                }
            }

            primaryText.text = item.title
            secondaryText.text = item.subtitle

            supportingText.tag = item

            if (item.cachedSummary != null) {
                supportingText.isVisible = true
                supportingText.text = item.cachedSummary
            } else {
                supportingText.isVisible = false

                scope.launchWhenResumed {
                    item.summary.collect { summary ->
                        if (supportingText.tag == item && summary.isNotBlank()) {
                            supportingText.isVisible = true
                            supportingText.text = summary
                        }
                    }
                }
            }

            primaryText.isEnabled = item.unread
            secondaryText.isEnabled = item.unread
            supportingText.isEnabled = item.unread

            podcastPanel.isVisible = false
            podcastPanel.tag = item

            if (item.podcast) {
                scope.launchWhenResumed {
                    item.podcastDownloadPercent.collect { progress ->
                        if (podcastPanel.tag != item) {
                            return@collect
                        }

                        podcastPanel.isVisible = true

                        if (progress == null) {
                            downloadPodcast.isVisible = true
                            downloadingPodcast.isVisible = false
                            downloadPodcastProgress.isVisible = false
                            playPodcast.isVisible = false
                        } else {
                            downloadPodcast.isVisible = false
                            downloadingPodcast.isVisible = progress != 100L
                            downloadPodcastProgress.isVisible = progress != 100L
                            downloadPodcastProgress.progress = progress.toInt()
                            playPodcast.isVisible = progress == 100L
                        }
                    }
                }

                downloadPodcast.setOnClickListener {
                    callback.onDownloadPodcastClick(item)
                }

                playPodcast.setOnClickListener {
                    callback.onPlayPodcastClick(item)
                }
            }

            setOnClickListener {
                callback.onItemClick(item)
            }
        }
    }
}