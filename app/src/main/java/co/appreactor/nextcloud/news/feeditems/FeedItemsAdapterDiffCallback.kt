package co.appreactor.nextcloud.news.feeditems

import androidx.recyclerview.widget.DiffUtil

class FeedItemsAdapterDiffCallback : DiffUtil.ItemCallback<FeedItemsAdapterRow>() {
    override fun areItemsTheSame(oldItem: FeedItemsAdapterRow, newItem: FeedItemsAdapterRow): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: FeedItemsAdapterRow, newItem: FeedItemsAdapterRow): Boolean {
        return oldItem == newItem
    }
}