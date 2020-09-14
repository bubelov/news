package co.appreactor.nextcloud.news.feeditems

import androidx.recyclerview.widget.DiffUtil

class FeedItemsAdapterDiffCallback : DiffUtil.ItemCallback<FeedItemsAdapterItem>() {

    override fun areItemsTheSame(oldItem: FeedItemsAdapterItem, newItem: FeedItemsAdapterItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: FeedItemsAdapterItem, newItem: FeedItemsAdapterItem): Boolean {
        return oldItem.title == newItem.title
                && oldItem.subtitle == newItem.subtitle
                && oldItem.unread == newItem.unread
                && oldItem.podcast == newItem.podcast
                && oldItem.showImage == newItem.showImage
                && oldItem.cropImage == newItem.cropImage
    }
}