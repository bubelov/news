package entries

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemEntryBinding

class EntriesAdapter(
    private val callback: EntriesAdapterCallback,
    private val screenWidth: Int,
) : ListAdapter<EntriesAdapterItem, EntriesAdapterViewHolder>(EntriesAdapterDiffCallback()) {

    init {
        if (screenWidth == 0) {
            throw Exception("Invalid screen width")
        }
    }

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