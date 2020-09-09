package co.appreactor.nextcloud.news.feeds

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.db.Feed
import kotlinx.android.synthetic.main.list_item_feed.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedsAdapter(
    private val items: MutableList<Feed> = mutableListOf(),
    private val callback: FeedsAdapterCallback
) : RecyclerView.Adapter<FeedsAdapter.ViewHolder>() {

    class ViewHolder(
        private val view: View,
        private val callback: FeedsAdapterCallback
    ) :
        RecyclerView.ViewHolder(view) {

        fun bind(item: Feed, isFirst: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst

                primaryText.text = item.title
                secondaryText.text = item.link

                clickableArea.setOnClickListener {
                    callback.onClick(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_feed,
            parent, false
        )

        return ViewHolder(view, callback)

    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            isFirst = position == 0
        )
    }

    suspend fun swapItems(newItems: List<Feed>) {
        val diff = withContext(Dispatchers.IO) {
            DiffUtil.calculateDiff(FeedsAdapterDiffCallback(items, newItems))
        }

        diff.dispatchUpdatesTo(this)
        this.items.clear()
        this.items += newItems
    }
}