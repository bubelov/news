package logentries

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemLogEntryBinding
import db.LogEntry

class LogEntriesAdapter :
    ListAdapter<LogEntry, LogEntriesAdapterViewHolder>(LogEntriesAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogEntriesAdapterViewHolder {
        val binding = ListItemLogEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return LogEntriesAdapterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogEntriesAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}