package co.appreactor.nextcloud.news

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_news_item.*
import kotlinx.coroutines.launch

class NewsItemFragment : Fragment() {

    private val args: NewsItemFragmentArgs by navArgs()

    init {
        lifecycleScope.launch {
            whenResumed {
                val item = Gson().fromJson(args.item, Item::class.java)

                toolbar.apply {
                    setNavigationOnClickListener {
                        findNavController().popBackStack()
                    }

                    title = item.title
                }

                val imageGetter = PicassoImageGetter(textView)

                val body = if (Build.VERSION.SDK_INT >= 24) {
                    Html.fromHtml(item.body, Html.FROM_HTML_MODE_COMPACT, imageGetter, null)
                } else {
                    Html.fromHtml(item.body, imageGetter, null)
                }

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