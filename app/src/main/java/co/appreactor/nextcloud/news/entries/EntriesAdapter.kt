package co.appreactor.nextcloud.news.entries

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.nextcloud.news.R

class EntriesAdapter(
    var screenWidth: Int = 0,
    private val scope: LifecycleCoroutineScope,
    private val callback: EntriesAdapterCallback,
) : ListAdapter<EntriesAdapterItem, EntriesAdapterViewHolder>(EntriesAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntriesAdapterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_entry,
            parent, false
        )

        return EntriesAdapterViewHolder(view, screenWidth, scope, callback)

    }

    override fun onBindViewHolder(holder: EntriesAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}