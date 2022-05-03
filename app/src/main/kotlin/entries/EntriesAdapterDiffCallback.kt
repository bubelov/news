package entries

import androidx.recyclerview.widget.DiffUtil

class EntriesAdapterDiffCallback : DiffUtil.ItemCallback<EntriesAdapterItem>() {

    override fun areItemsTheSame(
        oldItem: EntriesAdapterItem,
        newItem: EntriesAdapterItem,
    ): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(
        oldItem: EntriesAdapterItem,
        newItem: EntriesAdapterItem,
    ): Boolean {
        return oldItem.ogImageUrl == newItem.ogImageUrl
                && oldItem.ogImageWidth == newItem.ogImageWidth
                && oldItem.ogImageHeight == newItem.ogImageHeight
                && oldItem.cropImage == newItem.cropImage
                && oldItem.title == newItem.title
                && oldItem.subtitle == newItem.subtitle
                && oldItem.summary == newItem.summary
                && oldItem.podcast == newItem.podcast
                && oldItem.podcastDownloadPercent == newItem.podcastDownloadPercent
                && oldItem.read == newItem.read
    }
}