package co.appreactor.nextcloud.news

import android.os.Build
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
    private val items: MutableList<Item>,
    private val feeds: MutableList<Feed>,
    var onClick: ((Item) -> Unit)? = null
) : RecyclerView.Adapter<ItemsAdapter.ViewHolder>() {

    class ViewHolder(private val view: View, private val onClick: ((Item) -> Unit)?) : RecyclerView.ViewHolder(view) {
        fun bind(item: Item, feed: Feed, isFirst: Boolean) {
            view.apply {
                topOffset.isVisible = isFirst

                image.isVisible = false // TODO

                primaryText.text = item.title
                val date = LocalDateTime.ofEpochSecond(item.pubDate, 0, ZoneOffset.UTC)
                val dateString = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                secondaryText.text = resources.getString(R.string.s_s_s, feed.title, "·", dateString)

                val body: String = if (Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(item.body, Html.FROM_HTML_MODE_COMPACT).toString()
                } else {
                    Html.fromHtml(item.body).toString()
                }

                val shortBody = body.substring(0, min(body.length - 1, 150)) + "..."
                supportingText.text = shortBody.trimStart { !it.isLetterOrDigit() }

                if (!item.unread) {
                    primaryText.isEnabled = false
                    secondaryText.isEnabled = false
                    supportingText.isEnabled = false
                }

                card.setOnClickListener {
                    onClick?.invoke(item)
                }
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

    fun swapItems(items: List<Item>, feeds: List<Feed>) {
        this.items.clear()
        this.items += items

        this.feeds.clear()
        this.feeds += feeds

        notifyDataSetChanged()
    }

    fun updateItem(item: Item) {
        val index = items.indexOfFirst { it.id == item.id }
        items[index] = item
        notifyItemChanged(index)
    }
}