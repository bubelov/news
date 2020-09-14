package co.appreactor.nextcloud.news.feeditems

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.nextcloud.news.R

class FeedItemsAdapter(
    var screenWidth: Int = 0,
    private val scope: LifecycleCoroutineScope,
    private val callback: FeedItemsAdapterCallback,
) : ListAdapter<FeedItemsAdapterItem, FeedItemsAdapterViewHolder>(FeedItemsAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedItemsAdapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_feed_item,
            parent, false
        )

        return FeedItemsAdapterViewHolder(view, screenWidth, scope, callback)

    }

    override fun onBindViewHolder(holder: FeedItemsAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}