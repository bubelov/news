package co.appreactor.news.entry

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.news.R
import co.appreactor.news.common.showDialog
import co.appreactor.news.db.Entry
import kotlinx.android.synthetic.main.fragment_entry.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class EntryFragment : Fragment() {

    private val args: EntryFragmentArgs by navArgs()

    private val model: EntryFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_entry,
            container,
            false
        )
    }

    @SuppressLint("NewApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        progress.isVisible = true

        lifecycleScope.launchWhenResumed {
            val entry = model.getEntry(args.entryId)

            if (entry == null) {
                showDialog(R.string.error, getString(R.string.cannot_find_entry_with_id_s, args.entryId)) {
                    findNavController().popBackStack()
                }

                return@launchWhenResumed
            }

            viewEntry(entry)
        }

        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            fab.isVisible = scrollView.canScrollVertically(1)
        }
    }

    private suspend fun viewEntry(entry: Entry) {
        toolbar.title = model.getFeed(entry.feedId)?.title ?: getString(R.string.unknown_feed)
        title.text = entry.title
        date.text = model.getDate(entry)

        runCatching {
            fillSummary(entry)
            progress.isVisible = false
        }.onFailure {
            progress.isVisible = false

            showDialog(R.string.error, R.string.cannot_show_content) {
                findNavController().popBackStack()
            }

            return
        }

        model.markAsViewed(entry)

        lifecycleScope.launchWhenResumed {
            model.getBookmarked(entry).collect { bookmarked ->
                val menuItem = toolbar.menu.findItem(R.id.toggleBookmarked)

                if (bookmarked) {
                    menuItem.setIcon(R.drawable.ic_baseline_bookmark_24)
                    menuItem.setTitle(R.string.remove_bookmark)
                } else {
                    menuItem.setIcon(R.drawable.ic_baseline_bookmark_border_24)
                    menuItem.setTitle(R.string.bookmark)
                }
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.share -> {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, entry.title)
                        putExtra(Intent.EXTRA_TEXT, entry.link)
                    }

                    startActivity(Intent.createChooser(intent, ""))
                }

                R.id.toggleBookmarked -> lifecycleScope.launchWhenResumed {
                    model.toggleBookmarked(args.entryId)
                }
            }

            true
        }

        fab.setOnClickListener {
            lifecycleScope.launch {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(entry.link)
                startActivity(intent)
            }
        }
    }

    private fun fillSummary(entry: Entry) {
        val summary = HtmlCompat.fromHtml(
            entry.summary,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            TextViewImageGetter(summaryView),
            null
        ) as SpannableStringBuilder

        if (summary.isBlank()) {
            return
        }

        summary.applyStyle(summaryView)

        while (summary.contains("\u00A0")) {
            val index = summary.indexOfFirst { it == '\u00A0' }
            summary.delete(index, index + 1)
        }

        while (summary.contains("\n\n\n")) {
            val index = summary.indexOf("\n\n\n")
            summary.delete(index, index + 1)
        }

        while (summary.startsWith("\n\n")) {
            summary.delete(0, 1)
        }

        while (summary.endsWith("\n\n")) {
            summary.delete(summary.length - 2, summary.length - 1)
        }

        summaryView.text = summary
        summaryView.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun SpannableStringBuilder.applyStyle(textView: TextView) {
        val spans = getSpans(0, length - 1, Any::class.java)

        spans.forEach {
            when (it) {
                is BulletSpan -> {
                    val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                    val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                    val span = if (Build.VERSION.SDK_INT >= 28) {
                        BulletSpan(gap, textView.currentTextColor, radius)
                    } else {
                        BulletSpan(gap, textView.currentTextColor)
                    }

                    setSpan(
                        span,
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is QuoteSpan -> {
                    val color = date.currentTextColor
                    val stripe = resources.getDimension(R.dimen.quote_stripe_width).toInt()
                    val gap = resources.getDimension(R.dimen.quote_gap).toInt()

                    val span = if (Build.VERSION.SDK_INT >= 28) {
                        QuoteSpan(color, stripe, gap)
                    } else {
                        QuoteSpan(color)
                    }

                    setSpan(
                        span,
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is URLSpan -> {
                    if (it.url.startsWith("#")) {
                        removeSpan(it)
                    }
                }
            }
        }
    }
}