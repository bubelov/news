package co.appreactor.nextcloud.news.starred

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
import co.appreactor.nextcloud.news.news.NewsAdapter
import co.appreactor.nextcloud.news.news.NewsAdapterCallback
import co.appreactor.nextcloud.news.news.NewsAdapterRow
import co.appreactor.nextcloud.news.podcasts.playPodcast
import kotlinx.android.synthetic.main.fragment_starred_news.empty
import kotlinx.android.synthetic.main.fragment_starred_news.itemsView
import kotlinx.android.synthetic.main.fragment_starred_news.progress
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel

class StarredNewsFragment : Fragment() {

    private val model: StarredNewsFragmentModel by viewModel()

    private val adapter = NewsAdapter(
        callback = object : NewsAdapterCallback {
            override fun onRowClick(row: NewsAdapterRow) {
                val action = StarredNewsFragmentDirections.actionStarredNewsFragmentToNewsItemFragment(row.id)
                findNavController().navigate(action)
            }

            override fun onDownloadPodcastClick(row: NewsAdapterRow) {
                model.downloadPodcast(row.id)
            }

            override fun onPlayPodcastClick(row: NewsAdapterRow) {
                lifecycleScope.launch {
                    val podcast = model.getNewsItem(row.id).first()!!
                    playPodcast(podcast)
                }
            }
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_starred_news,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launchWhenResumed {
            initNewsList()
        }
    }

    private suspend fun initNewsList() {
        progress.isVisible = true

        itemsView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
        }

        itemsView.adapter = adapter

        model.getNewsItems().collect { rows ->
            progress.isVisible = false
            empty.isVisible = rows.isEmpty()
            adapter.swapRows(rows)
        }
    }
}