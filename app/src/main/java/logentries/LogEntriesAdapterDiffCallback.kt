package logentries

import androidx.recyclerview.widget.DiffUtil
import db.LogEntry

class LogEntriesAdapterDiffCallback : DiffUtil.ItemCallback<LogEntry>() {

    override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
        return true
    }
}