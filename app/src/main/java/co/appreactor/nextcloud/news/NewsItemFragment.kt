package co.appreactor.nextcloud.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.android.synthetic.main.fragment_news_item.*
import kotlinx.android.synthetic.main.fragment_news_item.toolbar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class NewsItemFragment : Fragment() {

    private val args: NewsItemFragmentArgs by navArgs()

    private val model: NewsItemFragmentModel by viewModel()

    init {
        lifecycleScope.launch {
            whenResumed {
                val item = model.getNewsItem(args.newsItemId)

                if (item.unread) {
                    model.markAsRead(item.id)
                }

                toolbar.apply {
                    setNavigationOnClickListener {
                        findNavController().popBackStack()
                    }

                    title = item.title

                    inflateMenu(R.menu.menu_news_item)

                    lifecycleScope.launchWhenResumed {
                        model.getReadFlag(item.id).collect { read ->
                            val menuItem = menu.findItem(R.id.toggleRead)

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
                        model.getStarredFlag(item.id).collect { starred ->
                            val menuItem = menu.findItem(R.id.toggleStarred)

                            if (starred) {
                                menuItem.setIcon(R.drawable.ic_baseline_star_24)
                                menuItem.setTitle(R.string.unstar)
                            } else {
                                menuItem.setIcon(R.drawable.ic_baseline_star_border_24)
                                menuItem.setTitle(R.string.star)
                            }
                        }
                    }

                    setOnMenuItemClickListener {
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
                }

                val imageGetter = PicassoImageGetter(textView)
                val body = HtmlCompat.fromHtml(
                    item.body,
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                    imageGetter,
                    null
                )

                textView.text = body

                fab.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse(item.url)
                    startActivity(intent)
                }
            }
        }
    }

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
}