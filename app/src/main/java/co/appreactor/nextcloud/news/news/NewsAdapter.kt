package co.appreactor.nextcloud.news.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.row_item.view.*

class NewsAdapter(
    private val rows: MutableList<NewsAdapterRow> = mutableListOf(),
    private val callback: NewsAdapterCallback
) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    class ViewHolder(
        private val view: View,
        private val callback: NewsAdapterCallback
    ) :
        RecyclerView.ViewHolder(view) {
        fun bind(row: NewsAdapterRow, isFirst: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst

                image.isVisible = false

                if (row.imageUrl.isNotBlank()) {
                    Picasso.get()
                        .load(row.imageUrl)
                        .resize(1080, 0)
                        .into(image, object : Callback {
                            override fun onSuccess() {
                                image.isVisible = true
                            }

                            override fun onError(e: Exception) {
                                image.isVisible = false
                            }
                        })
                }

                primaryText.text = row.title
                secondaryText.text = row.subtitle
                supportingText.text = row.summary

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
                    callback.onRowClick(row)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.row_item,
            parent, false
        )

        return ViewHolder(view, callback)

    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            row = rows[position],
            isFirst = position == 0
        )
    }

    fun swapRows(rows: List<NewsAdapterRow>) {
        this.rows.clear()
        this.rows += rows
        notifyDataSetChanged()
    }
}