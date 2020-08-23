package co.appreactor.nextcloud.news

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.row_item.view.*
import java.lang.Integer.min
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
        fun bind(item: Item, feed: Feed, isFirst: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst

                image.isVisible = false
                //image.isVisible = feed.faviconLink.isNotEmpty()

                //if (feed.faviconLink.isNotEmpty()) {
                //    Picasso.get().load(feed.faviconLink).into(image)
                //}

                primaryText.text = item.title
                val date = LocalDateTime.ofEpochSecond(item.pubDate, 0, ZoneOffset.UTC)
                val dateString = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                secondaryText.text = "${feed.title} · $dateString"
                val body = Html.fromHtml(item.body).toString()
                supportingText.text = body.substring(0, min(body.length - 1, 150)) + "..."

                if (item.starred) {
                    starred.setImageResource(R.drawable.ic_baseline_star_24)
                } else {
                    starred.setImageResource(R.drawable.ic_baseline_star_border_24)
                }

                card.setOnClickListener { onClick(item) }
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
            isFirst = position == 0
        )
    }
}