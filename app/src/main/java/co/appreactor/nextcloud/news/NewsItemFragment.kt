package co.appreactor.nextcloud.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.ImageSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.android.synthetic.main.fragment_news_item.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class NewsItemFragment : Fragment() {

    private val args: NewsItemFragmentArgs by navArgs()

    private val model: NewsItemFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_news_item,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        progress.isVisible = true

        lifecycleScope.launch {
            val item = model.getNewsItem(args.newsItemId)

            if (item.unread) {
                model.markAsRead(item.id)
            }

            toolbar.title = item.author

            val imageGetter = PicassoImageGetter(textView)

            val body = HtmlCompat.fromHtml(
                item.body,
                HtmlCompat.FROM_HTML_MODE_LEGACY,
                imageGetter,
                null
            ) as SpannableStringBuilder

            title.text = item.title

            val spans = body.getSpans(0, body.length - 1, Any::class.java)

            spans.forEach {
                when (it) {
                    is BulletSpan -> {
                        val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                        val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                        body.setSpan(
                            BulletSpan(gap, textView.currentTextColor, radius),
                            body.getSpanStart(it),
                            body.getSpanEnd(it),
                            0
                        )

                        body.removeSpan(it)
                    }

                    is ImageSpan -> {
                        if (body[body.getSpanEnd(it) + 1] != '\n') {
                            body.insert(body.getSpanEnd(it), "\n \n")
                        }
                    }
                }
            }

            while (body.contains("\u00A0")) {
                val index = body.indexOfFirst { it == '\u00A0' }
                body.delete(index, index + 1)
            }

            while (body.contains("\n\n\n")) {
                val index = body.indexOf("\n\n\n")
                body.delete(index, index + 1)
            }

            textView.text = body
            textView.movementMethod = LinkMovementMethod.getInstance()

            progress.isVisible = false
        }

        lifecycleScope.launchWhenResumed {
            model.getReadFlag(args.newsItemId).collect { read ->
                val menuItem = toolbar.menu.findItem(R.id.toggleRead)

                if (read) {
                    menuItem.setIcon(R.drawable.ic_baseline_visibility_24)
                    menuItem.setTitle(R.string.mark_as_unread)
                } else {
                    menuItem.setIcon(R.drawable.ic_baseline_visibility_off_24)
                    menuItem.setTitle(R.string.mark_as_read)
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            model.getStarredFlag(args.newsItemId).collect { starred ->
                val menuItem = toolbar.menu.findItem(R.id.toggleStarred)

                if (starred) {
                    menuItem.setIcon(R.drawable.ic_baseline_star_24)
                    menuItem.setTitle(R.string.unstar)
                } else {
                    menuItem.setIcon(R.drawable.ic_baseline_star_border_24)
                    menuItem.setTitle(R.string.star)
                }
            }
        }

        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.toggleRead) {
                lifecycleScope.launch {
                    model.toggleReadFlag(args.newsItemId)
                }

                return@setOnMenuItemClickListener true
            }

            if (it.itemId == R.id.toggleStarred) {
                lifecycleScope.launch {
                    model.toggleStarredFlag(args.newsItemId)
                }

                return@setOnMenuItemClickListener true
            }

            false
        }

        fab.setOnClickListener {
            lifecycleScope.launch {
                val item = model.getNewsItem(args.newsItemId)
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(item.url)
                startActivity(intent)
            }
        }
    }
}