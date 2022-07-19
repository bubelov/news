package feeds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.ListItemFeedBinding
import okhttp3.HttpUrl

class FeedsAdapter(
    private val callback: Callback,
) : ListAdapter<FeedsAdapter.Item, FeedsAdapter.ItemViewHolder>(
    Diff(),
) {

    data class Item(
        val id: String,
        val title: String,
        val selfLink: HttpUrl,
        val alternateLink: HttpUrl?,
        val unreadCount: Long,
        val confUseBuiltInBrowser: Boolean,
    )

    class ItemViewHolder(
        private val binding: ListItemFeedBinding,
        private val callback: Callback,
    ) : RecyclerView.ViewHolder(
        binding.root,
    ) {

        fun bind(item: Item) {
            binding.apply {
                primaryText.text = item.title
                secondaryText.text = item.selfLink.toString()

                unreadCount.isVisible = item.unreadCount > 0
                unreadCount.text = item.unreadCount.toString()

                actions.setOnClickListener {
                    val popup = PopupMenu(root.context, actions)

                    popup.apply {
                        menuInflater.inflate(R.menu.menu_feed_actions, popup.menu)
                        menu.findItem(R.id.openAlternateLink)!!.isVisible = item.alternateLink != null

                        setOnMenuItemClickListener {
                            when (it.itemId) {
                                R.id.openSettings -> {
                                    callback.onSettingsClick(item)
                                }

                                R.id.openSelfLink -> {
                                    callback.onOpenSelfLinkClick(item)
                                }

                                R.id.openAlternateLink -> {
                                    callback.onOpenAlternateLinkClick(item)
                                }

                                R.id.rename -> {
                                    callback.onRenameClick(item)
                                }

                                R.id.delete -> {
                                    callback.onDeleteClick(item)
                                }
                            }

                            true
                        }

                        show()
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

        override fun areContentsTheSame(oldItem: Item, newItem: Item) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ListItemFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return ItemViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}