package enclosures

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemEnclosureBinding
import db.Link

class EnclosuresAdapter(private val listener: Listener) :
    ListAdapter<Link, EnclosuresAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ListItemEnclosureBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), listener)
    }

    interface Listener {
        fun onDeleteClick(item: Link)
    }

    class ViewHolder(
        private val binding: ListItemEnclosureBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Link, listener: Listener) = binding.apply {
            binding.primaryText.text = item.title
            binding.secondaryText.text = item.href.toString()
            binding.delete.isVisible = item.extEnclosureDownloadProgress == 1.0
            binding.delete.setOnClickListener { listener.onDeleteClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Link>() {

        override fun areItemsTheSame(
            oldItem: Link,
            newItem: Link,
        ): Boolean {
            return false
        }

        override fun areContentsTheSame(
            oldItem: Link,
            newItem: Link,
        ): Boolean {
            return false
        }
    }
}