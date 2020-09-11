package co.appreactor.nextcloud.news.feeds

import android.content.Intent
import android.net.Uri
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
import co.appreactor.nextcloud.news.common.showDialog
import co.appreactor.nextcloud.news.db.Feed
import kotlinx.android.synthetic.main.fragment_feeds.*
import kotlinx.coroutines.flow.collect
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class FeedsFragment : Fragment() {

    private val model: FeedsFragmentModel by viewModel()

    private val adapter = FeedsAdapter(callback = object : FeedsAdapterCallback {
        override fun onOpenWebsiteClick(feed: Feed) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(feed.link)
            startActivity(intent)
        }

        override fun onOpenRssFeedClick(feed: Feed) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(feed.url)
            startActivity(intent)
        }

        override fun onDeleteClick(feed: Feed) {
            lifecycleScope.launchWhenResumed {
                listView.isVisible = false
                progress.isVisible = true

                runCatching {
                    model.deleteFeed(feed.id)
                }.onFailure {
                    Timber.e(it)
                    showDialog(R.string.error, it.message ?: "")
                }

                listView.isVisible = true
                progress.isVisible = false
            }
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_feeds,
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
                adapter.submitList(feeds)
            }
        }
    }
}