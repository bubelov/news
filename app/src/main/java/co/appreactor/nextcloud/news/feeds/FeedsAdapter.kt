package co.appreactor.nextcloud.news.feeds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.db.Feed

class FeedsAdapter(
    private val callback: FeedsAdapterCallback
) : ListAdapter<Feed, FeedsAdapterViewHolder>(FeedsAdapterDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedsAdapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_feed,
            parent, false
        )

        return FeedsAdapterViewHolder(view, callback)
    }

    override fun onBindViewHolder(holder: FeedsAdapterViewHolder, position: Int) {
        holder.bind(
            item = getItem(position),
            isFirst = position == 0,
            isLast = position == itemCount - 1
        )
    }
}