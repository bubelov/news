package hnentries

import androidx.recyclerview.widget.DiffUtil

class HnEntriesAdapterDiffCallback : DiffUtil.ItemCallback<HnEntriesAdapter.Item>() {

    override fun areItemsTheSame(
        oldItem: HnEntriesAdapter.Item,
        newItem: HnEntriesAdapter.Item,
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: HnEntriesAdapter.Item,
        newItem: HnEntriesAdapter.Item,
    ): Boolean {
        return oldItem == newItem
    }
}