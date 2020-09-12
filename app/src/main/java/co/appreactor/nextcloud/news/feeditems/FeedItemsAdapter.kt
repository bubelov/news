package co.appreactor.nextcloud.news.feeditems

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
import kotlinx.coroutines.Job

class FeedItemsAdapter(
    var screenWidth: Int = 0,
    private val scope: LifecycleCoroutineScope,
    private val callback: FeedItemsAdapterCallback,
) : ListAdapter<FeedItemsAdapterRow, FeedItemsAdapter.ViewHolder>(FeedItemsAdapterDiffCallback()) {

    class ViewHolder(
        private val view: View,
        private val screenWidth: Int,
        private val scope: LifecycleCoroutineScope,
        private val callback: FeedItemsAdapterCallback
    ) :
        RecyclerView.ViewHolder(view) {

        fun bind(row: FeedItemsAdapterRow, isFirst: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst

                imageView.isVisible = false
                imageProgress.isVisible = false

                if (row.imageUrl.isNotBlank()) {
                    imageView.isVisible = true
                    imageProgress.isVisible = true
                }

                val cardMargin = resources.getDimensionPixelSize(R.dimen.card_horizontal_margin)

                Picasso.get()
                    .load(if (row.imageUrl.isBlank()) null else row.imageUrl)
                    .resize(screenWidth - cardMargin, 0)
                    .into(imageView, object : Callback {
                        override fun onSuccess() {
                            imageView.isVisible = true
                            imageProgress.isVisible = false

                            if (!row.cropImage) {
                                val drawable = imageView.drawable
                                val targetHeight =
                                    ((screenWidth - cardMargin) * (drawable.intrinsicHeight.toDouble() / drawable.intrinsicWidth.toDouble()))

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

                primaryText.text = row.title
                secondaryText.text = row.subtitle

                supportingText.tag.apply {
                    if (this is Job) {
                        this.cancel()
                    }
                }

                supportingText.tag = null
                supportingText.isVisible = false

                supportingText.tag = scope.launchWhenResumed {
                    val summary = callback.generateSummary(row.id)

                    if (summary.isNotBlank()) {
                        supportingText.isVisible = true
                        supportingText.text = summary
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