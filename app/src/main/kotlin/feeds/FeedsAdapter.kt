package feeds

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemFeedBinding

class FeedsAdapter(
    private val scope: LifecycleCoroutineScope,
    private val callback: FeedsAdapterCallback
) : ListAdapter<FeedsAdapterItem, FeedsAdapterViewHolder>(FeedsAdapterDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedsAdapterViewHolder {
        val binding = ListItemFeedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return FeedsAdapterViewHolder(binding, scope, callback)
    }

    override fun onBindViewHolder(holder: FeedsAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}