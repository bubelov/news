package entries

import androidx.recyclerview.widget.DiffUtil

class EntriesAdapterDiffCallback : DiffUtil.ItemCallback<EntriesAdapterItem>() {

    override fun areItemsTheSame(
        oldItem: EntriesAdapterItem,
        newItem: EntriesAdapterItem,
    ): Boolean {
        return oldItem.entry.id == newItem.entry.id
    }

    override fun areContentsTheSame(
        oldItem: EntriesAdapterItem,
        newItem: EntriesAdapterItem,
    ): Boolean {
        return oldItem.entry.ogImageUrl == newItem.entry.ogImageUrl
                && oldItem.entry.ogImageWidth == newItem.entry.ogImageWidth
                && oldItem.entry.ogImageHeight == newItem.entry.ogImageHeight
                && oldItem.showImage == newItem.showImage
                && oldItem.cropImage == newItem.cropImage
                && oldItem.title == newItem.title
                && oldItem.subtitle == newItem.subtitle
                && oldItem.summary == newItem.summary
                && oldItem.read == newItem.read
    }
}