package logentries

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemLogEntryBinding
import db.LogEntry
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class LogEntriesAdapterViewHolder(
    private val binding: ListItemLogEntryBinding,
) : RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(item: LogEntry) {
        binding.apply {
            primaryText.text = item.message
            val instant = Instant.parse(item.date)
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            secondaryText.text = "${format.format(Date(instant.millis))} | ${item.tag}"
        }
    }
}