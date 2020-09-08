package co.appreactor.nextcloud.news.news

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
import co.appreactor.nextcloud.news.podcasts.playPodcast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_news.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class NewsFragment : Fragment() {

    private val model: NewsFragmentModel by viewModel()

    private val adapter = NewsAdapter(
        callback = object : NewsAdapterCallback {
            override fun onRowClick(row: NewsAdapterRow) {
                val action = NewsFragmentDirections.actionNewsFragmentToNewsItemFragment(row.id)
                findNavController().navigate(action)
            }

            override fun onDownloadPodcastClick(row: NewsAdapterRow) {
                lifecycleScope.launchWhenResumed {
                    runCatching {
                        model.downloadPodcast(row.id)
                    }.apply {
                        if (isFailure) {
                            Timber.e(exceptionOrNull())

                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(getString(R.string.error))
                                .setMessage(exceptionOrNull()?.message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
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
            R.layout.fragment_news,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initToolbar()

        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                runCatching {
                    model.performFullSync()
                }.apply {
                    if (isFailure) {
                        Timber.e(exceptionOrNull())

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.error))
                            .setMessage(exceptionOrNull()?.message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }

                swipeRefresh.isRefreshing = false
            }
        }

        lifecycleScope.launchWhenResumed {
            initNewsList()
        }
    }

    private fun initToolbar() {
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.showReadNews) {
                lifecycleScope.launch {
                    model.setShowReadNews(!model.getShowReadNews().first())
                }

                return@setOnMenuItemClickListener true
            }

            false
        }

        lifecycleScope.launchWhenResumed {
            model.getShowReadNews().collect { show ->
                val item = toolbar.menu.findItem(R.id.showReadNews)

                if (show) {
                    item.setIcon(R.drawable.ic_baseline_visibility_24)
                    item.setTitle(R.string.hide_read_news)
                } else {
                    item.setIcon(R.drawable.ic_baseline_visibility_off_24)
                    item.setTitle(R.string.show_read_news)
                }
            }
        }
    }

    private suspend fun initNewsList() {
        progress.isVisible = true

        runCatching {
            model.performInitialSyncIfNoData()
        }.apply {
            if (isFailure) {
                Timber.e(exceptionOrNull())

                progress.isVisible = false

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.error))
                    .setMessage(exceptionOrNull()?.message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

        itemsView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context)
        }

        itemsView.adapter = adapter

        model.getNews().collect { rows ->
            Timber.d("Got ${rows.size} news!")

            if (model.isInitialSyncCompleted().first()) {
                progress.isVisible = false
            }

            empty.isVisible = rows.isEmpty() && model.isInitialSyncCompleted().first()

            adapter.swapRows(rows)
        }
    }
}