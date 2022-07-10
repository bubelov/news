package entries

import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemEntryBinding

class EntriesAdapter(
    private val activity: FragmentActivity,
    private val callback: EntriesAdapterCallback,
) : ListAdapter<EntriesAdapterItem, EntriesAdapterViewHolder>(EntriesAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntriesAdapterViewHolder {
        val binding = ListItemEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return EntriesAdapterViewHolder(
            binding = binding,
            callback = callback,
            screenWidth = screenWidth(),
        )
    }

    override fun onBindViewHolder(holder: EntriesAdapterViewHolder, position: Int) {
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
}