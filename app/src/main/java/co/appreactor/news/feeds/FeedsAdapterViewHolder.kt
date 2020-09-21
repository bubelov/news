package co.appreactor.news.feeds

import android.view.MenuInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.db.Feed
import kotlinx.android.synthetic.main.list_item_feed.view.*

class FeedsAdapterViewHolder(
    private val view: View,
    private val callback: FeedsAdapterCallback
) : RecyclerView.ViewHolder(view) {

    fun bind(item: Feed) {
        view.apply {
            primaryText.text = item.title
            secondaryText.text = item.alternateLink

            errorText.isVisible = false // TODO

//            if (!item.lastUpdateError.isBlank()) {
//                errorText.isVisible = true
//                errorText.text = item.lastUpdateError
//            } else {
//                errorText.isVisible = false
//            }

            actions.setOnClickListener {
                val popup = PopupMenu(context, actions)
                val inflater: MenuInflater = popup.menuInflater
                inflater.inflate(R.menu.menu_feed_actions, popup.menu)
                popup.show()

                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.openHtmlLink -> {
                            callback.onOpenHtmlLinkClick(item)
                        }

                        R.id.openLink -> {
                            callback.openLinkClick(item)
                        }

                        R.id.renameFeed -> {
                            callback.onRenameClick(item)
                        }

                        R.id.deleteFeed -> {
                            callback.onDeleteClick(item)
                        }
                    }

                    true
                }
            }
        }
    }
}