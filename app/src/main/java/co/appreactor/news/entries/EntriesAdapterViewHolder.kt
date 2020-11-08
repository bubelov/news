package co.appreactor.news.entries

import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.db.EntryImage
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.list_item_entry.view.*
import kotlinx.coroutines.flow.collect

class EntriesAdapterViewHolder(
    private val view: View,
    private val screenWidth: Int,
    private val scope: LifecycleCoroutineScope,
    private val callback: EntriesAdapterCallback,
) : RecyclerView.ViewHolder(view) {

    fun bind(item: EntriesAdapterItem) {
        view.apply {
            val cardMargin = resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

            val cardHeightMin = resources.getDimensionPixelSize(R.dimen.card_height_min)
            val cardHeightMax = resources.getDimensionPixelSize(R.dimen.card_height_max)

            Picasso.get().load(null as String?).into(imageView)
            imageView.isVisible = false
            imageProgress.isVisible = false
            imageView.tag = item

            if (item.showImage) {
                val handleImage = fun(image: EntryImage?) {
                    if (
                        imageView.tag != item
                        || imageView.drawable != null
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

                    val picassoRequestCreator = Picasso.get().load(if (image.url.isBlank()) null else image.url)

                    if (image.width > 0) {
                        picassoRequestCreator.resize(image.width.toInt(), 0)
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

                if (item.cachedImage.value != null) {
                    handleImage(item.cachedImage.value)
                } else {
                    scope.launchWhenResumed {
                        item.image.collect {
                            handleImage(it)
                        }
                    }
                }
            }

            primaryText.text = item.title.trim()
            secondaryText.text = item.subtitle.value

            supportingText.isVisible = false
            supportingText.tag = item

            if (!item.cachedSupportingText.isNullOrBlank()) {
                supportingText.isVisible = true
                supportingText.text = item.cachedSupportingText
            } else {
                scope.launchWhenResumed {
                    item.supportingText.collect { text ->
                        if (supportingText.tag == item && text.isNotBlank()) {
                            supportingText.isVisible = true
                            supportingText.text = text
                        }
                    }
                }
            }

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

            primaryText.isEnabled = !item.opened
            secondaryText.isEnabled = !item.opened
            supportingText.isEnabled = !item.opened

            setOnClickListener {
                callback.onItemClick(item)
            }
        }
    }
}