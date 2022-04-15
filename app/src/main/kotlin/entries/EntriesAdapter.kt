package entries

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemEntryBinding

class EntriesAdapter(
    var screenWidth: Int = 0,
    private val callback: EntriesAdapterCallback,
) : ListAdapter<EntriesAdapterItem, EntriesAdapterViewHolder>(EntriesAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntriesAdapterViewHolder {
        val binding = ListItemEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return EntriesAdapterViewHolder(binding, screenWidth, callback)
    }

    override fun onBindViewHolder(holder: EntriesAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}