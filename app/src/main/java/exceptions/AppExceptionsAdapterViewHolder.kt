package exceptions

import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemAppExceptionBinding
import db.LoggedException
import org.joda.time.Instant
import java.text.DateFormat
import java.util.*

class AppExceptionsAdapterViewHolder(
    private val binding: ListItemAppExceptionBinding,
    private val callback: AppExceptionsAdapterCallback,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: LoggedException) {
        binding.apply {
            primaryText.text = item.exceptionClass

            val instant = Instant.parse(item.date)
            val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            secondaryText.text = format.format(Date(instant.millis))

            supportingText.isVisible = item.message.isNotBlank()
            supportingText.text = item.message

            root.setOnClickListener {
                callback.onClick(item)
            }
        }
    }
}