package feeds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemFeedBinding
import db.Feed

class FeedsAdapter(
    private val callback: FeedsAdapterCallback
) : ListAdapter<Feed, FeedsAdapterViewHolder>(FeedsAdapterDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedsAdapterViewHolder {
        val binding = ListItemFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return FeedsAdapterViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: FeedsAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}