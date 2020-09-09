package co.appreactor.nextcloud.news.feeditems

import androidx.recyclerview.widget.DiffUtil

class FeedItemsAdapterDiffCallback(
    private val oldItems: List<FeedItemsAdapterRow>,
    private val newItems: List<FeedItemsAdapterRow>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldItems.size
    }

    override fun getNewListSize(): Int {
        return newItems.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldItems[oldItemPosition].id == newItems[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldItems[oldItemPosition] == newItems[newItemPosition]
    }
}