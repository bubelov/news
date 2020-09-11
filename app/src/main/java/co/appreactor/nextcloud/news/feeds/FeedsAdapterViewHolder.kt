package co.appreactor.nextcloud.news.feeds

import android.view.MenuInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.db.Feed
import kotlinx.android.synthetic.main.list_item_feed.view.*

class FeedsAdapterViewHolder(
    private val view: View,
    private val callback: FeedsAdapterCallback
) : RecyclerView.ViewHolder(view) {

    fun bind(item: Feed, isFirst: Boolean) {
        view.apply {
            topOffset.isVisible = isFirst

            primaryText.text = item.title
            secondaryText.text = item.link

            if (!item.lastUpdateError.isNullOrBlank()) {
                errorText.isVisible = true
                errorText.text = item.lastUpdateError
            } else {
                errorText.isVisible = false
            }

            actions.setOnClickListener {
                val popup = PopupMenu(context, actions)
                val inflater: MenuInflater = popup.menuInflater
                inflater.inflate(R.menu.menu_feed_actions, popup.menu)
                popup.show()

                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.openWebsite -> {
                            callback.onOpenWebsiteClick(item)
                        }

                        R.id.openRssFeed -> {
                            callback.onOpenRssFeedClick(item)
                        }

                        R.id.delete -> {
                            callback.onDeleteClick(item)
                        }
                    }

                    true
                }
            }
        }
    }
}