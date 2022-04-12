package enclosures

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemEnclosureBinding
import okhttp3.HttpUrl

class EnclosuresAdapter(private val listener: Listener) :
    ListAdapter<EnclosuresAdapter.Item, EnclosuresAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ListItemEnclosureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    data class Item(
        val entryId: String,
        val title: String,
        val url: HttpUrl,
        val downloaded: Boolean,
    )

    interface Listener {
        fun onDeleteClick(item: Item)
    }

    class ViewHolder(
        private val binding: ListItemEnclosureBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, listener: Listener) = binding.apply {
            binding.primaryText.text = item.title
            binding.secondaryText.text = item.url.toString()

            binding.delete.isInvisible = !item.downloaded

            binding.delete.setOnClickListener {
                listener.onDeleteClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Item>() {

        override fun areItemsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return false
        }

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return false
        }
    }
}