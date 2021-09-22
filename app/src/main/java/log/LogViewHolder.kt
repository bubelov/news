package log

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemLogBinding
import db.Log
import java.text.DateFormat
import java.time.Instant
import java.util.Date

class LogViewHolder(
    private val binding: ListItemLogBinding,
    private val callback: LogCallback,
) : RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(item: Log) {
        binding.apply {
            primaryText.text = item.message
            val instant = Instant.parse(item.date)
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val date = format.format(Date(instant.toEpochMilli()))

            secondaryText.text = if (item.tag.isNotBlank()) {
                "$date | ${item.tag}"
            } else {
                date
            }

            if (item.stackTrace.isNotBlank()) {
                root.setOnClickListener { callback.onClick(item) }
            } else {
                root.setOnClickListener(null)
            }
        }
    }
}