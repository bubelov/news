package log

import androidx.recyclerview.widget.DiffUtil
import db.Log

class LogDiffCallback : DiffUtil.ItemCallback<Log>() {

    override fun areItemsTheSame(oldItem: Log, newItem: Log): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Log, newItem: Log): Boolean {
        return true
    }
}