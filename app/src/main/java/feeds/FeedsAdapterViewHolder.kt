package feeds

import android.view.MenuInflater
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.R
import co.appreactor.news.databinding.ListItemFeedBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect

class FeedsAdapterViewHolder(
    private val binding: ListItemFeedBinding,
    private val scope: LifecycleCoroutineScope,
    private val callback: FeedsAdapterCallback
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: FeedsAdapterItem) {
        binding.apply {
            primaryText.text = item.title
            secondaryText.text = item.selfLink

            errorText.isVisible = false // TODO

//            if (!item.lastUpdateError.isBlank()) {
//                errorText.isVisible = true
//                errorText.text = item.lastUpdateError
//            } else {
//                errorText.isVisible = false
//            }

            unread.isVisible = false

            val job: Job? = unread.tag as Job?
            job?.cancel()

            unread.tag = scope.launchWhenResumed {
                item.unreadCount.collect {
                    unread.isVisible = it > 0
                    unread.text = it.toString()

                    unread.alpha = 0f
                    unread.animate().alpha(1f).duration = 150
                }
            }

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