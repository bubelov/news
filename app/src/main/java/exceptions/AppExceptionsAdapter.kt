package exceptions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import co.appreactor.news.databinding.ListItemAppExceptionBinding
import db.LoggedException

class AppExceptionsAdapter(
    private val callback: AppExceptionsAdapterCallback,
) : ListAdapter<LoggedException, AppExceptionsAdapterViewHolder>(AppExceptionsAdapterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppExceptionsAdapterViewHolder {
        val binding = ListItemAppExceptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return AppExceptionsAdapterViewHolder(binding, callback)
    }

    override fun onBindViewHolder(holder: AppExceptionsAdapterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}