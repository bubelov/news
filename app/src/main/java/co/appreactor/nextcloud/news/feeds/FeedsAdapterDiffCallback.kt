package co.appreactor.nextcloud.news.feeds

import androidx.recyclerview.widget.DiffUtil
import co.appreactor.nextcloud.news.db.Feed

class FeedsAdapterDiffCallback(
    private val oldItems: List<Feed>,
    private val newItems: List<Feed>
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