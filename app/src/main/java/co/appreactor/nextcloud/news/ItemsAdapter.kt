package co.appreactor.nextcloud.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.row_item.view.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ItemsAdapter(
    private val items: List<Item>,
    private val feeds: List<Feed>,
    val onClick: (Item) -> Unit
) : RecyclerView.Adapter<ItemsAdapter.ViewHolder>() {

    class ViewHolder(private val view: View, private val onClick: (Item) -> Unit) : RecyclerView.ViewHolder(view) {
        fun bind(item: Item, feed: Feed, isFirst: Boolean, isLast: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst
                primaryText.text = item.title
                val date = LocalDateTime.ofEpochSecond(item.pubDate, 0, ZoneOffset.UTC)
                val dateString = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                secondaryText.text = "${feed.title} · $dateString"
                bottomOffset.isVisible = isLast

                clickableArea.setOnClickListener { onClick(item) }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.row_item,
            parent, false
        )

        return ViewHolder(view, onClick)

    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            feed = feeds.find { it.id == items[position].feedId }!!,
            isFirst = position == 0,
            isLast = position == itemCount - 1
        )
    }
}