package feeds

import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.ListItemFeedBinding

class FeedsAdapter(
    private val callback: Callback,
) : ListAdapter<FeedsAdapter.Item, FeedsAdapter.ViewHolder>(
    Diff(),
) {

    data class Item(
        val id: String,
        val title: String,
        val selfLink: String,
        val alternateLink: String,
        val unreadCount: Long,
    )

    class ViewHolder(
        private val binding: ListItemFeedBinding,
        private val callback: Callback,
    ) : RecyclerView.ViewHolder(
        binding.root,
    ) {

        fun bind(item: Item) {
            binding.apply {
                primaryText.text = item.title
                secondaryText.text = item.selfLink

                unread.isVisible = false

                unread.isVisible = item.unreadCount > 0
                unread.text = item.unreadCount.toString()

                actions.setOnClickListener {
                    val popup = PopupMenu(root.context, actions)
                    val inflater: MenuInflater = popup.menuInflater
                    inflater.inflate(R.menu.menu_feed_actions, popup.menu)
                    popup.show()

                    popup.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.openSettings -> {
                                callback.onSettingsClick(item)
                            }

                            R.id.openHtmlLink -> {
                                callback.onOpenAlternateLinkClick(item)
                            }

                            R.id.openLink -> {
                                callback.onOpenSelfLinkClick(item)
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

                root.setOnClickListener { callback.onClick(item) }
            }
        }
    }

    interface Callback {
        fun onClick(item: Item)
        fun onSettingsClick(item: Item)
        fun onOpenSelfLinkClick(item: Item)
        fun onOpenAlternateLinkClick(item: Item)
        fun onRenameClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    class Diff : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem.id == newItem.id
                && oldItem.title == newItem.title
                && oldItem.selfLink == newItem.selfLink
                && oldItem.alternateLink == newItem.alternateLink
                && oldItem.unreadCount == newItem.unreadCount
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return ViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}