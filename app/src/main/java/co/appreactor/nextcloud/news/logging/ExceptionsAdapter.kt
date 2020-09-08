package co.appreactor.nextcloud.news.logging

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.db.LoggedException
import kotlinx.android.synthetic.main.row_exception.view.*

class ExceptionsAdapter(
    private val items: MutableList<LoggedException> = mutableListOf(),
    private val callback: ExceptionsAdapterCallback
) : RecyclerView.Adapter<ExceptionsAdapter.ViewHolder>() {

    class ViewHolder(
        private val view: View,
        private val callback: ExceptionsAdapterCallback
    ) :
        RecyclerView.ViewHolder(view) {

        fun bind(item: LoggedException, isFirst: Boolean) {
            view.topOffset.isVisible = isFirst

            view.primaryText.text = item.exceptionClass
            view.secondaryText.text = item.date

            view.clickableArea.setOnClickListener {
                callback.onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.row_exception,
            parent, false
        )

        return ViewHolder(view, callback)

    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            isFirst = position == 0
        )
    }

    fun swapItems(items: List<LoggedException>) {
        this.items.clear()
        this.items += items
        notifyDataSetChanged()
    }
}