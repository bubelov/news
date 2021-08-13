package log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemLogBinding
import db.Log

class LogAdapter(
    private val callback: LogCallback,
) : ListAdapter<Log, LogViewHolder>(LogDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ListItemLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )

        return LogViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}