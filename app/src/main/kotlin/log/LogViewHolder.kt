package log

import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemLogBinding
import db.Log
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class LogViewHolder(
    private val binding: ListItemLogBinding,
    private val callback: LogCallback,
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: Log) {
        binding.apply {
            primaryText.text = item.message
            val date = OffsetDateTime.parse(item.date)
            val dateString = dateFormatter.format(date)

            secondaryText.text = if (item.tag.isNotBlank()) {
                "$dateString | ${item.tag}"
            } else {
                dateString
            }

            if (item.stackTrace.isNotBlank()) {
                trace.isVisible = true
                root.setOnClickListener { callback.onClick(item) }
            } else {
                trace.isVisible = false
                root.setOnClickListener(null)
            }
        }
    }
}