package co.appreactor.nextcloud.news.feeditems

import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.list_item_feed_item.view.*
import kotlinx.coroutines.flow.collect

class FeedItemsAdapter(
    var screenWidth: Int = 0,
    private val scope: LifecycleCoroutineScope,
    private val callback: FeedItemsAdapterCallback,
) : ListAdapter<FeedItemsAdapterRow, FeedItemsAdapter.ViewHolder>(FeedItemsAdapterDiffCallback()) {

    class ViewHolder(
        private val view: View,
        private val screenWidth: Int,
        private val scope: LifecycleCoroutineScope,
        private val callback: FeedItemsAdapterCallback,
    ) :
        RecyclerView.ViewHolder(view) {

        fun bind(row: FeedItemsAdapterRow, isFirst: Boolean) {
            view.apply {
                val cardMargin = resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

                val cardHeightMin = resources.getDimensionPixelSize(R.dimen.card_height_min)
                val cardHeightMax = resources.getDimensionPixelSize(R.dimen.card_height_max)

                topOffset.isVisible = isFirst

                Picasso.get().load(null as String?).into(imageView)
                imageView.isVisible = false
                imageProgress.isVisible = false
                imageView.tag = row

                if (row.showImage) {
                    scope.launchWhenResumed {
                        row.imageUrl.collect { imageUrl ->
                            if (imageView.tag != row) {
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

                                        if (row.cropImage) {
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

                primaryText.text = row.title
                secondaryText.text = row.subtitle

                supportingText.isVisible = false
                supportingText.tag = row

                scope.launchWhenResumed {
                    row.summary.collect { summary ->
                        if (supportingText.tag == row && summary.isNotBlank()) {
                            supportingText.isVisible = true
                            supportingText.text = summary
                        }
                    }
                }

                primaryText.isEnabled = row.unread
                secondaryText.isEnabled = row.unread
                supportingText.isEnabled = row.unread

                podcastPanel.isVisible = row.podcast

                if (row.podcast) {
                    if (row.podcastDownloadPercent == null) {
                        downloadPodcast.isVisible = true
                        downloadingPodcast.isVisible = false
                        downloadPodcastProgress.isVisible = false
                        playPodcast.isVisible = false
                    } else {
                        downloadPodcast.isVisible = false
                        downloadingPodcast.isVisible = row.podcastDownloadPercent != 100L
                        downloadPodcastProgress.isVisible = row.podcastDownloadPercent != 100L
                        downloadPodcastProgress.progress = row.podcastDownloadPercent.toInt()
                        playPodcast.isVisible = row.podcastDownloadPercent == 100L
                    }

                    downloadPodcast.setOnClickListener {
                        callback.onDownloadPodcastClick(row)
                    }

                    playPodcast.setOnClickListener {
                        callback.onPlayPodcastClick(row)
                    }
                }

                card.setOnClickListener {
                    callback.onItemClick(row)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_feed_item,
            parent, false
        )

        return ViewHolder(view, screenWidth, scope, callback)

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            row = getItem(position),
            isFirst = position == 0
        )
    }
}