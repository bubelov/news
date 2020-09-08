package co.appreactor.nextcloud.news.feeds

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.db.NewsFeed
import kotlinx.android.synthetic.main.row_news_feed.view.*

class NewsFeedsAdapter(
    private val items: MutableList<NewsFeed> = mutableListOf(),
    private val callback: NewsFeedsAdapterCallback
) : RecyclerView.Adapter<NewsFeedsAdapter.ViewHolder>() {

    class ViewHolder(
        private val view: View,
        private val callback: NewsFeedsAdapterCallback
    ) :
        RecyclerView.ViewHolder(view) {

        fun bind(item: NewsFeed, isFirst: Boolean) {
            view.topOffset.isVisible = isFirst

            view.primaryText.text = item.title
            view.secondaryText.text = item.link

            view.clickableArea.setOnClickListener {
                callback.onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.row_news_feed,
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

    fun swapItems(items: List<NewsFeed>) {
        this.items.clear()
        this.items += items
        notifyDataSetChanged()
    }
}