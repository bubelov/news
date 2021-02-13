package exceptions

import androidx.recyclerview.widget.DiffUtil
import db.LoggedException

class AppExceptionsAdapterDiffCallback : DiffUtil.ItemCallback<LoggedException>() {

    override fun areItemsTheSame(oldItem: LoggedException, newItem: LoggedException): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: LoggedException, newItem: LoggedException): Boolean {
        return true
    }
}