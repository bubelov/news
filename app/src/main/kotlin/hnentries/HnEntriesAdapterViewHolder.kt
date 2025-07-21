package hnentries

import android.text.Html
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import co.appreactor.news.databinding.ListItemHnEntryBinding
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class HnEntriesAdapterViewHolder(
    private val binding: ListItemHnEntryBinding,
    private val callback: HnEntriesAdapterCallback,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: HnEntriesAdapter.Item) = binding.apply {

        val created = OffsetDateTime.ofInstant(Instant.ofEpochSecond(item.time), ZoneOffset.UTC)
        val difference = Duration.between(created,OffsetDateTime.now(ZoneOffset.UTC))

        var dateString = ""
        val days = difference.toDays()
        if (days == 1L) {
            dateString = "${days} day"
        }else if(days > 0){
            dateString = "${days} days"
        }else
        {
            val hours = difference.toHours()
            if (hours == 1L) {
                dateString = "${hours} hour"
            }else if (hours > 0){
                dateString = "${hours} hours"
            }else{
                val minutes = difference.toMinutes()
                if (minutes == 1L) {
                    dateString = "${minutes} minute"
                }else if (minutes > 0){
                    dateString = "${minutes} minutes"
                }else{
                    val seconds = difference.seconds
                    dateString = "${seconds} seconds"
                }

            }

        }

        if (item.text == "Go back")
        {
            headerText.isVisible = false
            contentText.text = item.text
        }else
        {
            headerText.text = " ${item.by} | ${dateString} ago | ${item.kidsCount} comments"
            contentText.text = Html.fromHtml(item.text, Html.FROM_HTML_MODE_COMPACT).toString()

        }

        root.setOnClickListener { callback.onItemClick(item) }
    }
}