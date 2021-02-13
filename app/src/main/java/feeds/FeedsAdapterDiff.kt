package feeds

import androidx.recyclerview.widget.DiffUtil

class FeedsAdapterDiff : DiffUtil.ItemCallback<FeedsAdapterItem>() {

    override fun areItemsTheSame(oldItem: FeedsAdapterItem, newItem: FeedsAdapterItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: FeedsAdapterItem, newItem: FeedsAdapterItem): Boolean {
        return oldItem == newItem
    }
}