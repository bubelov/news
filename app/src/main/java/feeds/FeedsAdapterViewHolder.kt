package feeds

import android.view.MenuInflater
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.ListItemFeedBinding
import db.Feed

class FeedsAdapterViewHolder(
    private val binding: ListItemFeedBinding,
    private val callback: FeedsAdapterCallback
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Feed) {
        binding.apply {
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
                val popup = PopupMenu(root.context, actions)
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

            root.setOnClickListener {
                callback.onFeedClick(item)
            }
        }
    }
}