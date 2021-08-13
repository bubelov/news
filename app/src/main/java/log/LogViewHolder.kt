package log

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemLogBinding
import db.Log
import org.joda.time.Instant
import java.text.DateFormat
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
            secondaryText.text = "${format.format(Date(instant.millis))} | ${item.tag}"

            if (item.stackTrace.isNullOrBlank()) {
                root.setOnClickListener(null)
            } else {
                root.setOnClickListener { callback.onClick(item) }
            }
        }
    }
}