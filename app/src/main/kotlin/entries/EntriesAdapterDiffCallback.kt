package entries

import androidx.recyclerview.widget.DiffUtil

class EntriesAdapterDiffCallback : DiffUtil.ItemCallback<EntriesAdapter.Item>() {

    override fun areItemsTheSame(
        oldItem: EntriesAdapter.Item,
        newItem: EntriesAdapter.Item,
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: EntriesAdapter.Item,
        newItem: EntriesAdapter.Item,
    ): Boolean {
        return oldItem == newItem
    }
}