package hnentries

import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemHnEntryBinding

class HnEntriesAdapter(
    private val activity: FragmentActivity,
    private val callback: HnEntriesAdapterCallback,
) : ListAdapter<HnEntriesAdapter.Item, HnEntriesAdapterViewHolder>(HnEntriesAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HnEntriesAdapterViewHolder {
        val binding = ListItemHnEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return HnEntriesAdapterViewHolder(
            binding = binding,
            callback = callback,
        )
    }

    override fun onBindViewHolder(holder: HnEntriesAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    private fun screenWidth(): Int {
        return when {
            Build.VERSION.SDK_INT >= 31 -> {
                val windowManager = activity.getSystemService<WindowManager>()!!
                windowManager.currentWindowMetrics.bounds.width()
            }
            Build.VERSION.SDK_INT >= 30 -> {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                activity.display?.getRealMetrics(displayMetrics)
                displayMetrics.widthPixels
            }
            else -> {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
                displayMetrics.widthPixels
            }
        }
    }

    data class Item(
        val id: Long,
        val time: Long,
        val title: String,
        val by: String,
        val text: String,
        val kidsCount: Int
    )
}