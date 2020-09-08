package co.appreactor.nextcloud.news.feeds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.nextcloud.news.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_news_feeds.*
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel

class NewsFeedsFragment : Fragment() {

    private val model: NewsFeedsFragmentModel by viewModel()

    private val adapter = NewsFeedsAdapter {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(it.title)
            .setMessage(it.toString())
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_news_feeds,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = adapter

        lifecycleScope.launchWhenResumed {
            progress.isVisible = true

            model.getFeeds().collect { feeds ->
                progress.isVisible = false
                empty.isVisible = feeds.isEmpty()
                adapter.swapItems(feeds)
            }
        }
    }
}