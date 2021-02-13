package exceptions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemLoggedExceptionBinding
import db.LoggedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class AppExceptionsAdapter(
    private val items: MutableList<LoggedException> = mutableListOf(),
    private val callback: AppExceptionsAdapterCallback
) : RecyclerView.Adapter<AppExceptionsAdapter.ViewHolder>() {

    class ViewHolder(
        private val view: ListItemLoggedExceptionBinding,
        private val callback: AppExceptionsAdapterCallback
    ) :
        RecyclerView.ViewHolder(view.root) {

        fun bind(item: LoggedException, isFirst: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst

                primaryText.text = item.exceptionClass

                val instant = Instant.parse(item.date)
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                secondaryText.text = format.format(Date(instant.millis))

                clickableArea.setOnClickListener {
                    callback.onClick(item)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ListItemLoggedExceptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding, callback)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            isFirst = position == 0
        )
    }

    suspend fun swapItems(newItems: List<LoggedException>) {
        val diff = withContext(Dispatchers.IO) {
            DiffUtil.calculateDiff(AppExceptionsAdapterDiffCallback(items, newItems))
        }

        diff.dispatchUpdatesTo(this)
        this.items.clear()
        this.items += newItems
    }
}