package enclosures

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemEnclosureBinding
import db.Link
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EnclosuresAdapter(private val listener: Callback) :
    ListAdapter<EnclosuresAdapter.Item, EnclosuresAdapter.ViewHolder>(DiffCallback()) {

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

    interface Callback {
        fun onDownloadClick(item: Item)
        fun onPlayClick(item: Item)
        fun onDeleteClick(item: Item)
    }

    data class Item(
        val entryId: String,
        val entryTitle: String,
        val entryPublished: OffsetDateTime,
        val enclosure: Link,
    )

    class ViewHolder(
        private val binding: ListItemEnclosureBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, listener: Callback) = binding.apply {
            binding.primaryText.text = item.entryTitle
            binding.secondaryText.text = DATE_TIME_FORMAT.format(item.entryPublished)
            binding.supportingText.isVisible = false
            binding.delete.isVisible = item.enclosure.extEnclosureDownloadProgress == 1.0
            binding.delete.setOnClickListener { listener.onDeleteClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Item>() {

        override fun areItemsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return newItem.entryId == oldItem.entryId
                    && newItem.enclosure.href == oldItem.enclosure.href
        }

        override fun areContentsTheSame(
            oldItem: Item,
            newItem: Item,
        ): Boolean {
            return true
        }
    }

    companion object {
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        )
    }
}