package co.appreactor.nextcloud.news.bookmarks

import android.os.Bundle
import android.util.DisplayMetrics
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
import co.appreactor.nextcloud.news.entries.EntriesAdapter
import co.appreactor.nextcloud.news.entries.EntriesAdapterCallback
import co.appreactor.nextcloud.news.entries.EntriesAdapterDecoration
import co.appreactor.nextcloud.news.entries.EntriesAdapterItem
import co.appreactor.nextcloud.news.podcasts.playPodcast
import kotlinx.android.synthetic.main.fragment_bookmarks.empty
import kotlinx.android.synthetic.main.fragment_bookmarks.listView
import kotlinx.android.synthetic.main.fragment_bookmarks.progress
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class BookmarksFragment : Fragment() {

    private val model: BookmarksFragmentModel by viewModel()

    private val adapter = EntriesAdapter(
        scope = lifecycleScope,
        callback = object : EntriesAdapterCallback {
            override fun onItemClick(item: EntriesAdapterItem) {
                val action = BookmarksFragmentDirections.actionStarredNewsFragmentToNewsItemFragment(item.id)
                findNavController().navigate(action)
            }

            override fun onDownloadPodcastClick(item: EntriesAdapterItem) {
                lifecycleScope.launchWhenResumed {
                    runCatching {
                        model.downloadPodcast(item.id)
                    }.getOrElse {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }
                }
            }

            override fun onPlayPodcastClick(item: EntriesAdapterItem) {
                lifecycleScope.launch {
                    runCatching {
                        playPodcast(model.getEntry(item.id)!!)
                    }.getOrElse {
                        Timber.e(it)
                        showDialog(R.string.error, it.message ?: "")
                    }
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
            R.layout.fragment_bookmarks,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launchWhenResumed {
            showBookmarks()
        }
    }

    private suspend fun showBookmarks() {
        progress.isVisible = true

        listView.setHasFixedSize(true)
        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = adapter
        listView.addItemDecoration(EntriesAdapterDecoration(resources.getDimensionPixelSize(R.dimen.entries_cards_gap)))

        val displayMetrics = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
        adapter.screenWidth = displayMetrics.widthPixels

        model.getBookmarks().collect { rows ->
            progress.isVisible = false
            empty.isVisible = rows.isEmpty()
            adapter.submitList(rows)
        }
    }
}