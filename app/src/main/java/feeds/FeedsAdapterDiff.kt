package feeds

import androidx.recyclerview.widget.DiffUtil
import co.appreactor.news.db.Feed

class FeedsAdapterDiff : DiffUtil.ItemCallback<Feed>() {

    override fun areItemsTheSame(oldItem: Feed, newItem: Feed): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Feed, newItem: Feed): Boolean {
        return oldItem == newItem
    }
}