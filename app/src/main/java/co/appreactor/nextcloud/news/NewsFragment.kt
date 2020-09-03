package co.appreactor.nextcloud.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import co.appreactor.nextcloud.news.db.NewsItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.fragment_news.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class NewsFragment : Fragment() {

    private val model: NewsFragmentModel by viewModel()

    private val itemsAdapter = ItemsAdapter(
        items = mutableListOf(),
        feeds = mutableListOf()
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
        toolbar.apply {
            inflateMenu(R.menu.menu_news)

            lifecycleScope.launchWhenResumed {
                model.showReadNews.collect { show ->
                    val item = menu.findItem(R.id.showReadNews)

                    if (show) {
                        item.setIcon(R.drawable.ic_baseline_visibility_24)
                        item.setTitle(R.string.hide_read_news)
                    } else {
                        item.setIcon(R.drawable.ic_baseline_visibility_off_24)
                        item.setTitle(R.string.show_read_news)
                    }
                }
            }

            setOnMenuItemClickListener {
                if (it.itemId == R.id.showReadNews) {
                    model.showReadNews.value = !model.showReadNews.value
                    return@setOnMenuItemClickListener true
                }

                false
            }
        }

        lifecycleScope.launch {
            whenResumed {
                showData()
            }
        }

        swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                runCatching {
                    model.sync()
                }.apply {
                    if (isFailure) {
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
    }

    private suspend fun showData() {
        progress.isVisible = true

        runCatching {
            model.performInitialSyncIfNoData()
        }.apply {
            if (isFailure) {
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
            adapter = itemsAdapter
        }

        model.getNewsItems().collect { news ->
            Timber.d("Got ${news.size} news!")

            if (model.isInitialSyncCompleted().first()) {
                progress.isVisible = false
            }

            empty.isVisible = news.isEmpty() && model.isInitialSyncCompleted().first()

            val onItemClick: (NewsItem) -> Unit = {
                lifecycleScope.launch {
                    val action =
                        NewsFragmentDirections.actionNewsFragmentToNewsItemFragment(it.id)
                    findNavController().navigate(action)
                }
            }

            itemsAdapter.onClick = onItemClick

            itemsAdapter.swapItems(news, model.getFeeds())
        }
    }
}