package co.appreactor.nextcloud.news

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
                }

                val imageGetter = PicassoImageGetter(textView)
                val body = HtmlCompat.fromHtml(
                    item.body,
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                    imageGetter,
                    null
                )

                textView.text = body
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