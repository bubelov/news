package co.appreactor.nextcloud.news.feeditems

import android.graphics.drawable.BitmapDrawable
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
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
                scope.launchWhenResumed {
                    item.imageUrl.collect { imageUrl ->
                        if (imageView.tag != item) {
                            return@collect
                        }

                        Picasso.get().load(null as String?).into(imageView)
                        imageView.isVisible = false

                        if (imageUrl.isNotBlank()) {
                            imageView.isVisible = true
                            imageProgress.isVisible = true
                        }

                        Picasso.get()
                            .load(if (imageUrl.isBlank()) null else imageUrl)
                            .resize(screenWidth - cardMargin, 0)
                            .into(imageView, object : Callback {
                                override fun onSuccess() {
                                    imageProgress.isVisible = false

                                    val drawable = imageView.drawable as BitmapDrawable
                                    val bitmap = drawable.bitmap

                                    if (bitmap.hasTransparentAngles() || bitmap.looksLikeSingleColor()) {
                                        imageView.isVisible = false
                                        return
                                    }

                                    imageProgress.isVisible = false

                                    val targetHeight =
                                        ((screenWidth - cardMargin) * (drawable.intrinsicHeight.toDouble() / drawable.intrinsicWidth.toDouble()))

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
                                }

                                override fun onError(e: Exception) {
                                    imageView.isVisible = false
                                    imageProgress.isVisible = false
                                }
                            })
                    }
                }
            }

            primaryText.text = item.title
            secondaryText.text = item.subtitle

            supportingText.isVisible = false
            supportingText.tag = item

            scope.launchWhenResumed {
                item.summary.collect { summary ->
                    if (supportingText.tag == item && summary.isNotBlank()) {
                        supportingText.isVisible = true
                        supportingText.text = summary
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